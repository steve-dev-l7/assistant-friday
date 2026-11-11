plugins {
    alias(libs.plugins.android.application)
}

configurations.all {
    resolutionStrategy {
        force("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.11.0")
        force("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.11.0")
    }
}

android {
    namespace = "com.example.friday"
    compileSdk = 37



    defaultConfig {
        applicationId = "com.example.friday"
        minSdk = 28
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        ndk{
            //noinspection ChromeOsAbiSupport
            abiFilters += listOf("arm64-v8a")
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
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
}

dependencies {
    implementation(libs.androidx.activity.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.core.ktx)
    implementation(libs.material)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    //noinspection UseTomlInstead
    implementation("com.alphacephei:vosk-android:0.3.75")
    //noinspection UseTomlInstead
    implementation("org.tensorflow:tensorflow-lite:2.17.0")
    //noinspection UseTomlInstead
    implementation("com.google.ai.edge.litertlm:litertlm-android:0.17.1")
    //noinspection UseTomlInstead
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.11.0")
    //noinspection UseTomlInstead
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.11.0")
    //noinspection UseTomlInstead
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.11.0")
    //noinspection UseTomlInstead
    implementation("org.eclipse.paho:org.eclipse.paho.client.mqttv3:1.2.5")
    //noinspection UseTomlInstead
    implementation("org.eclipse.paho:org.eclipse.paho.android.service:1.1.1")
    //noinspection UseTomlInstead
    implementation("com.google.mlkit:translate:17.0.3")

}