package io.github.guibecko.skydex.data.repository

import com.google.gson.Gson
import retrofit2.HttpException

/**
 * Reading the backend's error envelope off a failed call.
 *
 * Every SkyDex endpoint answers a failure with `{"error": "..."}` — `GlobalExceptionHandler` makes
 * that true for all of them, including the unexpected ones. Those messages are written to be shown:
 * Tasks 12b and 12c designed five distinct, actionable 400s for the capture path alone ("This photo
 * has already been used for a capture", "Photo has expired; take a new one", …). Discarding them in
 * favour of one blanket string does not just lose detail, it misleads — the blanket string in
 * `CaptureViewModel` told the user to retry, which for most of those five cannot succeed.
 */

/** The shape of that envelope. Nullable, because a body can be anything at all. */
private data class ErrorEnvelope(val error: String?)

private val gson = Gson()

/**
 * The HTTP status of a failed call, or `null` when the failure was never an HTTP response —
 * a dropped connection, a DNS failure, a serialisation bug. The distinction matters: a 400 is the
 * server saying "this will never be accepted", while a dropped connection says nothing at all.
 */
val Throwable.httpStatusCode: Int?
    get() = (this as? HttpException)?.code()

/**
 * The message the backend put in its error envelope, or `null` if there is not one that can be
 * read. Callers are expected to fall back to their own wording on `null`.
 *
 * Defensive on purpose, because an error body is the least trustworthy thing on the wire. It may be
 * absent, empty, truncated, an HTML page injected by a proxy, or JSON of some other shape — and
 * this runs on the failure path, where a second exception would replace a bad message with a
 * crash. Anything that is not a readable envelope becomes `null`.
 *
 * **The body may only be consumed once.** `ResponseBody.string()` closes the underlying source, so
 * this must be called at most once per throwable; call it, keep the result, and do not ask again.
 */
fun Throwable.backendErrorMessage(): String? {
    val response = (this as? HttpException)?.response() ?: return null
    return try {
        val raw = response.errorBody()?.string()
        if (raw.isNullOrBlank()) {
            null
        } else {
            gson.fromJson(raw, ErrorEnvelope::class.java)?.error?.takeIf { it.isNotBlank() }
        }
    } catch (e: Exception) {
        // Unreadable, unparseable, or the wrong shape. The caller's generic message is better
        // than propagating a failure out of the error path.
        null
    }
}
