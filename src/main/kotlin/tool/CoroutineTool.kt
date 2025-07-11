package tool

import kotlinx.coroutines.*
import kotlinx.coroutines.swing.Swing
import kotlin.coroutines.CoroutineContext

object CoroutineTool {
    /**
     * global scope
     */
    val globalScope = CoroutineScope(
        SupervisorJob()
                + CoroutineExceptionHandler { coroutineContext, throwable ->
            // throw if is debugging
            println("CoroutineExceptionHandler ${coroutineContext[CoroutineExceptionHandler]} $throwable")
        })

    fun launchScope(context: CoroutineContext = Dispatchers.Swing, block: suspend CoroutineScope.() -> Unit): Job {
        return globalScope.launch(context + SupervisorJob()) {
            block.invoke(this)
        }
    }

}