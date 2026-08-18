package com.youneko.rate.data.musicbrainz

import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.PagingSource
import androidx.paging.PagingState
import com.youneko.rate.data.SettingsStore
import com.youneko.rate.data.local.dao.RemoteMetadataCacheDao
import com.youneko.rate.data.local.dao.SearchHistoryDao
import com.youneko.rate.data.local.entity.RemoteMetadataCacheEntity
import com.youneko.rate.data.local.entity.SearchHistoryEntity
import java.io.IOException
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

internal data class SearchPage(
    val items: List<MusicBrainzSearchItem>,
    val totalCount: Int,
)

@Singleton
class MusicBrainzRepository @Inject constructor(
    private val api: MusicBrainzApi,
    private val json: Json,
    private val cacheDao: RemoteMetadataCacheDao,
    private val historyDao: SearchHistoryDao,
    private val settings: SettingsStore,
) {
    suspend fun search(entity: String, query: String, offset: Int = 0): Resource<List<MusicBrainzSearchItem>> =
        when (val result = searchPage(entity, query, offset)) {
            is Resource.Success -> Resource.Success(result.value.items)
            is Resource.Error -> result
            Resource.Loading -> Resource.Loading
        }

    private suspend fun searchPage(entity: String, query: String, offset: Int): Resource<SearchPage> = try {
        if (settings.offlineOnly.first()) return Resource.Error(NetworkError.OFFLINE)
        val normalizedQuery = query.trim()
        if (normalizedQuery.length < 2) return Resource.Error(NetworkError.NO_RESULTS)
        val key = "search:$entity:$normalizedQuery:$offset"
        val cached = cacheDao.find(key)
        val now = System.currentTimeMillis()
        if (cached != null && cached.expiresAt > now) {
            when (val decoded = decodeSearchPage(cached.jsonBody, entity)) {
                is Resource.Success -> if (decoded.value.items.isNotEmpty()) return decoded
                is Resource.Error, Resource.Loading -> Unit
            }
            // Do not let an old empty/invalid cache permanently hide live results.
            cacheDao.delete(key)
        }

        val encodedQuery = encodeLuceneQuery(normalizedQuery)
        val response = api.search(entity = entity, query = encodedQuery, offset = offset)
        val page = response.toSearchPage(entity)
        if (page.items.isEmpty() || response.count <= 0) return Resource.Error(NetworkError.NO_RESULTS)

        saveCache(key, json.encodeToString(response), now)
        historyDao.insert(SearchHistoryEntity(UUID.randomUUID().toString(), normalizedQuery, now))
        Resource.Success(page)
    } catch (error: Throwable) {
        error.toNetworkError()
    }

    suspend fun lookupRelease(releaseId: String): Resource<MusicBrainzPreview> = try {
        if (settings.offlineOnly.first()) return Resource.Error(NetworkError.OFFLINE)
        val key = "release:$releaseId"
        val cached = cacheDao.find(key)
        val now = System.currentTimeMillis()
        if (cached != null && cached.expiresAt > now) {
            return runCatching { Resource.Success(json.decodeFromString<MbRelease>(cached.jsonBody).toPreview()) }
                .getOrElse { Resource.Error(NetworkError.PARSE_ERROR, "Không đọc được cache release") }
        }
        val response = api.lookupRelease(releaseId)
        saveCache(key, json.encodeToString(response), now)
        Resource.Success(response.toPreview())
    } catch (error: Throwable) {
        error.toNetworkError()
    }

    fun recentSearches(): Flow<List<SearchHistoryEntity>> = historyDao.observeRecent()
        .catch { emit(emptyList()) }
        .flowOn(Dispatchers.IO)

    fun searchPager(entity: String, query: String): Flow<PagingData<MusicBrainzSearchItem>> = Pager(
        config = PagingConfig(pageSize = 25, initialLoadSize = 25, enablePlaceholders = false),
    ) {
        createSearchPagingSource(entity, query)
    }.flow
        .catch { emit(PagingData.empty()) }
        .flowOn(Dispatchers.IO)

    internal fun createSearchPagingSource(entity: String, query: String): PagingSource<Int, MusicBrainzSearchItem> =
        object : PagingSource<Int, MusicBrainzSearchItem>() {
            override suspend fun load(params: LoadParams<Int>): LoadResult<Int, MusicBrainzSearchItem> = try {
                val offset = params.key ?: 0
                when (val result = searchPage(entity, query, offset)) {
                    is Resource.Success -> {
                        val page = result.value
                        LoadResult.Page(
                            data = page.items,
                            prevKey = if (offset == 0) null else (offset - params.loadSize).coerceAtLeast(0),
                            nextKey = if (page.items.isEmpty() || offset + page.items.size >= page.totalCount) null else offset + page.items.size,
                        )
                    }
                    is Resource.Error -> LoadResult.Error(IOException(result.message ?: result.kind.name))
                    Resource.Loading -> LoadResult.Page(emptyList(), null, null)
                }
            } catch (error: Throwable) {
                LoadResult.Error(error)
            }

            override fun getRefreshKey(state: PagingState<Int, MusicBrainzSearchItem>): Int? =
                state.anchorPosition?.let { position ->
                    state.closestPageToPosition(position)?.prevKey?.plus(25)
                        ?: state.closestPageToPosition(position)?.nextKey?.minus(25)
                }
        }

    private suspend fun saveCache(key: String, body: String, now: Long) {
        cacheDao.upsert(
            RemoteMetadataCacheEntity(
                key = key,
                provider = "musicbrainz",
                jsonBody = body,
                fetchedAt = now,
                expiresAt = now + CACHE_TTL_MS,
            ),
        )
    }

    private fun decodeSearchPage(body: String, entity: String): Resource<SearchPage> =
        runCatching {
            val response = json.decodeFromString<MbSearchResponse>(body)
            response.toSearchPage(entity)
        }.fold(
            onSuccess = { page ->
                page.items.takeIf { it.isNotEmpty() }?.let { Resource.Success(page) }
                    ?: Resource.Error(NetworkError.NO_RESULTS)
            },
            onFailure = { Resource.Error(NetworkError.PARSE_ERROR, "Không đọc được cache tìm kiếm") },
        )

    companion object {
        const val CACHE_TTL_MS = 30L * 24L * 60L * 60L * 1_000L
    }
}

internal fun MbSearchResponse.toSearchPage(entity: String): SearchPage = SearchPage(
    items = toSearchItems(entity),
    totalCount = count,
)

internal fun escapeLuceneQuery(query: String): String = query.map { character ->
    if (character in "+-&|!(){}[]^\"~*?:\\/") "\\$character" else character.toString()
}.joinToString("")

internal fun encodeLuceneQuery(query: String): String =
    URLEncoder.encode(escapeLuceneQuery(query), StandardCharsets.UTF_8.name()).replace("+", "%20")
