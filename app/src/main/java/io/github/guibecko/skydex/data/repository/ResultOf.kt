package io.github.guibecko.skydex.data.repository

import kotlin.coroutines.cancellation.CancellationException

/**
 * Like [runCatching], but never swallows coroutine cancellation.
 *
 * `runCatching` catches [Throwable], which includes the [CancellationException] the coroutine
 * machinery throws to unwind a cancelled job. Repositories are called from composable scopes that
 * cancel whenever the user navigates away, so a plain `runCatching` would convert an ordinary
 * cancellation into `Result.failure` — breaking structured concurrency and, worse, surfacing
 * "invalid credentials" to a user who simply left the screen. Rethrowing keeps cancellation a
 * control-flow signal rather than an error.
 */
inline fun <T> resultOf(block: () -> T): Result<T> = try {
    Result.success(block())
} catch (e: CancellationException) {
    throw e
} catch (e: Throwable) {
    Result.failure(e)
}
