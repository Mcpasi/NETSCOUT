plugins { alias(libs.plugins.kotlin.jvm) }
kotlin { jvmToolchain(17) }
dependencies {
    api(project(":core:search"))
    implementation(libs.coroutines.core)
    compileOnly(libs.onnxruntime.jvm)
    testImplementation(libs.onnxruntime.jvm)
    testImplementation(libs.junit)
    testImplementation(libs.coroutines.test)
}

val modelAssets = layout.buildDirectory.dir("model/netscout-relevance")
val modelCache = providers.gradleProperty("netscout.modelCache")
val offlineModel = providers.gradleProperty("netscout.offlineModel").map(String::toBooleanStrict).getOrElse(false) || gradle.startParameter.isOffline
val prepareModel by tasks.registering(Exec::class) {
    inputs.files(rootProject.file("relevance-model.lock.json"), rootProject.file("scripts/prepare-relevance-model.py"), rootProject.file("docs/relevance-model.md"), rootProject.file("third-party/APACHE-2.0.txt"), rootProject.file("third-party/ONNX-RUNTIME-LICENSE.txt"), rootProject.file("third-party/ONNX-RUNTIME-ThirdPartyNotices.txt"), rootProject.file("LICENSE"), rootProject.file("NOTICE"))
    inputs.property("offlineModel", offlineModel)
    inputs.property("modelCache", modelCache.getOrElse("default"))
    outputs.dir(modelAssets)
    commandLine("python3", rootProject.file("scripts/prepare-relevance-model.py"), "--output", modelAssets.get().asFile)
    if (modelCache.isPresent) args("--cache-dir", rootProject.file(modelCache.get()))
    if (offlineModel) args("--offline")
}
tasks.test {
    dependsOn(prepareModel)
    systemProperty("netscout.modelDir", modelAssets.get().asFile.absolutePath)
    maxHeapSize = "512m"
}
