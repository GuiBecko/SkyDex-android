package com.example.skydex.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.MilitaryTech
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.skydex.ui.common.Tone
import com.example.skydex.ui.common.UiMessage
import com.example.skydex.ui.theme.SkyDexPalette
import com.example.skydex.ui.theme.SkyDexSpacing
import com.example.skydex.ui.theme.SkyDexTheme
import com.example.skydex.ui.theme.rarityColorFor
import com.example.skydex.ui.theme.rarityLabelFor
import kotlin.math.roundToInt

/**
 * # The peak moment
 *
 * Audit finding B6: the user photographs a phenomenon, uploads it, earns XP — and the app simply
 * swapped the screen for Meus Registros. No number, no animation, no haptic. By the peak-end rule
 * that was the single most valuable missing moment in the product, so this is the one place in
 * SkyDex allowed to be *loud*, within the app's light-and-airy language.
 *
 * ## What this component is allowed to claim
 *
 * Everything drawn here is backed by data the client actually holds. The rules are not stylistic:
 *
 * - **XP** comes from `WeatherEventResponse.xpAwarded`, which the capture POST returns. It is
 *   `rarity.xp` for a CONFIRMED capture and **exactly `0`** for an UNCONFIRMED one — the backend's
 *   `CaptureValidationService` returns `ValidationResult(UNCONFIRMED, observedCode, 0)` on every
 *   one of its five unconfirmed paths, and `CaptureCommitService.commit` zeroes it again if the
 *   travel re-check downgrades the verdict. So the XP block simply does not render when there is
 *   no XP; it never counts up to zero and never implies a reward that was not granted.
 * - **Rarity** comes from `WeatherEventResponse.rarity` and is resolved through
 *   [rarityColorFor], the app's one rarity-to-colour mapping. It is the game's core reward axis,
 *   so it drives the colour of the number, the size and strength of the glow behind it, how long
 *   the count-up takes, and whether the glow keeps breathing — a LEGENDARY capture is meant to be
 *   unmistakable next to a COMMON one.
 * - **Level-up and new badges** are NOT in the capture response. They exist only on the profile
 *   endpoint, so they arrive — if at all — from a second, entirely optional call, and this
 *   component renders them only when [CaptureReward.bonus] is non-null. See [CaptureReward.bonus]
 *   for the contract that keeps that call from ever delaying the celebration.
 *
 * ## The unconfirmed case is not a failure
 *
 * The backend cross-checks the photo's phenomenon against the region's real weather. UNCONFIRMED
 * also covers an Open-Meteo outage and a capture outside the forecast window, so it is not an
 * accusation. Celebrating it would be worse than staying silent, and scolding for it would be worse
 * still, so that branch drops the XP, the glow and the trophy and explains itself through
 * [SkyDexNotice] in the app's settled amber voice.
 *
 * What that branch may **not** do is claim the capture is stored: `CaptureViewModel` issues a
 * `DELETE` for an unconfirmed capture the moment the create returns, so copy promising the photo is
 * waiting in Meus Registros would be false. It does not announce the deletion either — there is
 * nothing the user can do about it, and a notice about a record they never saw would be noise. See
 * [UnconfirmedHeader] for the copy and `CaptureViewModel.discardUnconfirmed` for the rule.
 *
 * The same constraint applies to the buttons, not just the words: that branch promotes "Registrar
 * outro" to the primary action, because a filled button pointing at Meus Registros implies the same
 * thing the copy is no longer allowed to say. See [RewardCard].
 *
 * ## Token compliance
 *
 * No colour literals, no inline font size or weight, no inline corner radius. Colour comes
 * from `SkyDexPalette.colors` / `MaterialTheme.colorScheme`, type from `MaterialTheme.typography`
 * (4 sizes, 2 weights), spacing from [SkyDexSpacing], corners from `MaterialTheme.shapes`. The two
 * `dp` values in this file are a touch-target floor and a glow geometry constant, neither of which
 * is spacing — both are named and justified where they are declared.
 */

// ---------------------------------------------------------------------------------------------
// State
// ---------------------------------------------------------------------------------------------

