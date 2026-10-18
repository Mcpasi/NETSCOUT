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
        versionCode = providers.gradleProperty("netscout.versionCode").orNull?.toInt() ?: 1
        versionName = "0.1.1"
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
        debug { signingConfig = signingConfigs.getByName("debug") }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { compose = true }
    packaging { resources.excludes += "/META-INF/{AL2.0,LGPL2.1}" }
    testOptions { animationsDisabled = true }
    lint { abortOnError = true; checkDependencies = true }
}
dependencies {
    implementation(project(":core:search"))
    implementation(project(":data:search"))
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.preview)
    implementation(libs.compose.material3)
    implementation(libs.compose.icons)
    implementation(libs.activity.compose)
    implementation(libs.lifecycle.viewmodel)
    implementation(libs.lifecycle.runtime)
    implementation(libs.coroutines.android)
    testImplementation(libs.junit)
    testImplementation(libs.coroutines.test)
    androidTestImplementation(platform(libs.compose.bom))
    androidTestImplementation(libs.compose.test)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.junit)
    debugImplementation(libs.compose.tooling)
    debugImplementation(libs.compose.test.manifest)
}
