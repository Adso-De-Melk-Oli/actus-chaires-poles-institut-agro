package io.github.adsodemelkoli.actus

import io.github.adsodemelkoli.actus.data.Article
import io.github.adsodemelkoli.actus.data.Feed
import io.github.adsodemelkoli.actus.data.FeedJson
import io.github.adsodemelkoli.actus.notify.CheckWorker
import io.github.adsodemelkoli.actus.ui.Actions
import io.github.adsodemelkoli.actus.ui.longPress
import io.github.adsodemelkoli.actus.ui.relativeLabel
import io.github.adsodemelkoli.actus.ui.whenLabel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import io.github.adsodemelkoli.actus.data.HttpStatusException
import io.github.adsodemelkoli.actus.data.Problems
import io.github.adsodemelkoli.actus.data.SourceStatus
import io.github.adsodemelkoli.actus.data.downSources
import io.github.adsodemelkoli.actus.data.dueReminders
import io.github.adsodemelkoli.actus.data.eventsOn
import io.github.adsodemelkoli.actus.data.expiredFollows
import io.github.adsodemelkoli.actus.data.latestNews
import io.github.adsodemelkoli.actus.data.matches
import io.github.adsodemelkoli.actus.data.monthCells
import io.github.adsodemelkoli.actus.data.newSince
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth

class LogicTest {
    private val feed: Feed = FeedJson.decodeFromString(Feed.serializer(), javaClass.getResource("/articles.json")!!.readText())
    private fun a(id: String) = feed.articles.first { it.id == id }

    @Test fun lectureDesDonnees() {
        assertTrue(feed.articles.size > 40)
        assertTrue(feed.articles.any { it.source == "polevv" })
        assertEquals("identifiants uniques", feed.articles.size, feed.articles.map { it.id }.toSet().size)
    }

    @Test fun evenementsDuJourSurLesCampus() {
        val ids = CheckWorker.todayOnCampus(feed, LocalDate.of(2026, 11, 5)).map { it.id }.toSet()
        assertEquals("les deux colloques « Couvrir pour produire »", setOf("cvv-ag-plongez-dans-le-rapport-dactivite-2024-de-la-fondation-de-linstitut-ag", "pvv-ag-2026-11-05-colloque-chaires-innov-arbo-et-vigne-vin"), ids)
        // le 3 novembre, seul un webinaire (à distance) : rien sur place
        assertTrue(CheckWorker.todayOnCampus(feed, LocalDate.of(2026, 11, 3)).isEmpty())
        assertTrue(a("adm-ag-webinaire-l-alimentation-comme-porte-d-acces-aux-droits").remote)
    }

    @Test fun nouveautes() {
        val all = feed.articles.map { it.id }.toSet()
        assertTrue(CheckWorker.newItems(feed, all, setOf("agrotic", "masnum")).isEmpty())
        val fresh = CheckWorker.newItems(feed, all - "tic-enquete-sur-la-teledetection-en-agriculture-participez-a-la-mise-a-jou", setOf("agrotic"))
        assertEquals(listOf("tic-enquete-sur-la-teledetection-en-agriculture-participez-a-la-mise-a-jou"), fresh.map { it.id })
        // source non suivie : pas de notification
        assertTrue(CheckWorker.newItems(feed, all - "tic-enquete-sur-la-teledetection-en-agriculture-participez-a-la-mise-a-jou", setOf("polevv")).isEmpty())
        // historique d'une nouvelle source : jamais signalé
        val masnum = feed.articles.first { it.source == "masnum" && it.seed }
        assertTrue(CheckWorker.newItems(feed, all - masnum.id, setOf("masnum")).isEmpty())
    }

    @Test fun filtresPolesEtChaires() {
        assertTrue(a("pvv-ag-2026-11-05-colloque-chaires-innov-arbo-et-vigne-vin").matches("g:poles"))
        assertFalse(a("pvv-ag-2026-11-05-colloque-chaires-innov-arbo-et-vigne-vin").matches("g:chaires"))
        assertTrue(a("tic-enquete-sur-la-teledetection-en-agriculture-participez-a-la-mise-a-jou").matches("g:chaires"))
        assertTrue(a("tic-enquete-sur-la-teledetection-en-agriculture-participez-a-la-mise-a-jou").matches("agrotic"))
        assertTrue(a("tic-enquete-sur-la-teledetection-en-agriculture-participez-a-la-mise-a-jou").matches(null))
        assertEquals(feed.articles.size, feed.articles.count { it.matches("g:poles") } + feed.articles.count { it.matches("g:chaires") })
    }

