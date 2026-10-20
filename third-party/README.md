# Dependencies and licenses

Copyright for NetScout's own code: **2026 Mcpasi**, Apache-2.0
([LICENSE](../LICENSE), [NOTICE](../NOTICE)). Third-party components retain their
own notices. Versions are listed in
[libs.versions.toml](../gradle/libs.versions.toml).

| Component | License | Sources |
| --- | --- | --- |
| Kotlin / Compose Compiler | Apache-2.0 | https://github.com/JetBrains/kotlin |
| AndroidX / Jetpack Compose | Apache-2.0 | https://android.googlesource.com/platform/frameworks/support/ |
| kotlinx.coroutines | Apache-2.0 | https://github.com/Kotlin/kotlinx.coroutines |
| kotlinx.serialization | Apache-2.0 | https://github.com/Kotlin/kotlinx.serialization |
| OkHttp / Okio | Apache-2.0 | https://github.com/square/okhttp / https://github.com/square/okio |
| jsoup | MIT | https://github.com/jhy/jsoup |
| commonmark-java | MIT | https://github.com/commonmark/commonmark-java |
| ONNX Runtime 1.23.2 | MIT | https://github.com/microsoft/onnxruntime/tree/v1.23.2 |
| Local model and tokenizer | Apache-2.0 | https://huggingface.co/cross-encoder/mmarco-mMiniLMv2-L12-H384-v1/tree/1427fd652930e4ba29e8149678df786c240d8825 |
| JUnit (tests only) | EPL-1.0 | https://github.com/junit-team/junit4 |
| Gradle / wrapper | Apache-2.0 | https://github.com/gradle/gradle/tree/v8.11.1 |

Libraries come from Maven Central or Google Maven, with build plugins also
sourced from the Gradle Plugin Portal. There are no proprietary SDKs, advertising,
tracking, or Google Play Services dependencies. ONNX Runtime is used as a freely
licensed Maven Central AAR; its native libraries are part of that AAR. This does
not mean those libraries are compiled within this project. The model is a
separately downloaded, immutably addressed data asset. The model card, provenance,
and checksums are documented in
[docs/relevance-model.md](../docs/relevance-model.md).

The [ONNX Runtime 1.23.2 third-party notices](ONNX-RUNTIME-ThirdPartyNotices.txt)
are taken unchanged from
https://github.com/microsoft/onnxruntime/blob/v1.23.2/ThirdPartyNotices.txt.

The APK includes license and provenance texts under
`assets/netscout-relevance/`, in addition to its libraries' license files.
