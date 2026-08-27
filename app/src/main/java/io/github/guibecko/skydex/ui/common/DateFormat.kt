package io.github.guibecko.skydex.ui.common

import java.time.DateTimeException
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.time.temporal.ChronoUnit
import java.util.Locale

/**
 * Every date and time the user reads, formatted in one place.
 *
 * Audit finding A11 was that the app rendered timestamps three different ways, one of them the raw
 * transport value (`Data: 2026-08-07T18:20:00Z`). The fix landed as two separate helpers during the
 * parallel refactor — one in `ui/captures`, one inside `HomeScreen` — which put the finding's own
 * defect (the same question answered differently in different places) back on the table one level up.
 * Both now live here.
 *
 * They stay **two functions**, deliberately, because they answer two different questions:
 *
 * | | [CaptureDate.format] | [formatEventTime] |
 * |---|---|---|
 * | subject | a capture that already happened | a weather slot on the clock |
 * | question | *how long ago?* | *at what time?* |
 * | output | `ontem, 18:40`, `há 3 dias`, `7 de ago` | `14:30` |
 * | on garbage | `null` — the caller drops the line | `--:--` — the card keeps its shape |
 *
 * The two failure contracts differ for the same reason. A capture's date is one optional line in a
 * card that reads fine without it, so an unparseable value is simply not shown — returning the raw
 * text as a fallback is exactly how the ISO string reached the screen in the first place. A
 * phenomenon's time sits in a fixed slot on the right edge of its card; blanking it would make the
 * row jump, so it degrades to a placeholder of the same width instead.
 *
 * Collapsing them into one function would mean picking one of those contracts for both callers, and
 * neither caller can accept the other's.
 */

// ---------------------------------------------------------------------------------------------
// Past captures — relative, human
// ---------------------------------------------------------------------------------------------

/**
 * Turns the backend's `capturedAt` timestamp into something a person reads.
 *
 * `MyCapturesScreen` used to render `"Data: ${capture.capturedAt}"`, which put
 * **`Data: 2026-08-07T18:20:00Z`** on screen (audit finding A11) — a transport detail leaking into
 * the UI, the same class of defect as the English backend strings Phase 2 removed. `FeedScreen`
 * showed no date at all, so the two screens disagreed about whether a capture even has a time.
 * Both now go through [format].
 *
 * ## Why the month names are hardcoded
 *
 * `DateTimeFormatter.ofPattern("d 'de' MMM", ptBR)` resolves month abbreviations through the
 * platform's CLDR data, which differs between the JVM the unit tests run on and the Android
 * runtime the app runs on (and between Android versions) — sometimes `"ago"`, sometimes `"ago."`,
 * sometimes `"Aug"` when the locale is missing. A twelve-element array is deterministic everywhere,
 * which is what lets [format] be asserted on in a plain JUnit test.
 */
object CaptureDate {

    /** pt-BR month abbreviations, indexed by `monthValue - 1`. */
    private val MONTHS_PT_BR = arrayOf(
        "jan", "fev", "mar", "abr", "mai", "jun",
        "jul", "ago", "set", "out", "nov", "dez"
    )

    /** Below this, "há 0 min" would be both wrong and useless. */
    private const val JUST_NOW_MINUTES = 1L

    private const val MINUTES_PER_HOUR = 60L

    /** Up to a week back, "há N dias" beats a calendar date; past that, the date is more useful. */
    private const val RELATIVE_DAYS_WINDOW = 7L

    /**
     * Formats an ISO-8601 timestamp as a short pt-BR phrase, relative for recent captures.
     *
     * | age | renders as |
     * |---|---|
     * | under a minute, or in the future | `agora mesmo` |
     * | under an hour | `há 12 min` |
     * | earlier today | `há 5 h` |
     * | yesterday | `ontem, 18:20` |
     * | 2–6 days | `há 3 dias` |
     * | this year | `7 de ago` |
     * | earlier | `7 de ago de 2025` |
     *
     * A timestamp in the future (clock skew between phone and server) is reported as `agora mesmo`
     * rather than a negative duration.
     *
     * @param raw the backend value. Accepts `2026-08-07T18:20:00Z`, an offset timestamp, a local
     *   date-time with no zone, or a bare `2026-08-07`.
     * @return the phrase, or **`null`** when [raw] cannot be parsed. Callers omit the line in that
     *   case — returning the raw text as a fallback would put the ISO string back on screen, which
     *   is the defect this exists to remove.
     */
    fun format(
        raw: String?,
        now: Instant = Instant.now(),
        zone: ZoneId = ZoneId.systemDefault()
    ): String? {
        val instant = parse(raw, zone) ?: return null

        val moment = instant.atZone(zone)
        val date = moment.toLocalDate()
        val today = now.atZone(zone).toLocalDate()
        val minutes = Duration.between(instant, now).toMinutes()
        val daysAgo = ChronoUnit.DAYS.between(date, today)

        return when {
            minutes < JUST_NOW_MINUTES -> "agora mesmo"
            minutes < MINUTES_PER_HOUR -> "há $minutes min"
            date == today -> "há ${minutes / MINUTES_PER_HOUR} h"
            date == today.minusDays(1) -> "ontem, ${timeOf(moment.hour, moment.minute)}"
            date > today.minusDays(RELATIVE_DAYS_WINDOW) && date < today -> "há $daysAgo dias"
            date.year == today.year -> "${date.dayOfMonth} de ${monthOf(date)}"
            else -> "${date.dayOfMonth} de ${monthOf(date)} de ${date.year}"
        }
    }

