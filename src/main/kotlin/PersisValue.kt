import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import com.russhwolf.settings.Settings
import com.russhwolf.settings.serialization.decodeValue
import com.russhwolf.settings.serialization.encodeValue
import kotlinx.serialization.builtins.ByteArraySerializer
import tool.CoroutineTool.launchScope
import java.io.*
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty

class PersisValue<T>(private val key: String, private val defaultValue: T) :
    ReadWriteProperty<Any?, T> {

    companion object {
        private val settings by lazy { Settings() }

        fun bool(key: String, defaultValue: Boolean = false) = PersisValue<Boolean>(key, defaultValue)
        fun long(key: String, defaultValue: Long = 0L) = PersisValue<Long>(key, defaultValue)
        fun int(key: String, defaultValue: Int = 0) = PersisValue<Int>(key, defaultValue)
        fun string(key: String, defaultValue: String = "") = PersisValue<String>(key, defaultValue)
        fun float(key: String, defaultValue: Float = 0f) = PersisValue<Float>(key, defaultValue)
        fun double(key: String, defaultValue: Double = 0.0) = PersisValue<Double>(key, defaultValue)
        fun <T> create(key: String, defaultValue: T) = PersisValue<T>(key, defaultValue)
    }

    private fun saveData(key: String, value: T) {
        when (value) {
            is Int -> settings.putInt(key, value)
            is String -> settings.putString(key, value)
            is Long -> settings.putLong(key, value)
            is Float -> settings.putFloat(key, value)
            is Boolean -> settings.putBoolean(key, value)
            is Double -> settings.putDouble(key, value)
            is Serializable -> settings.encodeValue(
                ByteArraySerializer(),
                key,
                value.serializeToBytes()
            )

            else -> throw IllegalArgumentException("Unsupported type")
        }
        launchScope {
            _state.value = value
        }
    }

    override operator fun getValue(thisRef: Any?, property: KProperty<*>): T {
        return (when (defaultValue) {
            is Int -> settings.getInt(key, defaultValue)
            is String -> settings.getString(key, defaultValue)
            is Long -> settings.getLong(key, defaultValue)
            is Float -> settings.getFloat(key, defaultValue)
            is Boolean -> settings.getBoolean(key, defaultValue)
            is Double -> settings.getDouble(key, defaultValue)
            is Serializable -> settings.decodeValue(
                ByteArraySerializer(), key, defaultValue.serializeToBytes()
            ).deserialization<T>()

            else -> throw IllegalArgumentException("Unsupported type")
        } as? T) ?: defaultValue
    }

    override operator fun setValue(thisRef: Any?, property: KProperty<*>, value: T) {
        saveData(key, value)
    }

    var value by this

    private val _state = mutableStateOf(value)
    val state = _state as State<T>

    private fun Any.serializeToBytes(): ByteArray = runCatching {
        ByteArrayOutputStream().use { bos ->
            ObjectOutputStream(bos).use { oos ->
                oos.writeObject(this)
                oos.flush()
                bos.toByteArray()
            }
        }
    }.getOrDefault(ByteArray(0))

    @Suppress("UNCHECKED_CAST")
    private fun <T> ByteArray.deserialization(): T? = runCatching {
        ByteArrayInputStream(this).use { bis ->
            ObjectInputStream(bis).use { ois ->
                ois.readObject() as? T
            }
        }
    }.getOrNull()
}