/**
 * Everything the reward moment shows, as plain data.
 *
 * Deliberately not a `WeatherEventResponse`: this component must be renderable from a `@Preview`
 * and from a test without a DTO, a `ServiceLocator` or a network layer anywhere behind it.
 *
 * @param phenomenonName the species' display copy, e.g. "Tempestade com Trovões". Backend-supplied
 *   and already pt-BR, so it is shown verbatim.
 * @param rarity the backend rarity code (`COMMON` / `UNCOMMON` / `RARE` / `EPIC` / `LEGENDARY`).
 *   Anything unrecognised degrades to Common in both colour and intensity — see [rarityWeightOf].
 * @param confirmed whether `validationStatus` came back `CONFIRMED`. Picks the copy: this is the
 *   only thing that decides whether the moment congratulates or explains.
 * @param xpAwarded XP actually granted for this capture. Zero for every unconfirmed path. Decides
 *   whether the number renders at all, independently of [confirmed], so a hypothetical confirmed
 *   capture worth nothing shows the confirmation copy without a meaningless "+0 XP".
 * @param bonus level-up and newly unlocked badges, or `null`.
 */
data class CaptureReward(
    val phenomenonName: String,
    val rarity: String,
    val confirmed: Boolean,
    val xpAwarded: Int,
    /**
     * The part of the reward the capture response cannot answer.
     *
     * `POST /api/captures` returns no level, no level-up flag and no badge list, so this can only
     * be filled by a second call to the profile endpoint. That call is **strictly optional**: the
     * overlay is already on screen, counting up the XP it does have, before it is even made, and a
     * failure leaves this `null` and costs the user nothing but the extra line. Never block the
     * peak moment on a second round-trip, and never populate this from anything but a verified
     * before/after comparison — a level-up the client guessed at is worse than one it never showed.
     */
    val bonus: CaptureRewardBonus? = null
)

/**
 * The optional second half of the reward, obtained by diffing the profile taken before the capture
 * against the profile taken after it.
 *
 * @param newLevel the level the user is now on, and **only** when it is strictly higher than the
 *   level they were on before this capture. `null` means "no level-up, or we could not verify one",
 *   and those two are deliberately indistinguishable here: both must render nothing.
 * @param newBadges display names of achievements unlocked between the two reads. Usually empty.
 *   Not gated on [CaptureReward.confirmed] — the backend's `AchievementContext` counts
 *   unconfirmed captures too, so an unconfirmed capture can genuinely unlock one.
 */
data class CaptureRewardBonus(
    val newLevel: Int?,
    val newBadges: List<String>
)

/**
 * Which of the card's three reward signals a given [CaptureReward] is allowed to draw.
 *
 * Split out of the composables as a pure function for one reason: the JVM test source set has no
 * Compose runtime, so "the unconfirmed branch shows no XP and no rarity" is a rule that cannot be
 * asserted by rendering. Keeping the decision here, and having [RewardCard] and [ConfirmedHeader]
 * read it rather than re-deriving it, means the test pins the same expression the UI obeys instead
 * of a copy of it that can drift.
 *
 * @param celebrates the confirmed treatment — trophy, congratulation, glow. Anything else is the
 *   explaining treatment.
 * @param showsXp whether the counting number renders. Gated on the award being non-zero as well as
 *   on the verdict, so a confirmed capture that happened to be worth nothing never draws "+0 XP".
 * @param showsRarity whether the rarity pill renders. Rarity is the reward axis, so naming it next
 *   to a capture that earned nothing would read as a prize withdrawn.
 * @param primaryIsSeeCaptures which of the two ways forward gets the filled button. See
 *   [RewardCard] for why this inverts on the unconfirmed branch.
 */
internal data class RewardPresentation(
    val celebrates: Boolean,
    val showsXp: Boolean,
    val showsRarity: Boolean,
    val primaryIsSeeCaptures: Boolean
)

internal fun presentationOf(reward: CaptureReward): RewardPresentation = RewardPresentation(
    celebrates = reward.confirmed,
    showsXp = reward.confirmed && reward.xpAwarded > 0,
    showsRarity = reward.confirmed,
    primaryIsSeeCaptures = reward.confirmed
)

// ---------------------------------------------------------------------------------------------
// Layout constants
// ---------------------------------------------------------------------------------------------

