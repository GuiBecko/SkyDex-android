package io.github.guibecko.skydex.ui.common

import io.github.guibecko.skydex.data.repository.backendErrorMessage
import io.github.guibecko.skydex.data.repository.httpStatusCode
import java.io.IOException
import java.io.InterruptedIOException
import java.util.concurrent.TimeoutException

/**
 * # Turning a `Throwable` into something a person can act on
 *
 * One entry point — [toUiMessage] — and one rule above all the others:
 *
 * > **A backend string is never shown to the user.**
 *
 * The backend answers every failure with `{"error": "..."}` in **English**, and some of those
 * strings carry internals: `"Unknown phenomenon: THUNDERSTORM"` names a domain enum. The audit
 * (finding B1) caught `CaptureViewModel` forwarding that envelope straight to a pt-BR screen.
 *
 * The fix is *not* to go back to one blanket sentence — `ApiError.kt` explains why that was worse
 * than it looked: it told the user to retry in cases where retrying is the one thing that cannot
 * work. The fix is to read the backend's message as a **signal**, match it against the strings we
 * know, and answer with **our** pt-BR copy. What comes out of this file is always a Kotlin literal
 * written here; nothing read off the wire is ever returned.
 *
 * ## Order of classification
 *
 * 1. **Transport first.** A timeout and a dropped connection are their own cases, never folded into
 *    a credential or validation failure — that conflation is finding A6, and it matters because the
 *    two demand opposite actions: *fix your typing* vs *wait and try again*.
 * 2. **Then HTTP status**, then the backend string, then the [ErrorContext] — so the same 404 can
 *    read as "esse convite não está mais aqui" on Amigos and "esse registro não existe mais" on
 *    Meus Registros.
 * 3. **Anything unmatched still gets a next step.** There is no "Erro desconhecido" in this file.
 *
 * ## The read-once constraint
 *
 * `Throwable.backendErrorMessage()` consumes the error body — `ResponseBody.string()` closes the
 * source. It is called **at most once** here, before the `when`, and the value is carried through.
 * A caller that also wants it must read it from the throwable *before* calling [toUiMessage].
 */

/**
 * Which screen is asking. Lets one status resolve to different copy depending on what the user was
 * actually trying to do.
 *
 * [NEARBY] is Home's context — the screen lists nearby phenomena.
 */
enum class ErrorContext {
    LOGIN,
    REGISTER,
    CAPTURE_SAVE,
    PHOTO_UPLOAD,
    FEED,
    SKYDEX,
    PROFILE,
    MY_CAPTURES,
    FRIENDS,
    NEARBY
}

/** The wording of the ordinary "do it again" affordance. One string, so it cannot drift. */
private const val RETRY = "Tentar de novo"

/**
 * The one failure in the app that has no [Throwable] behind it.
 *
 * The capture-detail screen resolves its capture from `CaptureRegistry`, an in-memory handoff from
 * the list the user tapped — there is no `GET api/events/{id}` to call. After process death Android
 * restores the route but not the registry, so the screen can be recomposed with a perfectly valid id
 * and nothing to resolve it against. Nothing threw; there is simply no object.
 *
 * It lives here rather than in the screen because this file owns **every** sentence the user reads
 * about something not working, and a message written next to its `if` is how an app ends up with two
 * shades of red and nine different tones. No [ErrorContext] value was added for it: contexts select
 * copy for a *status code*, and this failure never had one — an unused enum branch would be dead
 * code pretending to be a policy.
 *
 * The action is "Voltar" because it is the only one that can work: retrying resolves against the
 * same empty registry, and re-opening the capture from the list re-populates it.
 */
val CaptureUnavailable = UiMessage(
    title = "Não foi possível abrir este registro",
    body = "Volte para a lista e toque no registro de novo para ver os detalhes.",
    tone = Tone.NOTICE,
    actionLabel = "Voltar"
)

/** The wording for "re-read the server's list", which is a different promise from [RETRY]. */
private const val REFRESH = "Atualizar"

