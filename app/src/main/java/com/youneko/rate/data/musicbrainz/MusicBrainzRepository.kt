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
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.serialization.SerializationException
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import retrofit2.HttpException

@Singleton
class MusicBrainzRepository @Inject constructor(
    private val api: MusicBrainzApi,
    private val json: Json,
    private val cacheDao: RemoteMetadataCacheDao,
    private val historyDao: SearchHistoryDao,
    private val settings: SettingsStore,
) {
    suspend fun search(entity: String, query: String, offset: Int = 0): Resource<List<MusicBrainzSearchItem>> = try {
        if (settings.offlineOnly.first()) return Resource.Error(NetworkError.OFFLINE)
        val key = "search:$entity:${query.trim()}:$offset"
        val cached = cacheDao.find(key)
        val now = System.currentTimeMillis()
        if (cached != null && cached.expiresAt > now) {
            return decodeSearch(cached.jsonBody, entity)
        }
        val response = api.search(entity = entity, query = query, offset = offset)
        saveCache(key, json.encodeToString(response), now)
        historyDao.insert(SearchHistoryEntity(UUID.randomUUID().toString(), query.trim(), now))
        response.toSearchItems(entity).takeIf { it.isNotEmpty() }?.let { Resource.Success(it) }
            ?: Resource.Error(NetworkError.NO_RESULTS)
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

    fun searchPager(entity: String, query: String): Flow<PagingData<MusicBrainzSearchItem>> = (Pager(
        config = PagingConfig(pageSize = 25, initialLoadSize = 25, enablePlaceholders = false),
    ) {
        createSearchPagingSource(entity, query)
    }).flow
        .catch { emit(PagingData.empty()) }
        .flowOn(Dispatchers.IO)

    internal fun createSearchPagingSource(entity: String, query: String): PagingSource<Int, MusicBrainzSearchItem> =
        object : PagingSource<Int, MusicBrainzSearchItem>() {
            override suspend fun load(params: LoadParams<Int>): LoadResult<Int, MusicBrainzSearchItem> = try {
                val offset = params.key ?: 0
                when (val result = search(entity, query, offset)) {
                    is Resource.Success -> LoadResult.Page(
                        data = result.value,
                        prevKey = if (offset == 0) null else (offset - 25).coerceAtLeast(0),
                        nextKey = if (result.value.size < 25) null else offset + result.value.size,
                    )
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

    private fun decodeSearch(body: String, entity: String): Resource<List<MusicBrainzSearchItem>> =
        runCatching { json.decodeFromString<MbSearchResponse>(body).toSearchItems(entity) }
            .fold(
                onSuccess = { it.takeIf(List<MusicBrainzSearchItem>::isNotEmpty)?.let { value -> Resource.Success(value) } ?: Resource.Error(NetworkError.NO_RESULTS) },
                onFailure = { Resource.Error(NetworkError.PARSE_ERROR, "Không đọc được cache tìm kiếm") },
            )

    companion object {
        const val CACHE_TTL_MS = 30L * 24L * 60L * 60L * 1_000L
    }
}