/**
 * Touch-target floor for the two actions, matching `CaptureScreen`'s commit button. A minimum
 * height is an accessibility constraint, not a spacing decision, so it does not come from
 * [SkyDexSpacing] — the same reasoning `CaptureScreen.PrimaryButtonMinHeight` already carries.
 */
private val ActionMinHeight = 56.dp

/**
 * How far the glow's centre sits below the XP baseline. Optical centring: the number's ascenders
 * make its ink sit high inside its layout box, so a glow centred on the box reads as sitting above
 * the digits. Geometry, not spacing.
 */
private val GlowCenterNudge = 4.dp

// ---------------------------------------------------------------------------------------------
// The overlay
// ---------------------------------------------------------------------------------------------

/**
 * The post-capture reward, drawn over whatever screen invoked it.
 *
 * Composed only when there is a reward to show — it has no `visible` flag, because a reward that
 * exists is a reward the user has earned and must see. The caller controls its lifetime by
 * composing it or not; the entrance animation runs once per composition.
 *
 * Anchored to the bottom, sheet-style: both actions land in the thumb zone rather than floating in
 * the middle of the screen where the primary action of a one-handed flow cannot be reached.
 *
 * @param reward what to celebrate.
 * @param onSeeCaptures the primary way forward — the caller navigates to Meus Registros.
 * @param onCaptureAnother the secondary way forward — the caller resets the form and stays.
 */
@Composable
fun CaptureRewardOverlay(
    reward: CaptureReward,
    onSeeCaptures: () -> Unit,
    onCaptureAnother: () -> Unit,
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current

    // `rememberSaveable`, not `remember`: a rotation while the overlay is up recreates this
    // composition, and a plain `remember` would fire the haptic a second time for a capture the
    // user already felt. It survives the recreation, so the buzz belongs to the moment rather
    // than to the composition.
    var hapticPlayed by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        if (!hapticPlayed) {
            // The audit noted the app has zero haptics. Confirmed gets the heavier of the two —
            // it is the moment the whole flow exists for. Unconfirmed gets the light tick, so the
            // hand is told "done" without being told "well done".
            haptic.performHapticFeedback(
                if (reward.confirmed) HapticFeedbackType.LongPress else HapticFeedbackType.TextHandleMove
            )
            hapticPlayed = true
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        // The scrim both dims the form and swallows taps meant for it. Without the second half,
        // "Tirar Outra Foto" and "Salvar Registro" stay live under a full-screen overlay.
        // No ripple and no interaction source of its own: this is a barrier, not a button.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.scrim.copy(alpha = ScrimAlpha))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {}
                )
        )

        // `visible = true` on first composition is what makes AnimatedVisibility animate the
        // entrance rather than snap: the initial state is derived from the value at composition
        // time, so a MutableTransitionState seeded false and flipped true is required.
        val entrance = remember { MutableTransitionState(false) }
        entrance.targetState = true

        AnimatedVisibility(
            visibleState = entrance,
            enter = fadeIn(animationSpec = tween(EntranceMillis)) +
                scaleIn(
                    initialScale = EntranceInitialScale,
                    animationSpec = tween(EntranceMillis, easing = FastOutSlowInEasing)
                ),
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            RewardCard(
                reward = reward,
                onSeeCaptures = onSeeCaptures,
                onCaptureAnother = onCaptureAnother
            )
        }
    }
}

/**
 * The card itself: the header for whichever branch applies, the optional bonus, then the two ways
 * forward.
 *
 * ## Why the actions swap on the unconfirmed branch
 *
 * Both branches offer the same two destinations, and both keep them — what changes is which one is
 * the filled button.
 *
 * On a **confirmed** capture, "Ver meus registros" is the payoff. The row is really there, it is
 * worth XP, and looking at the collection it just joined is the whole point of the flow. It leads.
 *
 * On an **unconfirmed** one it is a dead end, and since `CaptureViewModel.discardUnconfirmed`
 * started taking the record back, a provably empty one: the user would arrive in Meus Registros
 * hunting for a capture that is no longer on the server. Copy that no longer claims the record was
 * kept, sitting above a primary button that still points at where it would have been, is the same
 * lie moved from the paragraph into the affordance. So the filled button becomes "Registrar outro"
 * — the one action that can actually help, and the one the copy right above it already ends on.
 *
 * "Ver meus registros" stays as the quiet action rather than being dropped for two reasons. It is
 * the only route *off* this screen (the alternative leaves the user on a blank capture form with
 * the overlay gone), and it is not itself dishonest: the list still holds every capture they have
 * earned, just not this one. Removing it would trade a mild misdirection for a real dead end.
 */
