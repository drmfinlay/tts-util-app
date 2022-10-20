plugins {
    id("com.android.application")
    id("kotlin-android")
}

android {
    namespace = "com.danefinlay.ttsutil"
    compileSdk = 30
    buildToolsVersion = "30.0.3"

    defaultConfig {
        applicationId = "com.danefinlay.ttsutil"
        minSdk = 15
        targetSdk = 30
        versionCode = 8
        versionName = "4.0.2"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }

    buildTypes {
        release {
            isShrinkResources = true
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
}

configurations.all {
    resolutionStrategy {
        force("androidx.core:core-ktx:1.6.0")
    }
}

dependencies {
    implementation("androidx.appcompat:appcompat:1.3.1")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    implementation("androidx.documentfile:documentfile:1.0.1")
    implementation("androidx.navigation:navigation-fragment-ktx:2.3.5")
    implementation("androidx.navigation:navigation-ui-ktx:2.3.5")
    implementation("androidx.preference:preference-ktx:1.1.1")
    implementation("com.google.android.material:material:1.4.0")
    implementation("org.jetbrains.anko:anko-sdk15:0.9.1")
    implementation("org.jetbrains.anko:anko-support-v4:0.9.1")
    implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk7:1.7.20")
}
