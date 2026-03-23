package eu.kanade.tachiyomi.extension.en.sfmcompile

import eu.kanade.tachiyomi.animesource.model.AnimeFilter
import eu.kanade.tachiyomi.animesource.model.AnimeFilterList
import eu.kanade.tachiyomi.animesource.model.AnimesPage
import eu.kanade.tachiyomi.animesource.model.SAnime
import eu.kanade.tachiyomi.animesource.model.SEpisode
import eu.kanade.tachiyomi.animesource.model.Video
import eu.kanade.tachiyomi.animesource.online.ParsedAnimeHttpSource
import eu.kanade.tachiyomi.network.GET
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

class SFMCompile : ParsedAnimeHttpSource() {

    override val name = "SFM Compile"
    override val baseUrl = "https://sfmcompile.club"
    override val lang = "en"
    override val supportsLatest = true

    // ── Popular ──────────────────────────────────────────────────────────

    override fun popularAnimeRequest(page: Int): Request =
        GET("$baseUrl/?filter-by=popular&paged=$page", headers)

    override fun popularAnimeSelector() = "article.snax-item, div.snax-item, .entry-inner"

    override fun popularAnimeFromElement(element: Element): SAnime = SAnime.create().apply {
        val link = element.selectFirst("h2 a, h3 a, .entry-title a")!!
        setUrlWithoutDomain(link.attr("href"))
        title = link.text()
        thumbnail_url = element.selectFirst("img[src*=uploads]")?.absUrl("src")
            ?: element.selectFirst("img[data-src*=uploads]")?.attr("data-src")
        genre = element.select("a[href*=/category/]").joinToString { it.text() }
    }

    override fun popularAnimeNextPageSelector() = "a.next, a[rel=next]"

    // ── Latest ───────────────────────────────────────────────────────────

    override fun latestUpdatesRequest(page: Int): Request =
        GET(if (page == 1) baseUrl else "$baseUrl/page/$page/", headers)

    override fun latestUpdatesSelector() = popularAnimeSelector()
    override fun latestUpdatesFromElement(e: Element) = popularAnimeFromElement(e)
    override fun latestUpdatesNextPageSelector() = popularAnimeNextPageSelector()

    // ── Search ───────────────────────────────────────────────────────────

    override fun searchAnimeRequest(page: Int, query: String, filters: AnimeFilterList): Request {
        val url = baseUrl.toHttpUrl().newBuilder()
        if (query.isNotBlank()) {
            url.addQueryParameter("s", query)
            if (page > 1) url.addQueryParameter("paged", page.toString())
        } else {
            // Category filter
            val cat = filters.filterIsInstance<CategoryFilter>().firstOrNull()?.selected()
            if (cat != null) {
                return GET("$baseUrl/category/$cat/page/$page/", headers)
            }
        }
        return GET(url.build(), headers)
    }

    override fun searchAnimeSelector() = popularAnimeSelector()
    override fun searchAnimeFromElement(e: Element) = popularAnimeFromElement(e)
    override fun searchAnimeNextPageSelector() = popularAnimeNextPageSelector()

    // ── Anime details ────────────────────────────────────────────────────

    override fun animeDetailsParse(document: Document): SAnime = SAnime.create().apply {
        title = document.selectFirst("h1.entry-title, h1.post-title, h1")!!.text()
        genre = document.select("a[href*=/category/], a[href*=/tag/]")
            .joinToString { it.text() }
        description = document.selectFirst(".entry-content p, .snax-item-description")?.text()
        thumbnail_url = document.selectFirst(
            "meta[property=og:image]"
        )?.attr("content")
        status = SAnime.COMPLETED
    }

    // ── Episode list ─────────────────────────────────────────────────────
    // Each post is a single video — always one episode.

    override fun episodeListParse(document: Document): List<SEpisode> {
        return listOf(
            SEpisode.create().apply {
                setUrlWithoutDomain(document.location())
                name = "Video"
                episode_number = 1f
            }
        )
    }

    override fun episodeListSelector() = "body" // unused, episodeListParse handles it
    override fun episodeFromElement(element: Element) = SEpisode.create() // unused

    // ── Video list ───────────────────────────────────────────────────────

    override fun videoListParse(document: Document): List<Video> {
        // The MP4 is a direct <a> link in the post content
        val mp4Url = document.selectFirst("a[href$=.mp4]")?.absUrl("href")
            ?: Regex("""https?://[^\s"']+\.mp4""")
                .find(document.html())?.value
            ?: return emptyList()

        return listOf(Video(mp4Url, "MP4", mp4Url))
    }

    override fun videoListSelector() = "a[href\$=.mp4]" // unused, videoListParse handles it
    override fun videoFromElement(element: Element) = Video("", "", "") // unused
    override fun videoUrlParse(document: Document) = "" // unused

    // ── Filters ──────────────────────────────────────────────────────────

    override fun getFilterList() = AnimeFilterList(
        CategoryFilter()
    )

    private class CategoryFilter : AnimeFilter.Select<String>(
        "Category",
        arrayOf(
            "All",
            "overwatch", "genshin-impact", "league-of-legend",
            "marvel", "final-fantasy", "resident-evil",
            "fortnite", "baldurs-gate", "lara-croft",
            "metal-gear-solid", "kpop-demon-hunters"
        )
    ) {
        fun selected() = if (state == 0) null else values[state]
    }
}