@Composable
private fun RewardCard(
    reward: CaptureReward,
    onSeeCaptures: () -> Unit,
    onCaptureAnother: () -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = MaterialTheme.shapes.extraLarge,
        modifier = Modifier
            .fillMaxWidth()
            .padding(SkyDexSpacing.lg)
    ) {
        Column(
            modifier = Modifier.padding(SkyDexSpacing.xl),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(SkyDexSpacing.md)
        ) {
            val presentation = presentationOf(reward)
            if (presentation.celebrates) {
                ConfirmedHeader(reward, presentation)
            } else {
                UnconfirmedHeader(reward)
            }

            reward.bonus?.let { BonusSection(it) }

            // Both ways forward are always present; only their weight changes. At the bottom of a
            // bottom-anchored card, the filled button is the one a thumb already rests on — which
            // is exactly why which action gets it is not a cosmetic choice.
            if (presentation.primaryIsSeeCaptures) {
                PrimaryAction(text = "Ver meus registros", onClick = onSeeCaptures)
                SecondaryAction(text = "Registrar outro", onClick = onCaptureAnother)
            } else {
                PrimaryAction(text = "Registrar outro", onClick = onCaptureAnother)
                SecondaryAction(text = "Ver meus registros", onClick = onSeeCaptures)
            }
        }
    }
}

/** The filled button. Same treatment on both branches — only the label and target differ. */
@Composable
private fun PrimaryAction(text: String, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = ActionMinHeight)
            .padding(top = SkyDexSpacing.sm)
    ) {
        Text(text, style = MaterialTheme.typography.titleMedium)
    }
}

/**
 * The quieter way out. It keeps the full [ActionMinHeight] touch target: it is de-emphasised in
 * weight, not in reachability, and on the unconfirmed branch it is the user's only route off this
 * screen.
 */
@Composable
private fun SecondaryAction(text: String, onClick: () -> Unit) {
    TextButton(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = ActionMinHeight)
    ) {
        Text(text, style = MaterialTheme.typography.labelLarge)
    }
}

// ---------------------------------------------------------------------------------------------
// Confirmed
// ---------------------------------------------------------------------------------------------

@Composable
private fun ConfirmedHeader(reward: CaptureReward, presentation: RewardPresentation) {
    val weight = rarityWeightOf(reward.rarity)
    val rarityColor = rarityColorFor(reward.rarity)

    Icon(
        imageVector = Icons.Default.EmojiEvents,
        // The trophy repeats what the heading right below it already says in words; announcing it
        // would make a screen reader read the same idea twice. Same rule as `SkyDexNotice`'s icon.
        contentDescription = null,
        tint = rarityColor,
        modifier = Modifier.size(SkyDexSpacing.xxl)
    )

    Text(
        text = "Registro confirmado!",
        style = MaterialTheme.typography.titleLarge,
        color = SkyDexPalette.colors.textPrimary,
        textAlign = TextAlign.Center
    )

    // Gated on the award as well as on the verdict — see CaptureReward.xpAwarded. A capture worth
    // nothing never draws a number, whatever its status says.
    if (presentation.showsXp) {
        XpBloom(xp = reward.xpAwarded, rarityColor = rarityColor, weight = weight)
    }

    Text(
        text = reward.phenomenonName,
        style = MaterialTheme.typography.titleMedium,
        color = SkyDexPalette.colors.textPrimary,
        textAlign = TextAlign.Center
    )

    // The rarity, said in words as well as in colour: colour alone is not an accessible channel,
    // and this is the axis the whole game is scored on.
    if (presentation.showsRarity) {
        Surface(
            color = MaterialTheme.colorScheme.surfaceVariant,
            shape = MaterialTheme.shapes.small
        ) {
            Text(
                text = rarityLabelFor(reward.rarity),
                style = MaterialTheme.typography.labelLarge,
                color = rarityColor,
                modifier = Modifier.padding(
                    horizontal = SkyDexSpacing.md,
                    vertical = SkyDexSpacing.xs
                )
            )
        }
    }
}

