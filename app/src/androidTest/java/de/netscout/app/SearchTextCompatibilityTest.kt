package de.netscout.app

import androidx.test.ext.junit.runners.AndroidJUnit4
import de.netscout.core.ResultMerger
import de.netscout.core.SearchRequest
import de.netscout.core.SearchSource
import de.netscout.data.GitHubProvider
import de.netscout.data.JsonHttpClient
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Exercise the actual parser on Android, whose Java APIs differ from the host JVM. */
@RunWith(AndroidJUnit4::class)
class SearchTextCompatibilityTest {
    @Test fun markdownCodeSurvivesTheProviderOnAndroid() = runBlocking {
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            val request = chain.request()
            val issue = request.url.encodedPath == "/search/issues" &&
                request.url.queryParameter("q").orEmpty().contains("is:issue")
            val body = if (issue) """{"items":[{"id":1,"title":"Kotlin collection example","html_url":"https://github.com/example/library/issues/1","body":"Use `List<Widget>` in Kotlin to store typed values.\n\n```kotlin\nval items = ArrayList<Widget>()\n```"}],"total_count":1}"""
                else """{"items":[],"total_count":0}"""
            Response.Builder().request(request).protocol(Protocol.HTTP_1_1).code(200).message("OK")
                .body(body.toResponseBody()).build()
        }.build()
        try {
            val request = SearchRequest("Widget,kotlin", setOf(SearchSource.GITHUB))
            val page = GitHubProvider(JsonHttpClient(client)).search(request, null)
            assertTrue(page.items.single().searchText.contains("List<Widget>"))
            assertTrue(page.items.single().searchText.contains("ArrayList<Widget>"))
            assertEquals(1, ResultMerger.merge(request, page.items).size)
        } finally {
            client.dispatcher.executorService.shutdown()
            client.connectionPool.evictAll()
        }
    }
}
