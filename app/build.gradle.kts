import java.util.Properties
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

// Свой ключ подписи: keystore.properties в корне проекта (в .gitignore, в репозиторий не попадает).
// Ключи: storeFile (путь от корня проекта или абсолютный), storePassword, keyAlias, keyPassword.
val keystorePropertiesFile: File = rootProject.file("keystore.properties")
val keystoreProperties: Properties? = keystorePropertiesFile.takeIf { it.isFile }?.let { file ->
    Properties().apply { file.inputStream().use { load(it) } }
}

fun Properties.required(key: String): String =
    getProperty(key)?.trim()?.takeIf { it.isNotEmpty() }
        ?: throw GradleException("В keystore.properties нет значения «$key». Нужны: storeFile, storePassword, keyAlias, keyPassword.")

android {
    namespace = "dev.lupa"
    compileSdk = 36

    defaultConfig {
        applicationId = "dev.lupa"
        minSdk = 29
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"
    }

    signingConfigs {
        if (keystoreProperties != null) {
            create("release") {
                storeFile = rootProject.file(keystoreProperties.required("storeFile"))
                storePassword = keystoreProperties.required("storePassword")
                keyAlias = keystoreProperties.required("keyAlias")
                keyPassword = keystoreProperties.required("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            signingConfig = if (keystoreProperties != null) {
                signingConfigs.getByName("release")
            } else {
                signingConfigs.getByName("debug")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
            all { it.maxHeapSize = "2g" }
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

// Нет keystore.properties — release подписан debug-ключом. Предупреждаем, только когда собирается release.
if (keystoreProperties == null) {
    gradle.taskGraph.whenReady {
        if (allTasks.any { it.project == project && it.name in setOf("packageRelease", "signReleaseBundle") }) {
            logger.warn(
                "ВНИМАНИЕ: keystore.properties не найден — release-APK подписан debug-ключом. " +
                    "Как создать свой ключ, написано в README.",
            )
        }
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.camera.core)
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.view)
    implementation(libs.androidx.datastore.preferences)

    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(platform(libs.androidx.compose.bom))
    testImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
