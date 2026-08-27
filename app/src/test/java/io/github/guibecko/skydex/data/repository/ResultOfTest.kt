package io.github.guibecko.skydex.data.repository

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import kotlin.coroutines.cancellation.CancellationException

class ResultOfTest {

    @Test
    fun `a value is wrapped as success`() {
        assertEquals("ok", resultOf { "ok" }.getOrNull())
    }

    @Test
    fun `an ordinary failure is wrapped as failure`() {
        val result = resultOf { throw IOException("boom") }

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is IOException)
    }

    /** A cancellation raised inside the block must escape rather than become a failed [Result]. */
    @Test
    fun `a CancellationException escapes instead of becoming a failure`() {
        var escaped = false
        var result: Result<Unit>? = null
        try {
            result = resultOf { throw CancellationException("navigated away") }
        } catch (_: CancellationException) {
            escaped = true
        }

        assertTrue("resultOf must rethrow CancellationException", escaped)
        assertEquals(null, result)
    }

    /**
     * The reason this helper exists at all. `runCatching` turns a cancelled call into
     * `Result.failure(CancellationException)`, which the login screen reports to the user as
     * "credenciais inválidas" for someone who merely navigated away — and which leaves the
     * cancelled job looking like it completed normally.
     */
    @Test
    fun `cancelling the calling coroutine does not produce a Result at all`() = runBlocking {
        val started = CompletableDeferred<Unit>()
        var producedAResult = false

        val job = launch(Dispatchers.Default) {
            resultOf {
                started.complete(Unit)
                awaitCancellation()
            }
            producedAResult = true
        }
        started.await()
        job.cancelAndJoin()

        assertTrue(job.isCancelled)
        assertFalse(
            "resultOf swallowed the cancellation and let the caller carry on",
            producedAResult
        )
    }

    /** Guards the premise above: the behaviour this helper exists to replace. */
    @Test
    fun `runCatching by contrast swallows cancellation`() {
        val swallowed = runCatching { throw CancellationException("navigated away") }

        assertTrue(swallowed.isFailure)
        assertTrue(swallowed.exceptionOrNull() is CancellationException)
    }
}
