import com.vanniktech.maven.publish.SonatypeHost

plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.maven.publish)
}

android {
    namespace = "com.idanalyzer.docupass"
    compileSdk = 35

    defaultConfig {
        minSdk = 24
        consumerProguardFiles("consumer-rules.pro")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
    }
    // The MediaPipe .task model is already compressed; don't let AAPT re-compress it.
    androidResources {
        noCompress += "task"
    }
    // Publishing variants are configured by the vanniktech maven-publish plugin
    // (see the mavenPublishing block below) — do not add a singleVariant here too.
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)

    // Compose (drop-in UI). Consumers using only the headless API still get these
    // transitively but pay no UI cost unless they render DocuPassView.
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.material3)

    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.okhttp)

    implementation(libs.androidx.camera.core)
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.view)

    implementation(libs.mediapipe.tasks.vision)

    debugImplementation(libs.androidx.compose.ui.tooling)
}

mavenPublishing {
    publishToMavenCentral(SonatypeHost.CENTRAL_PORTAL, automaticRelease = true)
    signAllPublications()
    coordinates("com.idanalyzer", "docupass", "0.1.3")
    pom {
        name.set("DocuPass Android SDK")
        description.set("Native in-app ID verification & KYC for Android (ID Analyzer DocuPass) — document scan, face match, on-device liveness. No WebView.")
        url.set("https://github.com/idanalyzer/docupass-android")
        licenses {
            license {
                name.set("MIT License")
                url.set("https://opensource.org/licenses/MIT")
            }
        }
        developers {
            developer {
                id.set("idanalyzer")
                name.set("ID Analyzer")
                url.set("https://www.idanalyzer.com")
            }
        }
        scm {
            url.set("https://github.com/idanalyzer/docupass-android")
            connection.set("scm:git:https://github.com/idanalyzer/docupass-android.git")
            developerConnection.set("scm:git:ssh://git@github.com/idanalyzer/docupass-android.git")
        }
    }
}
