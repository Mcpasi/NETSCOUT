# Lokale inhaltliche Trefferbewertung

NetScout trennt die **Suche nach Kandidaten** von der **Bewertung des Themenbezugs**. Die Plattformen liefern weiterhin Keyword-Kandidaten. Ein bloßes Vorkommen der Wörter irgendwo im gesamten Beitrag genügt nicht mehr.

1. Aus dem gelieferten Originaltext werden einzelne, zusammenhängende Sätze/Textabschnitte entnommen. Titel und Abschnitt müssen das gemeinsame Suchthema mit allen eingegebenen Keywords belegen. Die Auswahl kann tief im Text liegen. Verschiedene Listenpunkte, Release-Note-Abschnitte oder weit auseinanderliegende Erwähnungen werden nicht künstlich zu einer Vorschau verbunden.
2. Ein mehrsprachiger **Cross-Encoder** liest die gesamte Suchanfrage gemeinsam mit jeder ausgewählten Textstelle. Er liefert einen gelernten Relevanzwert statt einer Zählung von Wörtern. Je Treffer werden höchstens zwei Textstellen, pro Modellaufruf höchstens vier Paare geprüft; maximal 384 Tokens pro Paar, davon maximal 96 für die Anfrage. Eine negative Modellbewertung verwirft den Kandidaten.
3. Der höchste Modellwert bestimmt die Relevanzsortierung. Plattformrang ist nur ein kleiner Tie-Breaker. Bei „Neueste“ bleibt das Veröffentlichungsdatum die primäre Reihenfolge, die inhaltliche Prüfung gilt trotzdem. Die Vorschau zeigt genau die bewertete Originalstelle; sie ist weder generiert noch eine Erklärung des Modells.

## Modell und Verteilung

- Modell: [cross-encoder/mmarco-mMiniLMv2-L12-H384-v1](https://huggingface.co/cross-encoder/mmarco-mMiniLMv2-L12-H384-v1), trainiert auf multilingual MS MARCO (14 Trainingssprachen, darunter Englisch und Deutsch).
- Unveränderliche Revision: `1427fd652930e4ba29e8149678df786c240d8825`.
- Quantisiertes ONNX-Modell, `model_qint8_arm64.onnx`, 118.620.017 Bytes. Die ARM64-/AVX512-Versionen dieser Revision sind identische Dateien; dasselbe Modell läuft in JVM- und x86_64-Emulatortests.
- SHA-256: `1825907d6c1a9001ff78124780bbde20a614a8c3df3b63409cf3c72c6fe5c8b4`.
- Modelllizenz: Apache-2.0, siehe [Lizenztext](../third-party/APACHE-2.0.txt).
- Laufzeit: Microsoft ONNX Runtime 1.23.2, [MIT-Lizenz](../third-party/ONNX-RUNTIME-LICENSE.txt), ebenfalls in der APK. Die Android-AAR enthält die nativen Bibliotheken.
- Der XLM-R-Unigram-Tokenizer nutzt das Originalvokabular und die originale SentencePiece-Normalisierungstabelle. Der Build wandelt das JSON verlustfrei in ein kompakt lesbares Binärformat um; IDs und Float-Scores bleiben erhalten. Referenz für das Normalisierungsformat: [Hugging Face spm_precompiled](https://github.com/huggingface/spm_precompiled).

`scripts/prepare-relevance-model.py` lädt Modell und Tokenizer ausschließlich beim Build, prüft Größe und SHA-256 aus `relevance-model.lock.json` und verpackt Modell, Tokenizer und Lizenz in die APK. Downloads werden unter `~/.cache/netscout-models` gecacht; GitHub Actions nutzt denselben verifizierten Cache. Es gibt keine zur Laufzeit ausgeführten Downloads und keine Modelländerung durch einen beweglichen `main`-Branch.

Auf Android wird die unkomprimierte Modell-Assetdatei per Memory Mapping geladen, nicht als zusätzliche Datei kopiert. Die nativen Laufzeitbibliotheken werden in der APK komprimiert und bei der Installation nur für die passende CPU extrahiert. Ein Modell pro Prozess, zwei CPU-Threads und kleine Batches begrenzen den Ressourcenbedarf. Erster Modellstart und jede Trefferseite brauchen zusätzliche Rechenzeit; vor allem auf älteren Geräten. Modell und Laufzeit vergrößern die APK deutlich. Suchtexte und Bewertungen verlassen das Gerät nicht; die ausgewählten Suchplattformen erhalten wie zuvor die Keywords. Bei einem Modellfehler wird eine sichtbare Quellenfehlermeldung ausgegeben, keine heimliche Rückkehr zur bloßen UND-Liste.

Der universelle Debug-Build 0.2.0 ist ungefähr 166 MB groß. Er enthält die Laufzeit für ARM64, ARMv7, x86 und x86_64; eine spätere Verteilung pro Architektur kann den Download verkleinern.

## Aussagekraft und Grenzen

Das ist ein lokaler semantischer **Reranker**, keine vollständige semantische Suche über die Plattformindizes. Die Kandidatenabfragen bleiben wörtlich. Es gibt keine automatische Query-Umschreibung, Synonym-/Übersetzungserweiterung oder zusätzliche API-Abfragen. Treffer, die eine Plattform nicht liefert oder deren Text sie nicht bereitstellt, kann das Modell nicht finden. Die konservative Satzprüfung kann passende, über mehrere Absätze erklärte Zusammenhänge auslassen.

Ein Relevanzwert ist **keine kalibrierte Wahrscheinlichkeit** und kein Beweis für einen logischen Zusammenhang. Der Null-Cutoff ist eine einfache Ausschlussschwelle; nachgelagerte Qualitätsarbeit braucht menschlich bewertete, vielfältige Suchbeispiele. Kurze mehrdeutige Anfragen können auch für ein Modell mehr als eine Bedeutung haben. Eigennamen, Negationen, Aufzählungen innerhalb eines Satzes und neue Fachbegriffe können zu Fehlbewertungen führen. NetScout behauptet weder perfekte Treffer noch garantiertes Sprachverständnis. Die unabhängige Textstellenprüfung hilft gerade bei langen Sammelbeiträgen, bei denen ältere Retrieval-Modelle einzelne Erwähnungen zu hoch gewichten.

Die JVM-Tests verwenden das echte quantisierte Modell, feste Tokenizer-Referenzvektoren und zusätzliche beliebige Suchthemen. Ein Android-Gerätetest lädt dasselbe Asset über den Produktionspfad. Mocktests prüfen Fehler, Abbruch, Filterung und Seitenwechsel ohne externe Plattformzugriffe.