    @Test fun appuiLong() {
        val done = mutableListOf<String>()
        val actions = Actions(onShare = { done += "partage:" + it.id }, onAddToCalendar = { done += "agenda:" + it.id })
        val press = actions.longPress(LocalDate.of(2026, 11, 5))
        press(a("cvv-ag-plongez-dans-le-rapport-dactivite-2024-de-la-fondation-de-linstitut-ag")) // événement du jour : agenda du téléphone
        press(a("eau-ag-la-chaire-eacc-partenaire-des-carrefours-de-linnovation-inrae")) // événement passé (juin) : partage
        press(a("tic-enquete-sur-la-teledetection-en-agriculture-participez-a-la-mise-a-jou")) // actu : partage
        assertEquals(listOf("agenda:cvv-ag-plongez-dans-le-rapport-dactivite-2024-de-la-fondation-de-linstitut-ag", "partage:eau-ag-la-chaire-eacc-partenaire-des-carrefours-de-linnovation-inrae", "partage:tic-enquete-sur-la-teledetection-en-agriculture-participez-a-la-mise-a-jou"), done)
    }

    @Test fun libellesDeDates() {
        assertEquals("du 19 au 20 novembre", a("tic-ag-les-journees-des-acteurs-du-phenotypage-les-19-et-20-novembre-2026-a-m").whenLabel())
        val t = Article(id = "x", source = "agrotic", title = "t", url = "u", date = "2026-11-04T10:00:00Z")
        assertEquals("Hier", t.relativeLabel(LocalDate.of(2026, 11, 5)))
        assertEquals("Aujourd'hui", t.relativeLabel(LocalDate.of(2026, 11, 4)))
        assertTrue(a("tic-ag-les-journees-des-acteurs-du-phenotypage-les-19-et-20-novembre-2026-a-m").isOn(LocalDate.of(2026, 11, 20)))
        assertFalse(a("tic-ag-les-journees-des-acteurs-du-phenotypage-les-19-et-20-novembre-2026-a-m").isOn(LocalDate.of(2026, 11, 21)))
        // actu sans date (page Actualités de la Chaire UNESCO)
        assertEquals("Date non communiquée", feed.articles.first { it.source == "unesco" && !it.isEvent }.relativeLabel(LocalDate.of(2026, 11, 5)))
    }

    // --- Événements suivis et rappels (Journées des acteurs du phénotypage : jeudi 19 novembre) ---
    private val chaire = "tic-ag-les-journees-des-acteurs-du-phenotypage-les-19-et-20-novembre-2026-a-m"
    private fun due(today: String, followedOn: String = "2026-11-01", sent: Set<String> = emptySet(), at: String = "10:00", veille: Boolean = true, semaine: Boolean = true) =
        dueReminders(feed, mapOf(chaire to LocalDate.parse(followedOn)), sent, LocalDate.parse(today), LocalTime.parse(at), veille, semaine).map { it.key }

    @Test fun rappels() {
        assertEquals("une semaine avant", listOf("$chaire:7"), due("2026-11-12"))
        assertEquals("déjà envoyé", emptyList<String>(), due("2026-11-12", sent = setOf("$chaire:7")))
        assertEquals("rien à J-8", emptyList<String>(), due("2026-11-11"))
        assertEquals("rattrapage si le téléphone était éteint à J-7", listOf("$chaire:7"), due("2026-11-14"))
        assertEquals("la veille", listOf("$chaire:1"), due("2026-11-18", sent = setOf("$chaire:7")))
        assertEquals("pas la nuit", emptyList<String>(), due("2026-11-18", at = "06:30"))
        assertEquals("pas le jour même", emptyList<String>(), due("2026-11-19"))
        assertEquals("suivi la veille : pas de rappel", emptyList<String>(), due("2026-11-18", followedOn = "2026-11-18"))
        assertEquals("suivi à J-4 : seulement la veille", emptyList<String>(), due("2026-11-15", followedOn = "2026-11-15"))
        assertEquals("suivi à J-4, la veille", listOf("$chaire:1"), due("2026-11-18", followedOn = "2026-11-15"))
        assertEquals("rappel de la veille désactivé", emptyList<String>(), due("2026-11-18", sent = setOf("$chaire:7"), veille = false))
        assertEquals("rappel d'une semaine désactivé", emptyList<String>(), due("2026-11-12", semaine = false))
        // une actu ne se « suit » pas
        assertTrue(dueReminders(feed, mapOf("tic-enquete-sur-la-teledetection-en-agriculture-participez-a-la-mise-a-jou" to LocalDate.of(2026, 1, 1)), emptySet(), LocalDate.of(2026, 11, 12), LocalTime.NOON, true, true).isEmpty())
    }

    @Test fun suivisPerimes() {
        val f = mapOf("eau-ag-la-chaire-eacc-partenaire-des-carrefours-de-linnovation-inrae" to LocalDate.of(2026, 5, 1), "inconnu" to LocalDate.of(2026, 10, 1), "cvv-ag-plongez-dans-le-rapport-dactivite-2024-de-la-fondation-de-linstitut-ag" to LocalDate.of(2026, 10, 1))
        assertEquals(setOf("eau-ag-la-chaire-eacc-partenaire-des-carrefours-de-linnovation-inrae", "inconnu"), expiredFollows(feed, f, LocalDate.of(2026, 11, 5)))
        // relevé vide (téléphone hors ligne au premier lancement) : on n'oublie rien
        assertTrue(expiredFollows(Feed(), mapOf("inconnu" to LocalDate.of(2026, 10, 1)), LocalDate.of(2026, 11, 5)).isEmpty())
    }

