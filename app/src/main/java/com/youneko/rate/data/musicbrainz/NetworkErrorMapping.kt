package com.youneko.rate.data.musicbrainz

import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import kotlinx.serialization.SerializationException
import retrofit2.HttpException

fun Throwable.toNetworkError(): Resource.Error = when (this) {
    is HttpException -> when (code()) {
        429, 503 -> Resource.Error(NetworkError.RATE_LIMITED, "MusicBrainz đang giới hạn truy cập")
        in 500..599 -> Resource.Error(NetworkError.SERVER_ERROR, "Máy chủ gặp sự cố")
        in 400..499 -> Resource.Error(NetworkError.BAD_REQUEST, "Từ khóa không hợp lệ")
        else -> Resource.Error(NetworkError.UNKNOWN, message())
    }

    is SocketTimeoutException -> Resource.Error(NetworkError.TIMEOUT)
    is SecurityException,
    is UnknownHostException,
    is IOException,
    -> Resource.Error(NetworkError.NO_CONNECTION)

    is SerializationException -> Resource.Error(NetworkError.PARSE_ERROR, "Không đọc được dữ liệu trả về")
    else -> Resource.Error(NetworkError.UNKNOWN, message)
}
