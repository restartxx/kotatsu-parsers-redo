package org.koitharu.kotatsu.parsers.local

import org.koitharu.kotatsu.parsers.MangaParser
import org.koitharu.kotatsu.parsers.model.*
import org.json.JSONArray
import okhttp3.Request
import java.util.concurrent.TimeUnit

// 1. O CÉREBRO (Lógica compartilhada)
abstract class BaseHomeLabParser : MangaParser {

    // Configurações Padrão
    override val language = Language.MULTI // Aparece em "Outros" ou "Multi"
    override val isNsfwSource = true
    
    // Timeout agressivo pq é rede local (opcional, mas bom pra não travar se o PC tiver desligado)
    override val client = super.client.newBuilder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    // --- LISTAGEM (HOME) ---
    override suspend fun getList(offset: Int, query: String?): List<Manga> {
        // A API Python deve retornar: [{"id": "PastaAutor", "title": "Autor", "cover_url": "http..."}, ...]
        val request = Request.Builder().url("$baseUrl/api/list").build()
        val response = client.newCall(request).execute()
        
        if (!response.isSuccessful) throw Exception("Erro ao conectar no HomeLab: ${response.code}")

        val json = JSONArray(response.body?.string())
        val list = mutableListOf<Manga>()

        for (i in 0 until json.length()) {
            val item = json.getJSONObject(i)
            val id = item.getString("id")
            
            // Filtro de busca Client-Side (Já que a API retorna tudo)
            if (!query.isNullOrBlank() && !item.getString("title").contains(query, true)) {
                continue
            }

            list.add(Manga(
                id = id,
                title = item.getString("title"),
                url = "/api/chapters/$id", // URL relativa para o getDetails
                publicUrl = "", // Não tem link publico web
                coverUrl = item.optString("cover_url").takeIf { it.isNotEmpty() },
                source = this
            ))
        }
        return list
    }

    // --- DETALHES (LISTA DE CAPÍTULOS/PASTAS) ---
    override suspend fun getDetails(manga: Manga): MangaDetails {
        // A API retorna as subpastas (ex: 2026-01, 2026-02)
        val request = Request.Builder().url("$baseUrl${manga.url}").build()
        val response = client.newCall(request).execute()
        val json = JSONArray(response.body?.string())

        val chapters = mutableListOf<MangaChapter>()
        for (i in 0 until json.length()) {
            val item = json.getJSONObject(i)
            val chapterId = item.getString("id") // "2026-01"
            
            // TRUQUE: Montamos a URL das páginas aqui para facilitar o próximo passo
            // Formato esperado pelo Python: /api/pages/{mangaId}/{chapterId}
            val pagesEndpoint = "/api/pages/${manga.id}/$chapterId"

            chapters.add(MangaChapter(
                id = chapterId,
                title = item.getString("title"),
                url = pagesEndpoint, // <--- Guardamos a rota da API aqui
                uploadDate = 0L, // Pode implementar parsing de data se quiser
                source = this,
                scanlator = "HomeServer"
            ))
        }

        return MangaDetails(
            title = manga.title,
            description = "Hospedado em: $baseUrl\nCaminho: ${manga.id}",
            chapters = chapters,
            coverUrl = manga.coverUrl
        )
    }

    // --- LEITOR (IMAGENS) ---
    override suspend fun getPages(chapter: MangaChapter): List<MangaPage> {
        // chapter.url já vem pronto do getDetails: "/api/pages/Autor/Capitulo"
        val request = Request.Builder().url("$baseUrl${chapter.url}").build()
        val response = client.newCall(request).execute()
        val jsonArray = JSONArray(response.body?.string())

        val pages = mutableListOf<MangaPage>()
        for (i in 0 until jsonArray.length()) {
            // A API Python deve retornar URLs absolutas ou relativas
            // Se retornar absoluta (http://...), usa direto. Se relativa, concatena.
            val pagePath = jsonArray.getString(i)
            val fullUrl = if (pagePath.startsWith("http")) pagePath else "$baseUrl$pagePath"
            
            pages.add(MangaPage(
                url = fullUrl,
                source = this
            ))
        }
        return pages
    }
}

// 2. AS IMPLEMENTAÇÕES CONCRETAS (Para aparecerem no menu)

class LocalhostParser : BaseHomeLabParser() {
    override val name = "HomeLab (Termux/Local)"
    override val baseUrl = "http://127.0.0.1:8000" // Porta da API Python
    override val configKey = "homelab_local"
    override val id = 9001L // ID único arbitrário
}

class WireguardParser : BaseHomeLabParser() {
    override val name = "HomeLab (Wireguard VM)"
    override val baseUrl = "http://10.66.66.1:8000" // Porta da API Python
    override val configKey = "homelab_wireguard"
    override val id = 9002L // ID único arbitrário
}

