import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

// 从 local.properties 读取 Gemini API Key（该文件已 gitignore，不进版本库）。
val geminiApiKey: String = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}.getProperty("GEMINI_API_KEY", "")

android {
    namespace = "io.github.rainpalette.raintale"
    compileSdk {
        version = release(36) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        applicationId = "io.github.rainpalette.raintale"
        minSdk = 29
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // Gemini API Key 的 BuildConfig 字段，代码只读 BuildConfig.GEMINI_API_KEY。
        // 默认给空串兜底：release 构建走这里 → 绝不携带开发者的内置 key（BYOK 用户自填）。
        // 真 key 只在下方 debug buildType 注入（见 buildTypes.debug），从构建结构上杜绝 key 泄进 release APK。
        buildConfigField("String", "GEMINI_API_KEY", "\"\"")
    }

    buildTypes {
        debug {
            // 仅 debug：注入 local.properties 的 key，方便开发期自测（该文件已 gitignore）。
            buildConfigField("String", "GEMINI_API_KEY", "\"$geminiApiKey\"")
        }
        release {
            // release 不覆盖 GEMINI_API_KEY → 继承 defaultConfig 的空串，内置 key 不会打包进 APK。
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
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.camera.core)
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.view)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    // ML Kit 人像分割（端上、内置模型、免下载）——预览期把人抠出来叠在角色之上。
    implementation(libs.mlkit.segmentation.selfie)
    // 设置持久化（冷启动不丢）。
    implementation(libs.androidx.datastore.preferences)
    // 图库：Room（照片元数据/影集批注）+ Coil（网格缩略图加载）。
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    implementation(libs.coil.compose)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}