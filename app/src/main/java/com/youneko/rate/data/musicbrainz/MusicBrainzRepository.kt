package com.youneko.rate.data.musicbrainz

import com.youneko.rate.data.SettingsStore
import com.youneko.rate.data.local.dao.RemoteMetadataCacheDao
import com.youneko.rate.data.local.dao.SearchHistoryDao
import com.youneko.rate.data.local.entity.RemoteMetadataCacheEntity
import com.youneko.rate.data.local.entity.SearchHistoryEntity
import java.io.IOException
import java.net.SocketTimeoutException
import java.util.UUID
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.PagingSource
import androidx.paging.PagingState
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first
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
    suspend fun search(entity: String, query: String, offset: Int = 0): Resource<List<MusicBrainzSearchItem>> {
        if (settings.offlineOnly.first()) return Resource.Error(NetworkError.OFFLINE)
        val key = "search:$entity:${query.trim()}:$offset"
        val cached = cacheDao.find(key)
        val now = System.currentTimeMillis()
        if (cached != null && cached.expiresAt > now) {
            return decodeSearch(cached.jsonBody, entity)
        }
        return try {
            val response = api.search(entity = entity, query = query, offset = offset)
            saveCache(key, json.encodeToString(response), now)
            historyDao.insert(SearchHistoryEntity(UUID.randomUUID().toString(), query.trim(), now))
            response.toSearchItems(entity).takeIf { it.isNotEmpty() }?.let { Resource.Success(it) }
                ?: Resource.Error(NetworkError.NO_RESULTS)
        } catch (error: Throwable) {
            mapError(error)
        }
    }

    suspend fun lookupRelease(releaseId: String): Resource<MusicBrainzPreview> {
        if (settings.offlineOnly.first()) return Resource.Error(NetworkError.OFFLINE)
        val key = "release:$releaseId"
        val cached = cacheDao.find(key)
        val now = System.currentTimeMillis()
        if (cached != null && cached.expiresAt > now) {
            return runCatching { Resource.Success(json.decodeFromString<MbRelease>(cached.jsonBody).toPreview()) }
                .getOrElse { Resource.Error(NetworkError.PARSE, "Không đọc được cache release") }
        }
        return try {
            val response = api.lookupRelease(releaseId)
            saveCache(key, json.encodeToString(response), now)
            Resource.Success(response.toPreview())
        } catch (error: Throwable) {
            mapError(error)
        }
    }

    fun recentSearches() = historyDao.observeRecent()

    fun searchPager(entity: String, query: String): Flow<PagingData<MusicBrainzSearchItem>> = Pager(
        config = PagingConfig(pageSize = 25, initialLoadSize = 25, enablePlaceholders = false),
    ) {
        object : PagingSource<Int, MusicBrainzSearchItem>() {
            override suspend fun load(params: LoadParams<Int>): LoadResult<Int, MusicBrainzSearchItem> {
                val offset = params.key ?: 0
                return when (val result = search(entity, query, offset)) {
                    is Resource.Success -> LoadResult.Page(
                        data = result.value,
                        prevKey = if (offset == 0) null else (offset - 25).coerceAtLeast(0),
                        nextKey = if (result.value.size < 25) null else offset + result.value.size,
                    )
                    is Resource.Error -> LoadResult.Error(IOException(result.message ?: result.kind.name))
                    Resource.Loading -> LoadResult.Page(emptyList(), null, null)
                }
            }

            override fun getRefreshKey(state: PagingState<Int, MusicBrainzSearchItem>): Int? =
                state.anchorPosition?.let { position -> state.closestPageToPosition(position)?.prevKey?.plus(25) ?: state.closestPageToPosition(position)?.nextKey?.minus(25) }
        }
    }.flow

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
                onFailure = { Resource.Error(NetworkError.PARSE, "Không đọc được cache tìm kiếm") },
            )

    private fun mapError(error: Throwable): Resource.Error = when (error) {
        is HttpException -> when (error.code()) {
            429 -> Resource.Error(NetworkError.RATE_LIMITED, "MusicBrainz đang giới hạn tốc độ")
            408 -> Resource.Error(NetworkError.TIMEOUT)
            else -> Resource.Error(NetworkError.HTTP, "MusicBrainz trả mã HTTP ${error.code()}")
        }
        is SocketTimeoutException -> Resource.Error(NetworkError.TIMEOUT)
        is IOException -> Resource.Error(NetworkError.NO_NETWORK)
        is SerializationException -> Resource.Error(NetworkError.PARSE)
        else -> Resource.Error(NetworkError.UNKNOWN, error.message)
    }

    companion object {
        const val CACHE_TTL_MS = 30L * 24L * 60L * 60L * 1_000L
    }
}