/**
 * The number, counting up, over a glow whose size, strength and pace are all a function of rarity.
 *
 * The count-up is not decoration: a number that lands instantly is read as a label, while one that
 * climbs is read as *earned*. Its duration scales with rarity rather than with the XP value, so the
 * pacing tracks the thing the user is being rewarded for rather than an arbitrary magnitude.
 *
 * `Animatable` over a plain `animate*AsState` because the target here is a fixed endpoint reached
 * once, not a value that keeps changing — and because the count must start from zero every time
 * regardless of what the previous reward was worth.
 */
@Composable
private fun XpBloom(xp: Int, rarityColor: Color, weight: Int) {
    val counted = remember { Animatable(0f) }
    val bloom = remember { Animatable(0f) }

    LaunchedEffect(xp) {
        bloom.animateTo(1f, tween(BloomMillis, easing = FastOutSlowInEasing))
    }
    LaunchedEffect(xp) {
        counted.animateTo(
            targetValue = xp.toFloat(),
            animationSpec = tween(
                durationMillis = CountUpBaseMillis + weight * CountUpPerTierMillis,
                easing = LinearOutSlowInEasing
            )
        )
    }

    // Only EPIC and LEGENDARY keep breathing. Restraint is the point: if every capture pulsed,
    // the pulse would stop meaning "this one was special".
    val pulse = if (weight >= PulseFromWeight) {
        val transition = rememberInfiniteTransition(label = "rarity-glow")
        transition.animateFloat(
            initialValue = 1f,
            targetValue = PulsePeak,
            animationSpec = infiniteRepeatable(
                animation = tween(PulseMillis, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "rarity-glow-scale"
        ).value
    } else {
        1f
    }

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .padding(vertical = SkyDexSpacing.sm)
            .drawBehind {
                val radius = size.height *
                    (GlowRadiusBase + weight * GlowRadiusPerTier) *
                    bloom.value * pulse
                drawCircle(
                    color = rarityColor,
                    radius = radius,
                    center = center.copy(y = center.y + GlowCenterNudge.toPx()),
                    alpha = (GlowAlphaBase + weight * GlowAlphaPerTier) * bloom.value
                )
            }
    ) {
        Text(
            // The payoff, in the largest slot the scale has (28sp Bold). Rounded, not truncated,
            // so the count lands exactly on the awarded value instead of one short of it.
            text = "+${counted.value.roundToInt()} XP",
            style = MaterialTheme.typography.displayLarge,
            color = rarityColor,
            textAlign = TextAlign.Center
        )
    }
}

// ---------------------------------------------------------------------------------------------
// Unconfirmed
// ---------------------------------------------------------------------------------------------

/**
 * The kind version of "no XP".
 *
 * No trophy, no glow, no number — but no red and no blame either. It says plainly what did not
 * happen and why, and reuses [SkyDexNotice] rather than inventing a second visual voice for the
 * same amber register the rest of the app already speaks in.
 *
 * ## What this copy may claim
 *
 * It used to open with "Registro salvo" and close with "Sua foto e seu registro estão guardados em
 * Meus Registros". Both became false the moment `CaptureViewModel.discardUnconfirmed` started
 * taking an unconfirmed capture back off the server, so both are gone. The replacement is held to
 * two rules that pull in opposite directions and are both non-negotiable:
 *
 * - **It promises no storage.** Nothing here says the capture, the photo or the record is kept
 *   anywhere. The user is not sent looking for something that will not be there.
 * - **It does not announce the deletion.** There is no action to take and no failure to report, so
 *   naming the DELETE would only add alarm to a moment that is already a small disappointment.
 *
 * What is left is the true, useful half: the weather data did not match, so this one earned
 * nothing, and the way forward is to try again when the phenomenon is actually overhead. It does
 * not promise a re-check, because the backend never re-validates a capture — claiming otherwise
 * would be a lie the app could not keep.
 *
 * The rarity pill is deliberately absent here. Rarity is the *reward* axis; showing "LENDÁRIO" next
 * to a capture that earned nothing would read as a prize that was then taken away.
 */
@Composable
private fun UnconfirmedHeader(reward: CaptureReward) {
    Text(
        text = "Não deu para confirmar",
        style = MaterialTheme.typography.titleLarge,
        color = SkyDexPalette.colors.textPrimary,
        textAlign = TextAlign.Center
    )

    Text(
        text = reward.phenomenonName,
        style = MaterialTheme.typography.bodyLarge,
        color = SkyDexPalette.colors.textSecondary,
        textAlign = TextAlign.Center
    )

    SkyDexNotice(
        message = UiMessage(
            title = "A foto não bateu com o tempo da região",
            // Non-blaming by construction: "não coincidiram" is about the two datasets, not about
            // the user, and the aside about the data source names the case where the fault is
            // certainly not theirs. Ends pointing at the way out — which is also the overlay's
            // own secondary action, "Registrar outro".
            body = "Comparamos sua foto com os dados meteorológicos da sua região e eles não " +
                "coincidiram desta vez — às vezes a fonte de dados também fica indisponível. " +
                "Essa captura não valeu XP. Quando o fenômeno aparecer de novo por aí, é só " +
                "registrar outro.",
            tone = Tone.NOTICE
        )
    )
}

// ---------------------------------------------------------------------------------------------
// Bonus (level-up and badges)
// ---------------------------------------------------------------------------------------------

/**
 * The second wave: whatever the profile diff turned up, sliding in under the XP.
 *
 * It animates in on its own rather than appearing with the card, because that is exactly what
 * happens — it arrives from a network call made *after* the overlay was already on screen. The
 * animation is honest about the sequencing instead of hiding it.
 */
@Composable
private fun BonusSection(bonus: CaptureRewardBonus) {
    val hasContent = bonus.newLevel != null || bonus.newBadges.isNotEmpty()

    AnimatedVisibility(
        visible = hasContent,
        enter = fadeIn(tween(BonusMillis)) + expandVertically(tween(BonusMillis))
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(SkyDexSpacing.sm)
        ) {
            bonus.newLevel?.let { level ->
                BonusRow(
                    icon = Icons.Default.MilitaryTech,
                    // Only ever reached when the after-level is strictly greater than the
                    // before-level, both read from the profile endpoint. Nothing here is inferred.
                    text = "Você chegou ao nível $level!",
                    tint = SkyDexPalette.colors.success
                )
            }

            bonus.newBadges.forEach { badge ->
                BonusRow(
                    icon = Icons.Default.EmojiEvents,
                    text = "Nova conquista: $badge",
                    tint = SkyDexPalette.colors.success
                )
            }
        }
    }
}

