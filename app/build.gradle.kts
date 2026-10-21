plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}
android {
    namespace = "de.netscout.app"
    compileSdk = 35
    defaultConfig {
        applicationId = "de.netscout.app"
        minSdk = 26
        targetSdk = 35
        versionCode = 10
        versionName = "0.4.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    signingConfigs {
        getByName("debug") {
            storeFile = rootProject.file("debug-signing/netscout-debug.jks")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
    }
    buildTypes {
        debug {
            signingConfig = signingConfigs.getByName("debug")
        }
        release {
            isDebuggable = false
            // F-Droid signs the unsigned APK with its own release key.
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { compose = true }
    // Runtime language switching needs both translations in every installation.
    bundle { language { enableSplit = false } }
    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
        jniLibs.useLegacyPackaging = true
    }
    androidResources { noCompress += "onnx" }
    sourceSets["main"].assets.srcDir(project(":data:relevance").layout.buildDirectory.dir("model"))
    testOptions { animationsDisabled = true }
    lint { abortOnError = true; checkDependencies = true }
}
// Only development builds may use a CI run number. Release metadata stays literal
// so fdroid checkupdates and independent builds see the same versionCode.
androidComponents {
    onVariants(selector().withBuildType("debug")) { variant ->
        providers.gradleProperty("netscout.versionCode").orNull?.toInt()?.let { code ->
            variant.outputs.forEach { it.versionCode.set(code) }
        }
    }
}
dependencies {
    implementation(project(":core:search"))
    implementation(project(":data:search"))
    implementation(project(":data:relevance"))
    implementation(libs.onnxruntime.android)
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.preview)
    implementation(libs.compose.material3)
    implementation(libs.compose.icons)
    implementation(libs.activity.compose)
    implementation(libs.lifecycle.viewmodel)
    implementation(libs.lifecycle.runtime)
    implementation(libs.coroutines.android)
    implementation(libs.serialization.json)
    testImplementation(libs.junit)
    testImplementation(libs.coroutines.test)
    androidTestImplementation(platform(libs.compose.bom))
    androidTestImplementation(libs.compose.test)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.junit)
    androidTestImplementation(libs.okhttp)
    debugImplementation(libs.compose.tooling)
    debugImplementation(libs.compose.test.manifest)
}
tasks.named("preBuild") { dependsOn(":data:relevance:prepareModel") }
