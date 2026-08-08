package com.example.skydex.ui.common

/**
 * Stands in for `androidLogWarning` on the JVM, where the real `android.util.Log` stub throws.
 *
 * It records instead of discarding so a test can assert *that* a failure was reported and *what*
 * went into the message — which is how the "no PII in logcat" rule stays enforced rather than
 * merely intended.
 */
class RecordingLogWarning : (String, String, Throwable) -> Unit {

    data class Warning(val tag: String, val message: String, val cause: Throwable)

    private val recorded = mutableListOf<Warning>()
    val warnings: List<Warning> get() = recorded

    override fun invoke(tag: String, message: String, cause: Throwable) {
        recorded += Warning(tag, message, cause)
    }
}

/** For the tests that only need the ViewModel not to touch the framework. */
val noLogging: LogWarning = { _, _, _ -> }