/**
 * The pt-BR message for this failure, on this screen.
 *
 * @param context the screen that is asking — see [ErrorContext].
 */
fun Throwable.toUiMessage(context: ErrorContext): UiMessage {
    val status = httpStatusCode

    // Read once, keep the value: the error body closes after a single read. Skipped entirely when
    // there is no HTTP response to read one from.
    val backend = if (status == null) null else backendErrorMessage()

    return when {
        // Transport, before anything else. A timeout is not a rejection and offline is not a 401.
        isTimeout() -> Timeout
        status == null && isTransportFailure() -> Offline
        // No response and not an I/O fault: a serialisation bug, a null we did not expect. Nothing
        // useful can be said about the cause, so the copy commits only to "try again".
        status == null -> generic(context)

        status == 401 -> unauthorized(context)
        status == 403 -> forbidden(backend)
        status == 404 -> notFound(context, backend)
        status == 409 -> conflict(context, backend)
        status == 413 -> PhotoTooLarge
        status == 422 -> unprocessable(context)
        // Before `status >= 500`, and the order is the whole point: 503 is not an outage report,
        // it is "an upstream we need is briefly unavailable, and nothing you did was lost".
        status == 503 -> unavailable(context)
        status == 400 -> badRequest(context, backend)
        status >= 500 -> ServerDown
        else -> generic(context)
    }
}

// ---------------------------------------------------------------------------------------------
// Transport
// ---------------------------------------------------------------------------------------------

/** No response at all. The user can fix this one, and the copy says how. */
private val Offline = UiMessage(
    title = "Sem conexão",
    body = "Verifique sua internet e tente de novo.",
    tone = Tone.NOTICE,
    actionLabel = RETRY
)

/** A response that never arrived in time. Distinct from [Offline]: waiting actually helps here. */
private val Timeout = UiMessage(
    title = "A conexão demorou demais",
    body = "Tente de novo em instantes.",
    tone = Tone.NOTICE,
    actionLabel = RETRY
)

/**
 * 5xx. Naming it as ours matters: without that line the user reads a server fault as their own
 * mistake and starts retyping a password that was always correct.
 */
private val ServerDown = UiMessage(
    title = "O SkyDex está fora do ar",
    body = "Não é você — estamos com problema. Tente de novo em instantes.",
    tone = Tone.NOTICE,
    actionLabel = RETRY
)

/** 413. Only one endpoint accepts a body big enough to hit this, so the copy can be specific. */
private val PhotoTooLarge = UiMessage(
    title = "Essa foto é pesada demais",
    body = "Tire uma nova foto e tente de novo.",
    tone = Tone.NOTICE,
    actionLabel = RETRY
)

// ---------------------------------------------------------------------------------------------
// Status-specific copy
// ---------------------------------------------------------------------------------------------

/**
 * 401.
 *
 * On [ErrorContext.LOGIN] the message says the pair does not match and **deliberately does not say
 * which half**. That vagueness is intentional and load-bearing: copy that distinguished "wrong
 * password" from "no such account" would turn the login form into an oracle for enumerating
 * registered e-mail addresses. The backend takes the same position — it answers `401 "Invalid
 * email or password"` for both cases on purpose.
 *
 * What changed relative to the old wording is a different thing entirely: the old string folded a
 * credential failure and a server outage into one sentence ("Credenciais inválidas ou servidor
 * indisponível.", finding A6). Those two want opposite actions from the user, so they are now
 * separate cases — [ServerDown] and [Offline] above. Staying vague about *which credential* stays.
 * The real cause still reaches logcat through the ViewModel's `logWarning`.
 */
private fun unauthorized(context: ErrorContext): UiMessage = when (context) {
    ErrorContext.LOGIN -> UiMessage(
        title = "E-mail ou senha não conferem",
        body = "Confira os dados e tente de novo.",
        tone = Tone.NOTICE
    )

    else -> UiMessage(
        title = "Sua sessão expirou",
        body = "Entre na sua conta de novo para continuar.",
        tone = Tone.NOTICE
    )
}

