import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

// Release signing: environment variables (GitHub secrets) win, otherwise keystore/release.properties.
val releaseProps = Properties().apply {
    val file = rootProject.file("keystore/release.properties")
    if (file.exists()) file.inputStream().use { load(it) }
}

fun signingValue(envName: String, propName: String): String? =
    System.getenv(envName)?.takeIf { it.isNotBlank() } ?: releaseProps.getProperty(propName)

android {
    namespace = "com.twooooooh.interpreter"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.twooooooh.theinterpreter"
        minSdk = 24
        targetSdk = 35
        // CI passes -PappVersionCode=<run number> so every build can update the previous one.
        versionCode = (findProperty("appVersionCode") as String?)?.toIntOrNull() ?: 1
        versionName = "2.4.0"
    }

    signingConfigs {
        create("release") {
            signingValue("RELEASE_STORE_FILE", "storeFile")?.let { storeFile = rootProject.file(it) }
            storePassword = signingValue("RELEASE_STORE_PASSWORD", "storePassword")
            keyAlias = signingValue("RELEASE_KEY_ALIAS", "keyAlias")
            keyPassword = signingValue("RELEASE_KEY_PASSWORD", "keyPassword")
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            val releaseSigning = signingConfigs.getByName("release")
            if (releaseSigning.storeFile != null) signingConfig = releaseSigning
        }
    }

    lint {
        checkReleaseBuilds = false
        abortOnError = false
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

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.core)

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.documentfile)
    implementation(libs.kotlinx.coroutines.android)

    testImplementation(libs.junit)
}
