import java.io.FileInputStream
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

val keystorePropertiesFile = rootProject.file("keystore.properties")
val keystoreProperties = Properties()
if (keystorePropertiesFile.exists()) {
    FileInputStream(keystorePropertiesFile).use(keystoreProperties::load)
}

val releaseSigningKeys = listOf("storeFile", "storePassword", "keyAlias", "keyPassword")
val releaseSigningComplete = keystorePropertiesFile.exists() &&
        releaseSigningKeys.all { !keystoreProperties.getProperty(it).isNullOrBlank() } &&
        keystoreProperties.getProperty("storeFile")?.let(rootProject::file)?.isFile == true

val missingReleaseKeystore = rootProject.file(
    "MISSING_RELEASE_SIGNING_CONFIG__see_keystore.properties.example"
)

android {
    namespace = "org.fungalsentinel.app"
    compileSdk = 37

    defaultConfig {
        applicationId = "org.fungalsentinel.app"
        minSdk = 24
        targetSdk = 36
        versionCode = 10
        versionName = "1.3.8"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        create("release") {
            storeFile = if (releaseSigningComplete) {
                rootProject.file(keystoreProperties.getProperty("storeFile"))
            } else {
                missingReleaseKeystore
            }
            storePassword = keystoreProperties.getProperty("storePassword", "")
            keyAlias = keystoreProperties.getProperty("keyAlias", "")
            keyPassword = keystoreProperties.getProperty("keyPassword", "")
        }
    }

    buildTypes {
        release {
            signingConfig = signingConfigs.getByName("release")
            isMinifyEnabled = false // 修复了 optimization 块
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.animation.core)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.animation) // 解决 animateDpAsState
    implementation(libs.androidx.compose.material.icons) // 解决 Icons 报错
    implementation(libs.androidx.navigation.compose) // 解决 navigation 报错
    implementation(libs.androidx.datastore.preferences) // 解决 datastore 报错
    implementation(libs.coil.compose) // 解决 coil/AsyncImage 报错
    implementation(libs.androidx.lifecycle.viewmodel.compose) // ViewModel
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.room.runtime)
    ksp(libs.androidx.room.compiler)
    testImplementation(libs.junit)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
    implementation("androidx.exifinterface:exifinterface:1.3.7")
}