/** 403. The backend uses this for ownership and for "you cannot befriend yourself". */
private fun forbidden(backend: String?): UiMessage = when {
    backend.mentions("cannot add yourself") -> UiMessage(
        title = "Esse e-mail é o seu",
        body = "Digite o e-mail de um amigo para enviar o convite.",
        tone = Tone.NOTICE
    )

    backend.mentions("request is not yours", "was not sent to you") -> UiMessage(
        title = "Esse convite não é seu",
        body = "Atualize a lista para ver os convites de agora.",
        tone = Tone.NOTICE,
        actionLabel = REFRESH
    )

    backend.mentions("only modify your own captures") -> UiMessage(
        title = "Esse registro não é seu",
        body = "Você só pode alterar os registros que você mesmo fez.",
        tone = Tone.NOTICE
    )

    else -> UiMessage(
        title = "Você não tem acesso a isso",
        body = "Entre na sua conta de novo ou volte para a tela anterior.",
        tone = Tone.NOTICE
    )
}

/** 404. Same status, three very different next steps depending on what was missing. */
private fun notFound(context: ErrorContext, backend: String?): UiMessage = when {
    backend.mentions("no user with that email") -> UnknownEmail

    backend.mentions("friend request not found") -> UiMessage(
        title = "Esse convite não está mais aqui",
        body = "Atualize a lista para ver os convites de agora.",
        tone = Tone.NOTICE,
        actionLabel = REFRESH
    )

    backend.mentions("capture not found") -> UiMessage(
        title = "Esse registro não existe mais",
        body = "Ele pode ter sido apagado. Atualize a lista.",
        tone = Tone.NOTICE,
        actionLabel = REFRESH
    )

    // The body may be unreadable (a proxy's HTML page, an empty 404). On Amigos the only thing a
    // 404 can mean is that the address has no account, so the screen's own copy is still the right
    // one to fall back to.
    context == ErrorContext.FRIENDS -> UnknownEmail

    else -> UiMessage(
        title = "Não encontramos o que você procura",
        body = "Atualize a tela e tente de novo.",
        tone = Tone.NOTICE,
        actionLabel = REFRESH
    )
}

private val UnknownEmail = UiMessage(
    title = "Não encontramos esse e-mail",
    body = "Confira o endereço e tente de novo.",
    tone = Tone.NOTICE
)

/**
 * 409.
 *
 * Registration is the case the audit called out (finding A6): the app asked *"O e-mail já
 * existe?"* while the server was answering `"Email already registered"` in the same breath. It had
 * the answer and chose to guess. A 409 on the register endpoint has exactly one meaning, so the
 * copy states it — and points at the two ways out.
 */
private fun conflict(context: ErrorContext, backend: String?): UiMessage = when {
    context == ErrorContext.REGISTER || backend.mentions("email already registered") -> UiMessage(
        title = "Este e-mail já tem uma conta",
        body = "Faça login ou use outro e-mail.",
        tone = Tone.NOTICE
    )

    backend.mentions("pending or accepted request") -> UiMessage(
        title = "Vocês já estão conectados",
        body = "O convite já foi enviado ou vocês já são amigos.",
        tone = Tone.NOTICE
    )

    else -> UiMessage(
        title = "Isso já existe",
        body = "Atualize a tela para ver como está agora.",
        tone = Tone.NOTICE,
        actionLabel = REFRESH
    )
}

/**
 * 400 — the server saying "this will never be accepted as it stands".
 *
 * Tasks 12b and 12c wrote distinct, actionable 400s for the capture path alone, and each one
 * implies a different next step: an expired photo needs a new shot, a wrong file type needs a
 * different file. A single "tente de novo" is wrong for all of them.
 */
