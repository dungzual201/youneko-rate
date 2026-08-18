package com.youneko.rate.data.musicbrainz

import okhttp3.Interceptor
import okhttp3.Response
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import kotlin.math.min

class TokenBucket(
    private val capacity: Int = 5,
    private val refillMillis: Long = 1_000L,
    private val nowMillis: () -> Long = System::currentTimeMillis,
    private val sleeper: (Long) -> Unit = { Thread.sleep(it) },
) {
    private var tokens = capacity.toDouble()
    private var lastRefill = nowMillis()

    @Synchronized
    fun acquire() {
        while (true) {
            refill()
            if (tokens >= 1.0) {
                tokens -= 1.0
                return
            }
            sleeper((refillMillis * (1.0 - tokens)).toLong().coerceAtLeast(1L))
        }
    }

    @Synchronized
    private fun refill() {
        val now = nowMillis()
        val elapsed = (now - lastRefill).coerceAtLeast(0L)
        if (elapsed > 0) {
            tokens = (tokens + elapsed.toDouble() / refillMillis).coerceAtMost(capacity.toDouble())
            lastRefill = now
        }
    }
}

class TokenBucketInterceptor(private val bucket: TokenBucket) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        bucket.acquire()
        return chain.proceed(chain.request())
    }
}

class MusicBrainzRetryInterceptor(
    private val sleeper: (Long) -> Unit = { Thread.sleep(it) },
    private val maxRetries: Int = 5,
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        var response = chain.proceed(chain.request())
        var retries = 0
        while (retries < maxRetries && (response.code == 503 || response.code == 429)) {
            val delay = if (response.code == 429) {
                retryAfterMillis(response.header("Retry-After")) ?: backoffMillis(retries)
            } else {
                backoffMillis(retries)
            }
            response.close()
            sleeper(delay)
            retries += 1
            response = chain.proceed(chain.request())
        }
        return response
    }

    private fun backoffMillis(retry: Int): Long = min(30_000L, 1_000L * (1L shl retry))

    private fun retryAfterMillis(value: String?): Long? {
        val header = value?.trim() ?: return null
        header.toLongOrNull()?.let { return it * 1_000L }
        return runCatching {
            (ZonedDateTime.parse(header, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant().toEpochMilli() - System.currentTimeMillis()).coerceAtLeast(0L)
        }.getOrNull()
    }
}
