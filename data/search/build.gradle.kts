plugins { alias(libs.plugins.kotlin.jvm) }
kotlin { jvmToolchain(17) }
dependencies {
    api(project(":core:search"))
    implementation(libs.coroutines.core)
    implementation(libs.serialization.json)
    implementation(libs.okhttp)
    implementation(libs.jsoup)
    implementation(libs.commonmark)
    testImplementation(libs.junit)
    testImplementation(libs.coroutines.test)
    testImplementation(libs.mockwebserver)
    testImplementation(project(":data:relevance"))
    testRuntimeOnly(libs.onnxruntime.jvm)
}

tasks.test {
    dependsOn(":data:relevance:prepareModel")
    systemProperty("netscout.modelDir", project(":data:relevance").layout.buildDirectory.dir("model/netscout-relevance").get().asFile.absolutePath)
}

tasks.register<JavaExec>("checkLiveSearch") {
    dependsOn("testClasses", ":data:relevance:prepareModel")
    group = "verification"
    description = "Opt-in check of all six keyless sources with real public search results"
    classpath = sourceSets["test"].runtimeClasspath
    mainClass.set("de.netscout.data.LiveSearchCheck")
    args(providers.gradleProperty("searchKeywords").getOrElse("Codex,mobile,android"))
    args(providers.gradleProperty("searchSources").getOrElse("WEB,REDDIT,YOUTUBE,GITHUB,FORUMS,HACKER_NEWS"))
    args(providers.gradleProperty("searchPages").getOrElse("1"))
    systemProperty("netscout.modelDir", project(":data:relevance").layout.buildDirectory.dir("model/netscout-relevance").get().asFile.absolutePath)
    listOf("http.proxyHost", "http.proxyPort", "https.proxyHost", "https.proxyPort", "http.nonProxyHosts", "javax.net.ssl.trustStore").forEach { key ->
        System.getProperty(key)?.let { systemProperty(key, it) }
    }
}

tasks.register<JavaExec>("checkSearchQuality") {
    dependsOn("testClasses", ":data:relevance:prepareModel")
    group = "verification"
    description = "Measure labelled search regressions through real providers and the local model, without live queries"
    classpath = sourceSets["test"].runtimeClasspath
    mainClass.set("de.netscout.data.SearchQualityCheck")
    args(providers.gradleProperty("searchQualityOutput").getOrElse(layout.buildDirectory.file("reports/search-quality.json").get().asFile.absolutePath))
    args(providers.gradleProperty("searchQualityLabel").getOrElse("working-tree"))
    systemProperty("netscout.modelDir", project(":data:relevance").layout.buildDirectory.dir("model/netscout-relevance").get().asFile.absolutePath)
}