private fun badRequest(context: ErrorContext, backend: String?): UiMessage = when {
    backend.mentions("photo has expired") -> UiMessage(
        title = "Essa foto expirou",
        body = "Tire uma nova foto para registrar.",
        tone = Tone.NOTICE
    )

    backend.mentions("already been used for a capture") -> UiMessage(
        title = "Essa foto já virou um registro",
        body = "Tire uma nova foto para registrar de novo.",
        tone = Tone.NOTICE
    )

    backend.mentions("not a jpeg", "not a png", "only jpeg and png") -> UiMessage(
        title = "Essa imagem não serve",
        body = "Use uma foto em JPEG ou PNG.",
        tone = Tone.NOTICE
    )

    backend.mentions("photo is not available for this capture") -> UiMessage(
        title = "A foto desse registro não está disponível",
        body = "Tire uma nova foto e salve de novo.",
        tone = Tone.NOTICE
    )

    context == ErrorContext.CAPTURE_SAVE -> UiMessage(
        title = "Não deu para salvar esse registro",
        body = "Confira a foto e tente de novo.",
        tone = Tone.NOTICE,
        actionLabel = RETRY
    )

    context == ErrorContext.PHOTO_UPLOAD -> UiMessage(
        title = "Não deu para enviar essa foto",
        body = "Tire uma nova foto e tente de novo.",
        tone = Tone.NOTICE
    )

    else -> UiMessage(
        title = "Alguma informação não foi aceita",
        body = "Confira os dados e tente de novo.",
        tone = Tone.NOTICE
    )
}

/**
 * 422 — the request was fine, the content was not.
 *
 * One endpoint produces it: `POST /api/photos`, when the vision model does not believe the picture
 * is an outdoor sky. Distinct from a 400 on purpose, because the instructions differ. A 400 means
 * re-check what you typed; this means point the camera somewhere else.
 */
private fun unprocessable(context: ErrorContext): UiMessage = when (context) {
    ErrorContext.PHOTO_UPLOAD -> UiMessage(
        title = "Essa foto não parece o céu",
        body = "Aponte a câmera para cima e tire outra foto do fenômeno.",
        tone = Tone.NOTICE
    )

    else -> UiMessage(
        title = "Não deu para aceitar isso",
        body = "Confira o que você enviou e tente de novo.",
        tone = Tone.NOTICE
    )
}

/**
 * 503 — an upstream the server needs did not answer.
 *
 * Two sources, and the copy differs because what survives differs. On upload nothing was written
 * at all. On save, the backend raises the 503 *before* the photo is spent — `PhotoProvenanceService
 * .consume` runs inside the commit transaction, which is never reached — so the photo is still
 * citable and pressing Save again is genuinely all that is needed. Saying "tire outra foto" here
 * would send the user to redo work that was never lost.
 */
private fun unavailable(context: ErrorContext): UiMessage = when (context) {
    ErrorContext.PHOTO_UPLOAD -> UiMessage(
        title = "Não conseguimos analisar a foto agora",
        body = "Tente de novo em instantes.",
        tone = Tone.NOTICE,
        actionLabel = RETRY
    )

    ErrorContext.CAPTURE_SAVE -> UiMessage(
        title = "Não conseguimos conferir o clima agora",
        body = "Sua foto está salva. Toque em salvar de novo em instantes.",
        tone = Tone.NOTICE,
        actionLabel = RETRY
    )

    else -> UiMessage(
        title = "Esse serviço está indisponível agora",
        body = "Tente de novo em instantes.",
        tone = Tone.NOTICE,
        actionLabel = RETRY
    )
}

/**
 * The catch-all — reached by an unmatched status and by a failure that never became a response.
 *
 * It is per-screen rather than one global sentence because "não deu para carregar" has to say
 * *what* did not load, and because the recovery differs: a feed offers a refresh, a login form does
 * not. What it never says is "erro desconhecido": the user cannot act on that.
 */
