package de.netscout.app

import android.content.Context
import de.netscout.relevance.OnnxPassageScorer
import de.netscout.relevance.SemanticRelevance
import java.io.FileInputStream
import java.nio.channels.FileChannel

/** Model bundled with the APK; no runtime download, server, credentials or search-text uploads. */
object LocalSemanticSearch {
    @Volatile private var instance: SemanticRelevance? = null

    fun relevance(context: Context): SemanticRelevance = instance ?: synchronized(this) {
        instance ?: create(context.applicationContext).also { instance = it }
    }

    private fun create(context: Context) = SemanticRelevance {
        val model = context.assets.openFd("netscout-relevance/model.onnx").use { descriptor ->
            FileInputStream(descriptor.fileDescriptor).channel.use { channel ->
                channel.map(FileChannel.MapMode.READ_ONLY, descriptor.startOffset, descriptor.declaredLength)
            }
        }
        OnnxPassageScorer(model, context.assets.open("netscout-relevance/tokenizer.bin"))
    }
}
