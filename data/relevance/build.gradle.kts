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
val prepareModel by tasks.registering(Exec::class) {
    inputs.files(rootProject.file("relevance-model.lock.json"), rootProject.file("scripts/prepare-relevance-model.py"), rootProject.file("docs/relevance-model.md"), rootProject.file("third-party/APACHE-2.0.txt"), rootProject.file("third-party/ONNX-RUNTIME-LICENSE.txt"))
    outputs.dir(modelAssets)
    commandLine("python3", rootProject.file("scripts/prepare-relevance-model.py"), "--output", modelAssets.get().asFile)
}
tasks.test {
    dependsOn(prepareModel)
    systemProperty("netscout.modelDir", modelAssets.get().asFile.absolutePath)
    maxHeapSize = "512m"
}