    @Test fun nouveauDepuisLaDerniereVisite() {
        val all = feed.articles.map { it.id }.toSet()
        assertTrue("jamais quitté l'application : rien de nouveau", newSince(feed, null).isEmpty())
        assertEquals(setOf("cvv-ag-plongez-dans-le-rapport-dactivite-2024-de-la-fondation-de-linstitut-ag"), newSince(feed, all - "cvv-ag-plongez-dans-le-rapport-dactivite-2024-de-la-fondation-de-linstitut-ag"))
        val seed = feed.articles.first { it.seed }.id
        assertTrue("historique d'une nouvelle source", newSince(feed, all - seed).isEmpty())
    }

    @Test fun calendrierDuMois() {
        val nov = monthCells(YearMonth.of(2026, 11))
        assertEquals(42, nov.size)
        assertEquals("le 1er novembre 2026 est un dimanche", LocalDate.of(2026, 11, 1), nov[6])
        assertEquals(LocalDate.of(2026, 11, 30), nov[35])
        assertTrue(nov.subList(36, 42).all { it == null })
        val fev = monthCells(YearMonth.of(2026, 2))
        assertEquals(35, fev.size)
        val juin = monthCells(YearMonth.of(2026, 6)) // commence un lundi
        assertEquals(LocalDate.of(2026, 6, 1), juin[0])
        val ids = eventsOn(feed.articles, LocalDate.of(2026, 11, 6)).map { it.id }.toSet()
        assertEquals(setOf("pvv-ag-2026-11-06-70-ans-de-l-unite-experimentale-de-pech-rouge-inrae"), ids)
        assertEquals(2, eventsOn(feed.articles, LocalDate.of(2026, 11, 5)).size)
    }

    @Test fun widgetDernieresActus() {
        val n = latestNews(feed)
        assertEquals(10, n.size)
        assertTrue(n.none { it.isEvent })
        assertTrue(n.zipWithNext().all { (a, b) -> (a.sortInstant ?: java.time.Instant.MIN) >= (b.sortInstant ?: java.time.Instant.MIN) })
    }

    @Test fun messagesClairs() {
        assertEquals("Pas de connexion Internet.", Problems.plain(java.net.UnknownHostException("adso-de-melk-oli.github.io")))
        assertEquals("Le serveur met trop de temps à répondre.", Problems.plain(java.net.SocketTimeoutException("Read timed out")))
        assertEquals("Le serveur est momentanément indisponible.", Problems.plain(HttpStatusException(503)))
        assertEquals("Les données sont introuvables pour le moment.", Problems.plain(HttpStatusException(404)))
        assertEquals("Les données reçues sont illisibles.", Problems.plain(kotlinx.serialization.SerializationException("Unexpected JSON token")))
        assertEquals("Chargement impossible pour le moment.", Problems.plain(null))
        // aucun message ne laisse passer un code, une adresse ou un nom technique
        listOf(java.net.UnknownHostException("x.io"), HttpStatusException(500), java.io.IOException("ECONNRESET"), RuntimeException("NullPointer"))
            .map { Problems.plain(it) }.forEach { m -> assertFalse(m, Regex("""\d{3}|http|\.io|Exception|ECONN""").containsMatchIn(m)) }
    }

    @Test fun sourcesEnPanne() {
        val panne = feed.copy(sources = feed.sources + mapOf(
            "polead" to SourceStatus(ok = false, count = 2, problem = "le site n'a pas répondu à temps"),
            "polevv" to SourceStatus(ok = false, count = 18, errors = listOf("actus : 404 sur https://x")),
        ))
        assertEquals(listOf("polevv", "polead"), panne.downSources().map { it.first.key })
        assertEquals("le site n'a pas répondu à temps", panne.downSources().last().second.plainProblem)
        assertEquals("ancien relevé sans explication", "le site n'a pas pu être lu", panne.downSources().first().second.plainProblem)
        assertTrue(feed.downSources().isEmpty())
        // champ « problem » lu depuis le JSON du robot
        val json = """{"sources":{"unesco":{"ok":false,"count":3,"errors":["actus : 404 sur u"],"problem":"la page lue jusqu'ici n'existe plus","failDays":2}},"articles":[]}"""
        val f = FeedJson.decodeFromString(Feed.serializer(), json)
        assertEquals("la page lue jusqu'ici n'existe plus", f.sources["unesco"]!!.plainProblem)
        assertEquals(2, f.sources["unesco"]!!.failDays)
    }
}
