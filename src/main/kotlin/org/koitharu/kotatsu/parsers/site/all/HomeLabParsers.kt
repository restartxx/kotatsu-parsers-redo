package org.koitharu.kotatsu.parsers.site.all

import org.koitharu.kotatsu.parsers.MangaParser
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.model.*
import org.json.JSONArray
import okhttp3.Request
import java.util.concurrent.TimeUnit

// 1. CLASSE BASE (Lógica)
// Nota: Recebe o 'context' e repassa pro pai (MangaParser)
abstract class BaseHomeLabParser(
    context: MangaLoaderContext,
    private val myBaseUrl: String
) : MangaParser(context) {

    // O Kotatsu exige definir um domínio padrão para configs
    override val configKeyDomain = myBaseUrl

    // Como estamos hardcodando o IP, usamos ele direto
    override val domain = myBaseUrl

    override val client = super.client.newBuilder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    override suspend fun getList(offset: Int, query: String?): List<Manga> {
        val request = Request.Builder().url("$domain/api/list").build()
        val response = client.newCall(request).execute()
        
        if (!response.isSuccessful) throw Exception("Erro HomeLab: ${response.code}")

        val json = JSONArray(response.body?.string())
        val list = mutableListOf<Manga>()

        for (i in 0 until json.length()) {
            val item = json.getJSONObject(i)
            val id = item.getString("id")
            
            if (!query.isNullOrBlank() && !item.getString("title").contains(query, true)) {
                continue
            }

            list.add(Manga(
                id = generateUid(id), // O README pede pra usar generateUid pra garantir ID único
                title = item.getString("title"),
                url = "/api/chapters/$id", 
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
            val pagesEndpoint = "/api/pages/${manga.url.removePrefix("/api/chapters/")}/$chapterId"

            chapters.add(MangaChapter(
                id = generateUid(chapterId), // generateUid aqui também
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

// 2. IMPLEMENTAÇÕES COM A ANOTAÇÃO MÁGICA
// O parametro 'name' na anotação deve ser único e minúsculo (ex: homelab_local)

@MangaSourceParser(
    name = "homelab_local",
    title = "HomeLab (Termux Local)",
    language = Language.MULTI
)
class LocalhostParser(context: MangaLoaderContext) : BaseHomeLabParser(context, "http://127.0.0.1:8000")

@MangaSourceParser(
    name = "homelab_wireguard",
    title = "HomeLab (Wireguard VM)",
    language = Language.MULTI
)
class WireguardParser(context: MangaLoaderContext) : BaseHomeLabParser(context, "http://10.66.66.1:8000")
