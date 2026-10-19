package de.netscout.data

import de.netscout.core.ResultMerger
import de.netscout.relevance.OnnxPassageScorer
import de.netscout.relevance.SemanticRelevance
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import java.nio.channels.FileChannel
import java.nio.file.Files
import java.nio.file.Path

/** Diagnostic corpus, not an estimate of precision/recall on the open web. No remote search calls. */
object SearchQualityCheck {
    @JvmStatic fun main(args: Array<String>) = runBlocking {
        val root = Path.of(System.getProperty("netscout.modelDir"))
        val model = OnnxPassageScorer(
            FileChannel.open(root.resolve("model.onnx")).use { it.map(FileChannel.MapMode.READ_ONLY, 0, it.size()) },
            root.resolve("tokenizer.bin").toFile().inputStream(),
        )
        val textResults = model.use {
            val relevance = SemanticRelevance { model }
            SearchQualityFixtures.textCases().map { case ->
                val page = relevance.assess(case.request, SearchQualityFixtures.retrieve(case))
                val accepted = ResultMerger.merge(case.request, page.items).isNotEmpty()
                buildJsonObject {
                    put("id", case.id); put("expected", case.relevant); put("accepted", accepted)
                    put("correct", accepted == case.relevant)
                    page.items.single().assessment?.score?.takeIf(Double::isFinite)?.let { put("score", it) }
                }
            }
        }
        val tp = textResults.count { it.flag("expected") && it.flag("accepted") }
        val fp = textResults.count { !it.flag("expected") && it.flag("accepted") }
        val fn = textResults.count { it.flag("expected") && !it.flag("accepted") }
        val tn = textResults.count { !it.flag("expected") && !it.flag("accepted") }
        val urls = SearchQualityFixtures.urlCases().map { case ->
            val expected = if (case.same) 1 else 2
            val actual = SearchQualityFixtures.mergeUrls(case).size
            buildJsonObject { put("id", case.id); put("expectedUniqueResults", expected); put("actualUniqueResults", actual); put("correct", actual == expected) }
        }
        val reliability = (1..3).flatMap { round ->
            SearchQualityFixtures.reliabilityCases().map { case ->
                val actual = SearchQualityFixtures.reliability(case)
                val correct = actual == SearchQualityFixtures.ReliabilityResult(case.expected, case.expectedItems, case.expectedDuckCalls)
                buildJsonObject {
                    put("id", case.id); put("round", round); put("correct", correct)
                    put("expectedStatus", case.expected.name); put("actualStatus", actual.status.name)
                    put("expectedItems", case.expectedItems); put("actualItems", actual.items)
                    put("expectedFallbackCalls", case.expectedDuckCalls); put("actualFallbackCalls", actual.duckCalls)
                }
            }
        }
        val report = buildJsonObject {
            put("schemaVersion", 1)
            put("label", args.getOrNull(1) ?: "working-tree")
            put("scope", "Fixed diagnostic fixtures; real providers, real ONNX relevance model, no live search calls")
            put("text", buildJsonObject {
                put("cases", textResults.size); put("truePositive", tp); put("falsePositive", fp); put("falseNegative", fn); put("trueNegative", tn)
                put("accuracy", (tp + tn).toDouble() / textResults.size)
                put("precision", if (tp + fp == 0) 0.0 else tp.toDouble() / (tp + fp))
                put("recall", tp.toDouble() / (tp + fn))
                put("f1", 2.0 * tp / (2 * tp + fp + fn))
                put("details", JsonArray(textResults))
            })
            put("urlIdentity", buildJsonObject {
                put("cases", urls.size); put("correct", urls.count { it.flag("correct") })
                put("accuracy", urls.count { it.flag("correct") }.toDouble() / urls.size)
                put("expectedUniqueResults", urls.sumOf { it.number("expectedUniqueResults") })
                put("actualUniqueResults", urls.sumOf { it.number("actualUniqueResults") })
                put("details", JsonArray(urls))
            })
            put("reliability", buildJsonObject {
                put("attempts", reliability.size); put("correct", reliability.count { it.flag("correct") })
                put("contractSuccessRate", reliability.count { it.flag("correct") }.toDouble() / reliability.size)
                put("details", JsonArray(reliability))
            })
        }
        val json = Json { prettyPrint = true }.encodeToString(JsonObject.serializer(), report)
        val output = args.firstOrNull()?.let(Path::of)
        if (output != null) { output.toAbsolutePath().parent?.let(Files::createDirectories); Files.writeString(output, json + "\n") }
        println(json)
    }
}