private fun generic(context: ErrorContext): UiMessage = when (context) {
    ErrorContext.LOGIN -> UiMessage(
        "Não deu para entrar agora", "Tente de novo em instantes.", Tone.NOTICE
    )

    ErrorContext.REGISTER -> UiMessage(
        "Não deu para criar sua conta agora", "Tente de novo em instantes.", Tone.NOTICE
    )

    ErrorContext.CAPTURE_SAVE -> UiMessage(
        "Não deu para salvar seu registro", "Tente de novo em instantes.", Tone.NOTICE, RETRY
    )

    ErrorContext.PHOTO_UPLOAD -> UiMessage(
        "Não deu para enviar a foto", "Tente de novo em instantes.", Tone.NOTICE, RETRY
    )

    ErrorContext.FEED -> UiMessage(
        "Não deu para carregar o feed", "Tente de novo em instantes.", Tone.NOTICE, RETRY
    )

    ErrorContext.SKYDEX -> UiMessage(
        "Não deu para carregar sua coleção", "Tente de novo em instantes.", Tone.NOTICE, RETRY
    )

    ErrorContext.PROFILE -> UiMessage(
        "Não deu para carregar seu perfil", "Tente de novo em instantes.", Tone.NOTICE, RETRY
    )

    ErrorContext.MY_CAPTURES -> UiMessage(
        "Não deu para carregar seus registros", "Tente de novo em instantes.", Tone.NOTICE, RETRY
    )

    // Amigos runs four different operations through one context (convidar, aceitar, recusar,
    // carregar), so the fallback has to read correctly after any of them.
    ErrorContext.FRIENDS -> UiMessage(
        "Não deu para concluir agora", "Tente de novo em instantes.", Tone.NOTICE, RETRY
    )

    ErrorContext.NEARBY -> UiMessage(
        "Não deu para carregar os eventos próximos", "Tente de novo em instantes.", Tone.NOTICE, RETRY
    )
}

// ---------------------------------------------------------------------------------------------
// Classification helpers
// ---------------------------------------------------------------------------------------------

/**
 * Case-insensitive substring match against the backend's envelope.
 *
 * Substring and not equality on purpose: a message like `"Photo has expired; take a new one"`
 * carries a clause this file only needs a fragment of ("photo has expired"), and a future
 * rewording of the rest of the sentence should not silently downgrade a specific message to the
 * generic one. `null` (unreadable body) never matches, which is what sends the flow to the context
 * fallback.
 */
private fun String?.mentions(vararg needles: String): Boolean {
    val text = this?.lowercase() ?: return false
    return needles.any { text.contains(it.lowercase()) }
}

/**
 * Whether this failure is a connection that never completed — offline, DNS gone, socket reset.
 *
 * Checked against the cause chain and not just the top throwable because the network stack wraps:
 * an `UnknownHostException` routinely arrives inside something else.
 */
private fun Throwable.isTransportFailure(): Boolean = anyInChain { it is IOException }

/**
 * Whether this failure is a timeout specifically.
 *
 * `SocketTimeoutException` extends `InterruptedIOException`, which is also what OkHttp throws for a
 * whole-call timeout, so matching the parent catches both. `TimeoutException` covers the plain
 * `java.util.concurrent` shape.
 *
 * Kotlin's `TimeoutCancellationException` is deliberately absent: it is a `CancellationException`,
 * and `resultOf` rethrows those rather than turning them into failures — see `ResultOf.kt`.
 */
private fun Throwable.isTimeout(): Boolean =
    anyInChain { it is InterruptedIOException || it is TimeoutException }

/**
 * Walks up to [MAX_CAUSE_DEPTH] links of the cause chain. Bounded rather than recursive because a
 * throwable whose cause is itself is legal, and this runs on the failure path where a second
 * exception (or a hang) would be strictly worse than a slightly generic message.
 */
private fun Throwable.anyInChain(predicate: (Throwable) -> Boolean): Boolean {
    var current: Throwable? = this
    var depth = 0
    while (current != null && depth < MAX_CAUSE_DEPTH) {
        if (predicate(current)) return true
        current = current.cause
        depth++
    }
    return false
}

private const val MAX_CAUSE_DEPTH = 5
