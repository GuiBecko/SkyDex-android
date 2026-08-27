package io.github.guibecko.skydex.ui.common

/**
 * How loud a [UiMessage] is allowed to be.
 *
 * There is **no `ERROR` / red tone, and that is the point of the error system**. An ordinary
 * failure — offline, a 401, a rejected photo — is not a catastrophe, it is a fork in the road: the
 * app's job is to say what happened and what to do next, not to alarm. Red (`SkyDexPalette.colors
 * .danger`) stays reserved for destructive *confirmation*, where the user is about to lose
 * something on purpose.
 *
 * The audit (finding A3) found red used in nine places for failures, including on the sentence
 * *"Permissão de localização negada. Ative em Configurações"* — an instruction, painted as a crash.
 */
enum class Tone {
    /** The quietest register: informational, no colour claim. Renders on `surfaceVariant`. */
    NEUTRAL,

    /** Warm amber. Something needs the user's attention or did not go through. */
    NOTICE,

    /** Green. Something good happened — an invite sent, a capture saved. */
    SUCCESS
}

/**
 * One piece of feedback, ready to render.
 *
 * The shape is deliberate. A message that is only a sentence ends up being *either* a diagnosis or
 * an instruction, and the app kept shipping the diagnosis ("Credenciais inválidas ou servidor
 * indisponível."). Splitting it forces both halves to exist:
 *
 * - [title] — what happened, in the user's terms. Short, calm, no final period, no "Erro:", no HTTP
 *   code, no exclamation-mark alarm.
 * - [body] — **what to do next.** If a body cannot name a next step, the message is not finished.
 * - [tone] — carried by the message itself, so no screen ever has to infer the kind of feedback by
 *   comparing the copy against a literal. That inference is finding A5: `FriendsScreen` decided
 *   between grey and red with `message == "Convite enviado!"`, so a single accent could have turned
 *   a success into a failure.
 * - [actionLabel] — the wording of the one recovery affordance, when there is one.
 *
 * Every string here is written by the app, in pt-BR. A backend string never becomes one — see
 * `ErrorPresenter`.
 */
data class UiMessage(
    val title: String,
    val body: String,
    val tone: Tone,
    val actionLabel: String? = null
)
