package klein.js

import kotlin.coroutines.Continuation
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.startCoroutine
import kotlin.coroutines.suspendCoroutine
import kotlin.js.Promise

internal fun <T> promise(block: suspend () -> T): Promise<T> =
    Promise { resolve, reject ->
        block.startCoroutine(Continuation(EmptyCoroutineContext) { result -> result.fold(resolve, reject) })
    }

internal suspend fun awaitResult(value: Any?): Any? {
    if (!isThenable(value)) return value
    return suspendCoroutine { continuation ->
        value.asDynamic().then(
            { settled: Any? -> continuation.resume(settled) },
            { reason: Any? -> continuation.resumeWithException(reason as? Throwable ?: Throwable(reason.toString())) },
        )
    }
}

private fun isThenable(value: Any?): Boolean =
    value != null && (jsTypeOf(value) == "object" || jsTypeOf(value) == "function") && jsTypeOf(value.asDynamic().then) == "function"
