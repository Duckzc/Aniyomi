package eu.kanade.tachiyomi.extension.en.mysite

// ─── Imports ──────────────────────────────────────────────────────────────
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.text.SimpleDateFormat
import java.util.Locale

// ─── Extension class ──────────────────────────────────────────────────────
// Rename both the class and the package to match your site.
// Then update:
//   • baseUrl
//   • All CSS selectors (marked with ← EDIT)
//   • dateFormat if the site uses a different pattern
class MySite : ParsedHttpSource() {

    // ── Identity ─────────────────────────────────────────────────────────

    override val name        = "MySite"
    override val baseUrl     = "https://example.com"   // ← EDIT
    override val lang        = "en"
    override val supportsLatest = true

    // ── Date parsing ─────────────────────────────────────────────────────
    // Adjust the pattern to match your site's date format.
    // Examples: "MM/dd/yyyy", "dd MMM yyyy", "yyyy-MM-dd"
    private val dateFormat = SimpleDateFormat("MM/dd/yyyy", Locale.US) // ← EDIT

    // ═════════════════════════════════════════════════════════════════════
    // POPULAR  (Browse → Popular)
    // ═════════════════════════════════════════════════════════════════════

    override fun popularMangaRequest(page: Int): Request =
        GET("$baseUrl/manga-list?sort=views&page=$page", headers)   // ← EDIT URL

    // CSS selector for each card / row in the list
    override fun popularMangaSelector() = "div.manga-card"           // ← EDIT

    override fun popularMangaFromElement(element: Element): SManga =
        SManga.create().apply {
            // href of the title link → stored as a relative URL
            setUrlWithoutDomain(element.selectFirst("a")!!.attr("href"))
            title         = element.selectFirst("h3, .title")!!.text()   // ← EDIT
            thumbnail_url = element.selectFirst("img")!!.absUrl("src")   // ← EDIT
        }

    // Selector for the "Next page" link; null = single-page result
    override fun popularMangaNextPageSelector() = "a.next, a[rel=next]"  // ← EDIT

    // ═════════════════════════════════════════════════════════════════════
    // LATEST  (Browse → Latest)
    // ═════════════════════════════════════════════════════════════════════

    override fun latestUpdatesRequest(page: Int): Request =
        GET("$baseUrl/manga-list?sort=updated&page=$page", headers)  // ← EDIT URL

    // Re-use popular selectors if the page layout is the same
    override fun latestUpdatesSelector()         = popularMangaSelector()
    override fun latestUpdatesFromElement(e: Element) = popularMangaFromElement(e)
    override fun latestUpdatesNextPageSelector() = popularMangaNextPageSelector()

    // ═════════════════════════════════════════════════════════════════════
    // SEARCH
    // ═════════════════════════════════════════════════════════════════════

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val url = "$baseUrl/search".toHttpUrl().newBuilder()
            .addQueryParameter("q", query)
            .addQueryParameter("page", page.toString())
            .build()
        return GET(url, headers)
    }

    override fun searchMangaSelector()               = popularMangaSelector()
    override fun searchMangaFromElement(e: Element)  = popularMangaFromElement(e)
    override fun searchMangaNextPageSelector()        = popularMangaNextPageSelector()

    // ═════════════════════════════════════════════════════════════════════
    // MANGA DETAILS  (tapped from any list)
    // ═════════════════════════════════════════════════════════════════════

    override fun mangaDetailsParse(document: Document): SManga = SManga.create().apply {
        title         = document.selectFirst("h1.title, h1.manga-title")!!.text()  // ← EDIT
        author        = document.selectFirst(".author, .info-author")?.text()       // ← EDIT
        artist        = document.selectFirst(".artist, .info-artist")?.text()       // ← EDIT
        description   = document.selectFirst(".synopsis, .summary, .description")?.text() // ← EDIT
        genre         = document.select("a.genre, a.tag").joinToString { it.text() }       // ← EDIT
        thumbnail_url = document.selectFirst("img.cover, .manga-cover img")?.absUrl("src") // ← EDIT

        status = when (
            document.selectFirst(".status, .manga-status")?.text()?.lowercase()   // ← EDIT
        ) {
            "ongoing", "updating"      -> SManga.ONGOING
            "completed", "finished"    -> SManga.COMPLETED
            "hiatus", "on hiatus"      -> SManga.ON_HIATUS
            "cancelled", "dropped"     -> SManga.CANCELLED
            else                       -> SManga.UNKNOWN
        }
    }

    // ═════════════════════════════════════════════════════════════════════
    // CHAPTER LIST
    // ═════════════════════════════════════════════════════════════════════

    // Selector for each chapter row in the chapter list
    override fun chapterListSelector() = "ul.chapter-list li, .chapters li"  // ← EDIT

    override fun chapterFromElement(element: Element): SChapter = SChapter.create().apply {
        setUrlWithoutDomain(element.selectFirst("a")!!.attr("href"))
        name         = element.selectFirst("a, .chapter-name")!!.text()   // ← EDIT
        chapter_number = name
            .removePrefix("Chapter ")
            .toFloatOrNull() ?: -1f

        // Date — adjust selector and dateFormat above
        date_upload  = element.selectFirst(".date, .chapter-date")
            ?.text()
            ?.let { runCatching { dateFormat.parse(it)?.time }.getOrNull() }
            ?: 0L
    }

    // ═════════════════════════════════════════════════════════════════════
    // PAGES  (reader)
    // ═════════════════════════════════════════════════════════════════════

    override fun pageListParse(document: Document): List<Page> {
        // Case A: images are embedded directly in the reader HTML
        return document.select("div.reader img, #reader-images img").mapIndexed { i, img ->  // ← EDIT
            Page(i, imageUrl = img.absUrl("src").ifEmpty { img.absUrl("data-src") })
        }

        // Case B: image URLs are in a JS variable — uncomment and adapt:
        // val json = document.selectFirst("script:containsData(images)")!!.data()
        // val urls = Regex("""["'](https?://[^"']+\.(?:jpg|png|webp)[^"']*)["']""")
        //     .findAll(json).map { it.groupValues[1] }
        //     .toList()
        // return urls.mapIndexed { i, url -> Page(i, imageUrl = url) }
    }

    // Required by ParsedHttpSource but unused when pageListParse sets imageUrl directly
    override fun imageUrlParse(document: Document) = ""
}