@Composable
private fun BonusRow(icon: ImageVector, text: String, tint: Color) {
    Surface(
        color = SkyDexPalette.colors.successContainer,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(SkyDexSpacing.md),
            horizontalArrangement = Arrangement.spacedBy(SkyDexSpacing.sm),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                // Decorative: the sentence beside it carries the whole message.
                contentDescription = null,
                tint = tint,
                modifier = Modifier.size(SkyDexSpacing.xl)
            )
            Text(
                text = text,
                style = MaterialTheme.typography.titleMedium,
                color = SkyDexPalette.colors.textPrimary
            )
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Rarity intensity
// ---------------------------------------------------------------------------------------------

/**
 * How big the moment is allowed to be, 0 (Common) to 4 (Legendary).
 *
 * The backend's XP curve is 10 / 25 / 60 / 150 / 400, so the gap between a Common and a Legendary
 * is fortyfold — a celebration that looked identical for both would flatten the only progression
 * the game has. This single number drives glow radius, glow opacity, count-up duration and whether
 * the glow keeps pulsing.
 *
 * An unrecognised code degrades to Common rather than throwing: a new rarity added server-side
 * should cost the user a subdued animation, never a crash on the app's peak moment.
 */
private fun rarityWeightOf(rarity: String): Int = when (rarity.uppercase()) {
    "LEGENDARY" -> 4
    "EPIC" -> 3
    "RARE" -> 2
    "UNCOMMON" -> 1
    else -> 0
}

// ---------------------------------------------------------------------------------------------
// Animation constants
// ---------------------------------------------------------------------------------------------

/** Dark enough to push the form back, light enough that the user still sees where they are. */
private const val ScrimAlpha = 0.55f

private const val EntranceMillis = 320
private const val EntranceInitialScale = 0.88f
private const val BloomMillis = 620
private const val BonusMillis = 300

/** Common counts up in 500ms, Legendary in 1300ms. */
private const val CountUpBaseMillis = 500
private const val CountUpPerTierMillis = 200

private const val GlowRadiusBase = 1.1f
private const val GlowRadiusPerTier = 0.22f
private const val GlowAlphaBase = 0.10f
private const val GlowAlphaPerTier = 0.045f

/** EPIC and above. See [XpBloom]. */
private const val PulseFromWeight = 3
private const val PulsePeak = 1.10f
private const val PulseMillis = 1500

// ---------------------------------------------------------------------------------------------
// Previews — every branch, light and dark (audit finding B4)
// ---------------------------------------------------------------------------------------------

private val CommonReward = CaptureReward(
    phenomenonName = "Nublado",
    rarity = "COMMON",
    confirmed = true,
    xpAwarded = 10
)

private val LegendaryReward = CaptureReward(
    phenomenonName = "Tempestade Severa com Granizo",
    rarity = "LEGENDARY",
    confirmed = true,
    xpAwarded = 400
)

private val UnconfirmedReward = CaptureReward(
    phenomenonName = "Tempestade com Trovões",
    rarity = "RARE",
    confirmed = false,
    // Zero, because that is what the backend returns on every unconfirmed path — not a placeholder.
    xpAwarded = 0
)

private val LevelUpReward = LegendaryReward.copy(
    bonus = CaptureRewardBonus(
        newLevel = 3,
        newBadges = listOf("Caçador de Tempestades")
    )
)

@Composable
private fun RewardPreviewHost(darkTheme: Boolean, reward: CaptureReward) {
    SkyDexTheme(darkTheme = darkTheme) {
        Box(modifier = Modifier.background(MaterialTheme.colorScheme.background)) {
            CaptureRewardOverlay(reward = reward, onSeeCaptures = {}, onCaptureAnother = {})
        }
    }
}

@Preview(name = "Recompensa — comum, claro", showBackground = true, heightDp = 720)
@Composable
private fun RewardCommonLightPreview() = RewardPreviewHost(false, CommonReward)

@Preview(
    name = "Recompensa — comum, escuro",
    showBackground = true,
    backgroundColor = 0xFF0B1220,
    heightDp = 720
)
@Composable
private fun RewardCommonDarkPreview() = RewardPreviewHost(true, CommonReward)

@Preview(name = "Recompensa — lendário, claro", showBackground = true, heightDp = 720)
@Composable
private fun RewardLegendaryLightPreview() = RewardPreviewHost(false, LegendaryReward)

@Preview(
    name = "Recompensa — lendário, escuro",
    showBackground = true,
    backgroundColor = 0xFF0B1220,
    heightDp = 720
)
@Composable
private fun RewardLegendaryDarkPreview() = RewardPreviewHost(true, LegendaryReward)

// The two below are the branch to eyeball after any change to the actions: the filled button must
// read "Registrar outro" and "Ver meus registros" must be the quiet one underneath it — the
// inverse of every other preview here. See [RewardCard] for why.

@Preview(
    name = "Recompensa — não confirmado (Registrar outro primeiro), claro",
    showBackground = true,
    heightDp = 720
)
@Composable
private fun RewardUnconfirmedLightPreview() = RewardPreviewHost(false, UnconfirmedReward)

@Preview(
    name = "Recompensa — não confirmado (Registrar outro primeiro), escuro",
    showBackground = true,
    backgroundColor = 0xFF0B1220,
    heightDp = 720
)
@Composable
private fun RewardUnconfirmedDarkPreview() = RewardPreviewHost(true, UnconfirmedReward)

@Preview(name = "Recompensa — subiu de nível, claro", showBackground = true, heightDp = 800)
@Composable
private fun RewardLevelUpLightPreview() = RewardPreviewHost(false, LevelUpReward)

@Preview(
    name = "Recompensa — subiu de nível, escuro",
    showBackground = true,
    backgroundColor = 0xFF0B1220,
    heightDp = 800
)
@Composable
private fun RewardLevelUpDarkPreview() = RewardPreviewHost(true, LevelUpReward)
