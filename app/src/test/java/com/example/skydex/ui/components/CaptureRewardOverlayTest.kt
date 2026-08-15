package com.example.skydex.ui.components

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The rules the reward card's two branches are held to, asserted through [presentationOf].
 *
 * This module has no Compose runtime in its JVM test source set, so "the unconfirmed branch shows
 * no XP and no rarity" cannot be checked by rendering. [presentationOf] is the pure function the
 * composables actually read for that decision — not a copy of it — which is what makes these
 * assertions load-bearing rather than decorative.
 */
class CaptureRewardOverlayTest {

    private fun reward(
        confirmed: Boolean,
        xpAwarded: Int,
        rarity: String = "RARE"
    ) = CaptureReward(
        phenomenonName = "Tempestade com Trovões",
        rarity = rarity,
        confirmed = confirmed,
        xpAwarded = xpAwarded
    )

    /**
     * The branch that must not celebrate.
     *
     * An unconfirmed capture earned nothing and, since the client now takes it back off the server
     * the moment the verdict lands, it is not even kept. Drawing the XP number or the rarity pill
     * would be showing a prize for it — and rarity is the reward axis, so naming "LENDÁRIO" beside
     * a capture worth zero reads as a prize withdrawn.
     */
    @Test
    fun `an unconfirmed capture shows no XP and no rarity`() {
        val presentation = presentationOf(reward(confirmed = false, xpAwarded = 0))

        assertFalse("an unconfirmed capture must not be celebrated", presentation.celebrates)
        assertFalse("no XP was awarded, so no number may be drawn", presentation.showsXp)
        assertFalse("rarity is the reward axis and there was no reward", presentation.showsRarity)
    }

    /**
     * The affordance has to be as truthful as the copy.
     *
     * The unconfirmed record is deleted the moment the verdict lands, so a filled "Ver meus
     * registros" would send the user hunting through a list that provably does not contain it —
     * implying exactly the claim the copy above it was rewritten to stop making. "Registrar outro"
     * is the only action that can help this user, and the copy already ends by pointing at it.
     */
    @Test
    fun `an unconfirmed capture leads with Registrar outro`() {
        val presentation = presentationOf(reward(confirmed = false, xpAwarded = 0))

        assertFalse(
            "Meus Registros must not be the primary action for a capture that is not there",
            presentation.primaryIsSeeCaptures
        )
    }

    /**
     * The backend awards zero on every unconfirmed path, but the client must not *depend* on that
     * to stay quiet: a response that somehow paired UNCONFIRMED with a non-zero award still may not
     * produce a celebration. The verdict is the gate, the award is only a second condition on top.
     */
    @Test
    fun `an unconfirmed capture stays quiet even if the response carries XP`() {
        val presentation = presentationOf(reward(confirmed = false, xpAwarded = 60))

        assertFalse(presentation.celebrates)
        assertFalse("the verdict gates the number, not just the amount", presentation.showsXp)
        assertFalse(presentation.showsRarity)
    }

    /**
     * The confirmed path, so the assertions above are pinning a branch and not a constant.
     *
     * The action order is asserted here too: a confirmed capture really is in Meus Registros, it is
     * worth XP, and seeing the collection it just joined is the payoff the whole flow exists for —
     * so "Ver meus registros" keeps the filled button. Only the unconfirmed branch inverts.
     */
    @Test
    fun `a confirmed capture shows its XP and its rarity and leads to Meus Registros`() {
        val presentation = presentationOf(reward(confirmed = true, xpAwarded = 400, rarity = "LEGENDARY"))

        assertTrue(presentation.celebrates)
        assertTrue(presentation.showsXp)
        assertTrue(presentation.showsRarity)
        assertTrue(presentation.primaryIsSeeCaptures)
    }

    /**
     * A confirmed capture worth nothing is not a case the backend produces today, but the card must
     * never count up to zero if it ever does: "+0 XP" reads as a reward that was denied.
     */
    @Test
    fun `a confirmed capture worth nothing draws no number`() {
        val presentation = presentationOf(reward(confirmed = true, xpAwarded = 0))

        assertTrue("it is still a confirmation, and it still says so", presentation.celebrates)
        assertFalse("no +0 XP", presentation.showsXp)
        assertTrue("the species really was confirmed, so its rarity is honest", presentation.showsRarity)
    }
}