    /**
     * Every shape the backend has been seen to send, tried widest first. Anything else yields
     * `null` instead of throwing — a malformed timestamp must not take a list down with it.
     */
    private fun parse(raw: String?, zone: ZoneId): Instant? {
        val text = raw?.trim().orEmpty()
        if (text.isEmpty()) return null

        return try {
            Instant.parse(text)
        } catch (_: DateTimeParseException) {
            try {
                OffsetDateTime.parse(text).toInstant()
            } catch (_: DateTimeParseException) {
                try {
                    LocalDateTime.parse(text).atZone(zone).toInstant()
                } catch (_: DateTimeParseException) {
                    try {
                        LocalDate.parse(text).atStartOfDay(zone).toInstant()
                    } catch (_: DateTimeParseException) {
                        null
                    }
                }
            }
        }
    }

    /** Zero-padded `HH:mm` without going through a locale-dependent formatter. */
    private fun timeOf(hour: Int, minute: Int): String =
        "${hour.toString().padStart(2, '0')}:${minute.toString().padStart(2, '0')}"

    private fun monthOf(date: LocalDate): String = MONTHS_PT_BR[date.monthValue - 1]
}

// ---------------------------------------------------------------------------------------------
// Upcoming weather slots — wall clock
// ---------------------------------------------------------------------------------------------

/** pt-BR shows clock time as 24-hour `HH:mm`. */
private val EventTimeFormatter: DateTimeFormatter =
    DateTimeFormatter.ofPattern("HH:mm", Locale.forLanguageTag("pt-BR"))

/** What a phenomenon card shows when the backend sends something no clock can be read out of. */
private const val UNKNOWN_TIME = "--:--"

/**
 * Formats a phenomenon's `time` field for display on the Home card.
 *
 * This replaces `time.substringAfter("T")`, which was string surgery on a timestamp: it rendered
 * `14:30` only for the exact shape `yyyy-MM-ddTHH:mm`, and leaked `14:30:00` or `14:30:00Z` the
 * moment the payload carried seconds or an offset — a raw ISO detail on the user's screen (A11).
 *
 * Three shapes are accepted, in order of specificity:
 * 1. offset-carrying (`2026-08-07T14:30:00Z`, `...-03:00`) — converted to [zone] first, because an
 *    instant expressed in UTC is not the wall clock the user is standing in;
 * 2. local date-time (`2026-08-07T14:30`), already the user's wall clock;
 * 3. a bare time (`14:30`).
 *
 * Anything else — empty, truncated, a date with no time, a sentence — yields [UNKNOWN_TIME] rather
 * than throwing. This runs on live data on a card in a list; a malformed field must cost a dash,
 * not the screen. Contrast [CaptureDate.format], which returns `null` so its caller can drop the
 * line entirely: that one is an optional extra line, this one is a fixed slot whose disappearance
 * would shift the card's layout.
 *
 * [zone] is a parameter only so tests can pin it; production always uses the device's zone.
 */
internal fun formatEventTime(raw: String, zone: ZoneId = ZoneId.systemDefault()): String {
    val text = raw.trim()
    if (text.isEmpty()) return UNKNOWN_TIME

    val time = parseOrNull { OffsetDateTime.parse(text).atZoneSameInstant(zone).toLocalTime() }
        ?: parseOrNull { LocalDateTime.parse(text).toLocalTime() }
        ?: parseOrNull { LocalTime.parse(text) }
        ?: return UNKNOWN_TIME

    return time.format(EventTimeFormatter)
}

/** `DateTimeException` covers both parse failures and out-of-range field values. */
private inline fun <T> parseOrNull(parse: () -> T): T? =
    try {
        parse()
    } catch (_: DateTimeException) {
        null
    }
