package com.example.skydex.ui.common

import android.util.Log

/**
 * How a ViewModel reports a failure cause it is not going to show the user.
 *
 * This exists for the same reason `AuthGateway` does: the JVM `android.jar` is all stubs that
 * throw, so a direct `android.util.Log` call turns every failure-path unit test into a crash.
 * Injecting the call as a defaulted constructor parameter keeps the ViewModels testable with no
 * mocking framework and no build-wide `isReturnDefaultValues`, which would silence *every*
 * framework stub in the suite and turn a missing fake into a false green.
 */
typealias LogWarning = (tag: String, message: String, cause: Throwable) -> Unit

/**
 * The production seam — the only place in `ui` that touches `android.util.Log`.
 *
 * Messages passed to it must name the operation, never its subject: no e-mail addresses, no
 * coordinates, no tokens. The throwable is what makes offline, a 401 and a parse error
 * distinguishable; identifiers add nothing diagnostically and leak into every bug report.
 */
val androidLogWarning: LogWarning = { tag, message, cause -> Log.w(tag, message, cause) }
