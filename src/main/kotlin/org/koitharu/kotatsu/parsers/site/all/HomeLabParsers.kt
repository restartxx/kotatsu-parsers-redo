package org.koitharu.kotatsu.parsers.site.all

import androidx.annotation.Keep
import org.json.JSONArray
import okhttp3.Request
import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaParser
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.*
import java.util.concurrent.TimeUnit
import kotlin.random.Random

// CLASSE BASE (Lógica Compartilhada)
abstract class BaseHomeLabParser(
    context: MangaLoaderContext,
    private val myBaseUrl: String
) : MangaParser(context) {

    // SOBRESCREVENDO ID MANUALMENTE
    // Como não passamos o Enum no super(), precisamos definir um ID fixo
    // Usamos o hash da URL pra gerar um Long único e constante
    override val id: Long = myBaseUrl.hashCode().toLong()

    override val configKeyDomain = myBaseUrl
    override val domain = myBaseUrl

    override val client = super.client.newBuilder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    override suspend fun getList(offset: Int, query: String?): List<Manga> {
        val request = Request.Builder().url("$domain/api/list").build()
        val response = client.newCall(request).execute()
        
        if (!response.isSuccessful) throw Exception("Erro HomeLab: ${response.code}")

        val json = JSONArray(response.body?.string())
        val list = mutableListOf<Manga>()

        for (i in 0 until json.length()) {
            val item = json.getJSONObject(i)
            val idStr = item.getString("id")
            
            if (!query.isNullOrBlank() && !item.getString("title").contains(query, true)) {
                continue
            }

            list.add(Manga(
                id = generateUid(idStr), 
                title = item.getString("title"),
                url = "/api/chapters/$idStr", 
                publicUrl = "", 
                coverUrl = item.optString("cover_url").takeIf { it.isNotEmpty() },
                source = this
            ))
        }
        return list
    }

    override suspend fun getDetails(manga: Manga): MangaDetails {
        val request = Request.Builder().url("$domain${manga.url}").build()
        val response = client.newCall(request).execute()
        val json = JSONArray(response.body?.string())

        val chapters = mutableListOf<MangaChapter>()
        for (i in 0 until json.length()) {
            val item = json.getJSONObject(i)
            val chapterId = item.getString("id")
            // Truque da URL
            val pagesEndpoint = "/api/pages/${manga.url.removePrefix("/api/chapters/")}/$chapterId"

            chapters.add(MangaChapter(
                id = generateUid(chapterId), 
                title = item.getString("title"),
                url = pagesEndpoint,
                uploadDate = 0L,
                source = this,
                scanlator = "HomeServer"
            ))
        }

        return MangaDetails(
            title = manga.title,
            description = "HomeLab em: $domain",
            chapters = chapters,
            coverUrl = manga.coverUrl
        )
    }

    override suspend fun getPages(chapter: MangaChapter): List<MangaPage> {
        val request = Request.Builder().url("$domain${chapter.url}").build()
        val response = client.newCall(request).execute()
        val jsonArray = JSONArray(response.body?.string())

        val pages = mutableListOf<MangaPage>()
        for (i in 0 until jsonArray.length()) {
            val pagePath = jsonArray.getString(i)
            val fullUrl = if (pagePath.startsWith("http")) pagePath else "$domain$pagePath"
            
            pages.add(MangaPage(
                url = fullUrl,
                source = this
            ))
        }
        return pages
    }
}

// -----------------------------------------------------------
// AS EXTENSÕES EM SI (CAMUFLAGEM TOTAL)
// -----------------------------------------------------------

@Keep
@MangaSourceParser("HOMELAB_LOCAL", "HomeLab (Termux)", type = ContentType.MANGA)
internal class LocalhostParser(context: MangaLoaderContext) : BaseHomeLabParser(context, "http://127.0.0.1:8000")

@Keep
@MangaSourceParser("HOMELAB_WIREGUARD", "HomeLab (Wireguard)", type = ContentType.MANGA)
internal class WireguardParser(context: MangaLoaderContext) : BaseHomeLabParser(context, "http://10.66.66.1:8000")
