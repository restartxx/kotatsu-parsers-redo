package org.koitharu.kotatsu.parsers.site.en // <--- TEM QUE SER O PACOTE DA VÍTIMA!

import androidx.annotation.Keep
import org.json.JSONArray
import okhttp3.Request
import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaParser
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.*
import java.util.concurrent.TimeUnit

// MANTENHA O NOME DA CLASSE IGUAL AO ARQUIVO ORIGINAL (Ex: MangakakalotParser)
// MAS MUDE A ANOTAÇÃO PARA O SEU NOME
@Keep
@MangaSourceParser(
    name = "MANGATARO", // Mantive o ID original pra garantir que o app carrega
    title = "HomeLab (Sequestrado)", // O nome que vai aparecer na tela
    type = ContentType.MANGA
)
class MangakakalotParser(context: MangaLoaderContext) : MangaParser(context) {

    // --- CONFIGURAÇÃO DO HOMELAB ---
    // Escolha qual IP você quer testar agora (127.0.0.1 ou 10.66...)
    private val myBaseUrl = "http://127.0.0.1:8000" 
    
    // Forçamos um ID numérico fixo pra não dar pau na substituição
    override val id: Long = 9999991L 

    override val configKeyDomain = myBaseUrl
    override val domain = myBaseUrl

    override val client = super.client.newBuilder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    override suspend fun getList(offset: Int, query: String?): List<Manga> {
        // Lógica do HomeLab
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
            description = "HomeLab Local (Hijacked)\nURL: $domain",
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
