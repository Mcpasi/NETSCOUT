plugins { alias(libs.plugins.kotlin.jvm) }
kotlin { jvmToolchain(17) }
dependencies {
    api(project(":core:search"))
    implementation(libs.coroutines.core)
    implementation(libs.serialization.json)
    implementation(libs.okhttp)
    implementation(libs.jsoup)
    testImplementation(libs.junit)
    testImplementation(libs.coroutines.test)
    testImplementation(libs.mockwebserver)
}

tasks.register<JavaExec>("checkLiveSearch") {
    dependsOn("testClasses")
    group = "verification"
    description = "Opt-in check of real public GitHub/HN search results (consumes API quota)"
    classpath = sourceSets["test"].runtimeClasspath
    mainClass.set("de.netscout.data.LiveSearchCheck")
    args(providers.gradleProperty("searchKeywords").getOrElse("Codex,mobile,android"))
    listOf("http.proxyHost", "http.proxyPort", "https.proxyHost", "https.proxyPort", "http.nonProxyHosts", "javax.net.ssl.trustStore").forEach { key ->
        System.getProperty(key)?.let { systemProperty(key, it) }
    }
}
