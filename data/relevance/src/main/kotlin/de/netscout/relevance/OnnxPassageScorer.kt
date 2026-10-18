package de.netscout.relevance

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import java.io.Closeable
import java.io.InputStream
import java.nio.ByteBuffer

class OnnxPassageScorer(model: ByteBuffer, tokenizer: InputStream) : PassageScorer, Closeable {
    private val environment = OrtEnvironment.getEnvironment()
    private val tokenizer = UnigramTokenizer(tokenizer)
    private val session = OrtSession.SessionOptions().use { options ->
        options.setIntraOpNumThreads(2)
        options.setInterOpNumThreads(1)
        environment.createSession(model, options)
    }
    override fun score(query: String, passages: List<String>): DoubleArray {
        if (passages.isEmpty()) return doubleArrayOf()
        val encoded = passages.map { tokenizer.pair(query, it) }
        val length = encoded.maxOf { it.size }
        val ids = encoded.map { row -> LongArray(length) { row.getOrElse(it) { 1L } } }.toTypedArray()
        val masks = encoded.map { row -> LongArray(length) { if (it < row.size) 1L else 0L } }.toTypedArray()
        return OnnxTensor.createTensor(environment, ids).use { input ->
            OnnxTensor.createTensor(environment, masks).use { mask ->
                session.run(mapOf("input_ids" to input, "attention_mask" to mask)).use { result ->
                    @Suppress("UNCHECKED_CAST")
                    (result[0].value as Array<FloatArray>).map { it.single().toDouble() }.toDoubleArray()
                }
            }
        }
    }
    override fun close() = session.close()
}
