package org.dokiteam.doki.parsers.site.natsu.id

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.config.ConfigKey
import org.koitharu.kotatsu.parsers.model.MangaChapter
import org.koitharu.kotatsu.parsers.model.MangaParserSource
import org.koitharu.kotatsu.parsers.site.natsu.NatsuParser
import org.koitharu.kotatsu.parsers.util.generateUid

@MangaSourceParser("KIRYUU", "Kiryuu", "id")
internal class Kiryuu(context: MangaLoaderContext) :
    NatsuParser(context, MangaParserSource.KIRYUU, pageSize = 24) {

    override val configKeyDomain = ConfigKey.Domain("kiryuu03.com")

    override fun onCreateConfig(keys: MutableCollection<ConfigKey<*>>) {
        super.onCreateConfig(keys)
        keys.add(ConfigKey.DisableUpdateChecking(defaultValue = true))
    }

    override suspend fun loadChapters(
        mangaId: String,
        mangaAbsoluteUrl: String,
    ): List<MangaChapter> {
        // Use WebView with polling-based script to extract chapter data
        val pageScript = """
            (() => {
                // Check if we need to click the chapters tab first
                const tabButton = document.querySelector('button[data-key="chapters"]');
                if (tabButton && !window.__kiryuuTabClicked) {
                    console.log('[Kiryuu] Found chapters tab button, clicking...');
                    tabButton.click();
                    window.__kiryuuTabClicked = true;
                    return null; // Keep polling
                }

                // Check if chapter list has loaded
                const chapterElements = document.querySelectorAll('div#chapter-list > div[data-chapter-number]');
                if (chapterElements.length === 0) {
                    console.log('[Kiryuu] Waiting for chapters to load...');
                    return null; // Keep polling
                }

                // Extract chapter data from DOM
                console.log('[Kiryuu] Chapter list loaded with ' + chapterElements.length + ' chapters');
                const chapters = [];

                chapterElements.forEach(element => {
                    const a = element.querySelector('a');
                    if (!a) return;

                    const href = a.getAttribute('href');
                    if (!href) return;

                    const titleSpan = element.querySelector('div.font-medium span');
                    const title = titleSpan ? titleSpan.textContent.trim() : '';

                    const timeElement = element.querySelector('time');
                    const dateText = timeElement ? timeElement.textContent.trim() : null;
                    const dateTime = timeElement ? timeElement.getAttribute('datetime') : null;

                    const chapterNumber = element.getAttribute('data-chapter-number');

                    chapters.push({
                        url: href,
                        title: title,
                        number: chapterNumber,
                        dateText: dateText,
                        dateTime: dateTime
                    });
                });

                console.log('[Kiryuu] Extracted ' + chapters.length + ' chapters');
                return JSON.stringify(chapters);
            })();
        """.trimIndent()

        val html = context.evaluateJs(mangaAbsoluteUrl, pageScript, timeout = 30000L)
            ?.takeIf { it.isNotBlank() }
            ?: throw Exception("Failed to extract chapter data from WebView")

        // Parse the JSON response from JavaScript
        val chaptersJson = org.json.JSONArray(html)
        val chapters = mutableListOf<MangaChapter>()

        for (i in 0 until chaptersJson.length()) {
            val chapterObj = chaptersJson.getJSONObject(i)
            val url = chapterObj.getString("url")
            val title = chapterObj.getString("title")
            val number = chapterObj.optString("number", "-1").toFloatOrNull() ?: -1f
            val dateText = chapterObj.optString("dateText", null)

            chapters.add(
                MangaChapter(
                    id = generateUid(url),
                    title = title,
                    url = url,
                    number = number,
                    volume = 0,
                    scanlator = null,
                    uploadDate = parseDate(dateText),
                    branch = null,
                    source = source,
                )
            )
        }

        return chapters.reversed()
    }
}
