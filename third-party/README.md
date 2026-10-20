# Abhängigkeiten und Lizenzen

Copyright für den eigenen NetScout-Code: **2026 Mcpasi**, Apache-2.0
([LICENSE](../LICENSE), [NOTICE](../NOTICE)). Fremde Komponenten behalten ihre
eigenen Rechtevermerke. Die Versionen stehen in
[libs.versions.toml](../gradle/libs.versions.toml).

| Komponente | Lizenz | Quellen |
| --- | --- | --- |
| Kotlin / Compose Compiler | Apache-2.0 | https://github.com/JetBrains/kotlin |
| AndroidX / Jetpack Compose | Apache-2.0 | https://android.googlesource.com/platform/frameworks/support/ |
| kotlinx.coroutines | Apache-2.0 | https://github.com/Kotlin/kotlinx.coroutines |
| kotlinx.serialization | Apache-2.0 | https://github.com/Kotlin/kotlinx.serialization |
| OkHttp / Okio | Apache-2.0 | https://github.com/square/okhttp / https://github.com/square/okio |
| jsoup | MIT | https://github.com/jhy/jsoup |
| commonmark-java | MIT | https://github.com/commonmark/commonmark-java |
| ONNX Runtime 1.23.2 | MIT | https://github.com/microsoft/onnxruntime/tree/v1.23.2 |
| Lokales Modell und Tokenizer | Apache-2.0 | https://huggingface.co/cross-encoder/mmarco-mMiniLMv2-L12-H384-v1/tree/1427fd652930e4ba29e8149678df786c240d8825 |
| JUnit (nur Tests) | EPL-1.0 | https://github.com/junit-team/junit4 |
| Gradle / Wrapper | Apache-2.0 | https://github.com/gradle/gradle/tree/v8.11.1 |

Bibliotheken kommen aus Maven Central oder Google Maven, Build-Plugins zusätzlich
aus dem Gradle Plugin Portal. Es gibt keine proprietären SDKs, Werbe-, Tracking-
oder Google-Play-Services-Abhängigkeiten. ONNX Runtime wird als frei lizenzierte
Maven-Central-AAR verwendet; ihre nativen Bibliotheken sind Bestandteil der AAR.
Das ist keine Behauptung, dass diese Bibliotheken in diesem Projekt selbst
kompiliert werden. Das Modell ist ein gesondert geladenes, unveränderlich
adressiertes Daten-Asset. Modellkarte, Herkunft und Prüfsummen stehen in
[docs/relevance-model.md](../docs/relevance-model.md).

Die [Third-Party Notices von ONNX Runtime 1.23.2](ONNX-RUNTIME-ThirdPartyNotices.txt)
stammen unverändert aus
https://github.com/microsoft/onnxruntime/blob/v1.23.2/ThirdPartyNotices.txt.

Die APK enthält die Lizenz- und Herkunftstexte unter
`assets/netscout-relevance/`, zusätzlich zu den Lizenzdateien ihrer Bibliotheken.
