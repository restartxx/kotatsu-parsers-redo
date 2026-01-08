package org.dokiteam.doki.parsers.site.natsu.id

import org.jsoup.Jsoup
import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.config.ConfigKey
import org.koitharu.kotatsu.parsers.model.MangaChapter
import org.koitharu.kotatsu.parsers.model.MangaParserSource
import org.koitharu.kotatsu.parsers.site.natsu.NatsuParser
import org.koitharu.kotatsu.parsers.util.attrAsRelativeUrl
import org.koitharu.kotatsu.parsers.util.generateUid
import org.koitharu.kotatsu.parsers.util.parseHtml
import org.koitharu.kotatsu.parsers.webview.InterceptionConfig

@MangaSourceParser("KIRYUU", "Kiryuu", "id")
internal class Kiryuu(context: MangaLoaderContext) :
    NatsuParser(context, MangaParserSource.KIRYUU, pageSize = 24) {

    override val configKeyDomain = ConfigKey.Domain("kiryuu03.com")

    override suspend fun loadChapters(
        mangaId: String,
        mangaAbsoluteUrl: String,
    ): List<MangaChapter> {
        val chapters = mutableListOf<MangaChapter>()

        try {
            // First, intercept the initial chapter list request from WebView
            val pageScript = """
                (async function() {
                    console.log('[Kiryuu] Starting chapter loading...');

                    // Helper function to wait for element
                    function waitForElement(selector, timeout = 5000) {
                        return new Promise((resolve, reject) => {
                            if (document.querySelector(selector)) {
                                return resolve(document.querySelector(selector));
                            }

                            const observer = new MutationObserver(() => {
                                if (document.querySelector(selector)) {
                                    observer.disconnect();
                                    resolve(document.querySelector(selector));
                                }
                            });

                            observer.observe(document.body, {
                                childList: true,
                                subtree: true
                            });

                            setTimeout(() => {
                                observer.disconnect();
                                reject(new Error('Timeout waiting for ' + selector));
                            }, timeout);
                        });
                    }

                    // Wait for and click the chapters tab
                    try {
                        const tabButton = await waitForElement('button[data-key="chapters"]');
                        console.log('[Kiryuu] Found chapters tab button, clicking...');
                        tabButton.click();

                        // Wait for the AJAX request to complete
                        await new Promise(resolve => setTimeout(resolve, 1500));
                    } catch (error) {
                        console.log('[Kiryuu] Error:', error);
                        // Try direct htmx trigger as fallback
                        const chapterList = document.querySelector('#chapter-list');
                        if (chapterList && typeof htmx !== 'undefined') {
                            htmx.trigger(chapterList, 'getChapterList');
                        }
                    }
                })();
            """.trimIndent()

            // Pattern to match the AJAX request for page 1
            val urlPattern = Regex("wp-admin/admin-ajax\\.php\\?.*manga_id=$mangaId.*page=1.*action=chapter_list")

            val config = InterceptionConfig(
                timeoutMs = 15000L,
                urlPattern = urlPattern,
                pageScript = pageScript,
                maxRequests = 1 // Only need the first page from WebView
            )

            val interceptedRequests = context.interceptWebViewRequests(
                url = mangaAbsoluteUrl,
                config = config
            )

            if (interceptedRequests.isNotEmpty()) {
                // Use the intercepted response body directly - it contains all chapters
                val responseBody = interceptedRequests.first().body
                if (!responseBody.isNullOrEmpty()) {
                    val doc = Jsoup.parse(responseBody)
                    val chapterElements = doc.select("div#chapter-list > div[data-chapter-number]")

                    chapterElements.forEach { element ->
                        val a = element.selectFirst("a") ?: return@forEach
                        val href = a.attrAsRelativeUrl("href")
                        if (href.isBlank()) return@forEach

                        val chapterTitle = element.selectFirst("div.font-medium span")?.text()?.trim() ?: ""
                        val dateText = element.selectFirst("time")?.text()
                        val number = element.attr("data-chapter-number").toFloatOrNull() ?: -1f

                        chapters.add(
                            MangaChapter(
                                id = generateUid(href),
                                title = chapterTitle,
                                url = href,
                                number = number,
                                volume = 0,
                                scanlator = null,
                                uploadDate = parseDate(dateText),
                                branch = null,
                                source = source,
                            ),
                        )
                    }

                    // Return all chapters from the intercepted response
                    if (chapters.isNotEmpty()) {
                        return chapters.reversed()
                    }
                }
            }

            // If WebView interception failed, fall back to standard method
            return super.loadChapters(mangaId, mangaAbsoluteUrl)

        } catch (e: Exception) {
            // Final fallback to standard method
            return super.loadChapters(mangaId, mangaAbsoluteUrl)
        }
    }
}
