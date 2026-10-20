package de.netscout.data

import de.netscout.core.MessageKey
import de.netscout.core.SearchMessage
import de.netscout.core.SourceException
import de.netscout.core.SourceStatus
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import okhttp3.Call
import okhttp3.Callback
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.IOException
import java.time.Instant
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** No disk cache or logging interceptor: search terms and credentials stay off disk. */
class JsonHttpClient(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(12, TimeUnit.SECONDS)
        .callTimeout(15, TimeUnit.SECONDS)
        .followRedirects(false)
        .build(),
    private val now: () -> Instant = Instant::now,
) {
    private val cooldowns = ConcurrentHashMap<String, Instant>()

    fun postpone(host: String, seconds: Long) {
        val deadline = now().plusSeconds(seconds.coerceIn(1, 86_400))
        cooldowns.compute(host) { _, old -> if (old != null && old > deadline) old else deadline }
    }

    suspend fun get(url: HttpUrl, headers: Map<String, String> = emptyMap()): JsonObject {
        val body = getText(url, headers)
        return Json.parseToJsonElement(body) as? JsonObject
            ?: throw SourceException(SourceStatus.ERROR, SearchMessage(MessageKey.INVALID_RESPONSE))
    }

    internal suspend fun getText(url: HttpUrl, headers: Map<String, String> = emptyMap()): String {
        if (cooldowns[url.host]?.isAfter(now()) == true) {
            throw SourceException(SourceStatus.RATE_LIMITED, SearchMessage(MessageKey.SOURCE_PAUSE_SEARCH))
        }
        val request = Request.Builder().url(url)
            .header("User-Agent", "android:de.netscout.app:v0.1 (NetScout keyword search)")
            .header("Accept", "application/json")
            .apply { headers.forEach { (key, value) -> header(key, value) } }.build()
        repeat(2) { attempt ->
            try {
                return execute(request)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: IOException) {
                if (attempt == 1) throw failure
                delay(350)
            }
        }
        error("Unreachable")
    }

    private suspend fun execute(request: Request): String = suspendCancellableCoroutine { continuation ->
        val call = client.newCall(request)
        continuation.invokeOnCancellation { call.cancel() }
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                if (!continuation.isCancelled) continuation.resumeWithException(e)
            }
            override fun onResponse(call: Call, response: Response) {
                val result = runCatching {
                    response.use {
                        val retryAfter = it.header("Retry-After")
                        // A 503 may request a maintenance/overload pause, just like a rate limit.
                        val limited = it.code == 429 || (it.code == 403 && it.header("X-RateLimit-Remaining") == "0") ||
                            (it.code == 503 && retryAfter != null)
                        if (limited) {
                            val retry = retryAfter?.let { value ->
                                value.toLongOrNull() ?: runCatching {
                                    ZonedDateTime.parse(value, DateTimeFormatter.RFC_1123_DATE_TIME).toEpochSecond() - now().epochSecond
                                }.getOrNull()
                            } ?: it.header("X-RateLimit-Reset")?.toLongOrNull()?.minus(now().epochSecond) ?: 60
                            postpone(request.url.host, retry)
                            throw SourceException(SourceStatus.RATE_LIMITED, SearchMessage(MessageKey.SOURCE_PAUSE_RETRY))
                        }
                        if (it.code >= 500) throw IOException("Temporary server failure")
                        if (it.code == 401 || it.code == 403) throw SourceException(SourceStatus.SETUP_REQUIRED, SearchMessage(MessageKey.ACCESS_REJECTED))
                        if (!it.isSuccessful) throw SourceException(SourceStatus.ERROR, SearchMessage(MessageKey.HTTP_REJECTED, it.code.toString()))
                        val body = it.body ?: throw IOException("Empty body")
                        val input = body.source()
                        input.request(2_097_153)
                        if (input.buffer.size > 2_097_152) throw SourceException(SourceStatus.ERROR, SearchMessage(MessageKey.RESPONSE_TOO_LARGE))
                        input.readUtf8()
                    }
                }
                if (!continuation.isCancelled) result.fold(continuation::resume, continuation::resumeWithException)
            }
        })
    }
}
