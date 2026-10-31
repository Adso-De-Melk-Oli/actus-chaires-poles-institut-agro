package io.github.adsodemelkoli.actus.data

import androidx.compose.ui.graphics.Color
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

val PARIS: ZoneId = ZoneId.of("Europe/Paris")

/** Le fichier data/articles.json publié par le robot de collecte du site. */
@Serializable
data class Feed(
    val updatedAt: String? = null,
    val sources: Map<String, SourceStatus> = emptyMap(),
    val articles: List<Article> = emptyList(),
)

@Serializable
data class SourceStatus(
    val ok: Boolean = true,
    val count: Int = 0,
    val errors: List<String> = emptyList(),
    /** Cause de l'échec en français courant, écrite par le robot (absente des anciens relevés). */
    val problem: String? = null,
    val failDays: Int = 0,
) {
    val plainProblem: String get() = problem?.takeIf { it.isNotBlank() } ?: "le site n'a pas pu être lu"
}

@Serializable
data class Article(
    val id: String,
    val source: String,
    val kind: String = "actu",
    val title: String,
    val summary: String? = null,
    val date: String? = null,
    val endDate: String? = null,
    val place: String? = null,
    val url: String,
    val image: String? = null,
    val remote: Boolean = false,
    val firstSeen: String? = null,
    val seed: Boolean = false,
) {
    val isEvent: Boolean get() = kind == "agenda"

    /** Jour de publication (actu) ou de début (événement), à l'heure de Paris. */
    val day: LocalDate? get() = parseDay(date)
    val endDay: LocalDate? get() = parseDay(endDate) ?: day

    fun isUpcoming(today: LocalDate): Boolean = isEvent && (endDay?.let { !it.isBefore(today) } ?: false)
    fun isOn(today: LocalDate): Boolean {
        val d = day ?: return false
        return isEvent && !d.isAfter(today) && !(endDay ?: d).isBefore(today)
    }
    /** Sur place (pas à distance), comme dans le digest du matin : pôles et chaires se réunissent aussi hors des campus. */
    val onCampus: Boolean get() = !remote

    val school: School get() = School.of(source)

    /** Instant de tri : date de publication pour une actu, début pour un événement. */
    val sortInstant: Instant? get() = parseInstant(date)

    companion object {
        fun parseDay(v: String?): LocalDate? = when {
            v.isNullOrBlank() -> null
            v.length == 10 -> runCatching { LocalDate.parse(v) }.getOrNull()
            else -> parseInstant(v)?.atZone(PARIS)?.toLocalDate()
        }

        fun parseInstant(v: String?): Instant? = when {
            v.isNullOrBlank() -> null
            v.length == 10 -> parseDay(v)?.atTime(12, 0)?.atZone(PARIS)?.toInstant()
            else -> runCatching { Instant.parse(v) }.getOrNull()
        }
    }
}

/** Les sources suivies (pôles et chaires), avec les couleurs du site et du digest.
 *  (« School » comme dans l'application jumelle, pour garder le même code.) */
enum class School(val key: String, val label: String, val shortLabel: String, val color: Color, val group: String = "") {
    POLEVV("polevv", "Pôle Vigne et Vin", "Pôle Vigne et Vin", Color(0xFF8E2B4E), "poles"),
    POLEAD("polead", "Pôle Alimentation Durable", "Pôle Alimentation Durable", Color(0xFFC9531F), "poles"),
    AGROSYS("agrosys", "Chaire partenariale AgroSYS", "AgroSYS", Color(0xFF2F8F46), "chaires"),
    AGROTIC("agrotic", "Chaire AgroTIC", "AgroTIC", Color(0xFF1F6FD1), "chaires"),
    MASNUM("masnum", "Mas numérique", "Mas numérique", Color(0xFF5B4BC4), "chaires"),
    INNOVARBO("innovarbo", "Chaire Innov'Arbo", "Innov'Arbo", Color(0xFF5F7F1F), "chaires"),
    UNESCO("unesco", "Chaire UNESCO Alimentations du monde", "Chaire UNESCO", Color(0xFF0E7F81), "chaires"),
    EACC("eacc", "Chaire Eau, Agriculture et Changement Climatique", "Chaire EACC", Color(0xFF0B6AA2), "chaires"),
    VIGNEVIN("vignevin", "Chaire d'entreprises Vigne et Vin", "Chaire Vigne et Vin", Color(0xFFA3367A), "chaires"),
    AUTRE("", "Autre", "Autre", Color(0xFF6E6E73));

    companion object {
        val listed = entries.filter { it != AUTRE }
        fun of(key: String) = entries.firstOrNull { it.key == key } ?: AUTRE
    }
}

/** Filtres de regroupement, devant les pastilles des sources (comme sur le site). */
enum class Group(val key: String, val label: String) {
    POLES("g:poles", "Pôles"),
    CHAIRES("g:chaires", "Chaires");
}

/** Le contenu correspond-il au filtre choisi (aucun, une source, ou « g:poles » / « g:chaires ») ? */
fun Article.matches(filter: String?): Boolean =
    filter == null || source == filter || (filter.startsWith("g:") && school.group == filter.removePrefix("g:"))

val FeedJson = Json {
    ignoreUnknownKeys = true
    explicitNulls = false
    coerceInputValues = true
}

/** Les articles affichables, du plus récent au plus ancien (sans date en dernier). */
fun Feed.sorted(): List<Article> = articles
    .filter { it.title.isNotBlank() && it.url.isNotBlank() }
    .sortedWith(compareByDescending<Article, Instant?>(nullsFirst()) { it.sortInstant })

/** Les pôles et chaires dont le site n'a pas pu être lu au dernier relevé. */
fun Feed.downSources(): List<Pair<School, SourceStatus>> =
    School.listed.mapNotNull { s -> sources[s.key]?.takeIf { !it.ok }?.let { s to it } }
