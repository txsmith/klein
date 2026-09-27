// Bridges Kotlin suspend functions and JavaScript promises: `promise` runs a suspending block as a
// promise JavaScript can await, and `await` suspends until a JavaScript promise settles. A promise
// rejected with something other than an Error resumes with a ThrownValue carrying it, since Kotlin
// can only throw a Throwable; mapExceptions in Kotlin.ts takes the original value out again.
package klein.jsbinding

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

@JsExport
class ThrownValue internal constructor(
    val value: Any?,
) : Throwable()

internal suspend fun <T> Promise<T>.await(): T =
    suspendCoroutine { continuation ->
        then({ continuation.resume(it) }, { reason: Any? ->
            continuation.resumeWithException(reason as? Throwable ?: ThrownValue(reason))
        })
    }
