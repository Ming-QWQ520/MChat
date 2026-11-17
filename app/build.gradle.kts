plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

// 可选的正式签名配置：通过环境变量注入（CI Secrets → env）。
// 未配置时 release 构建回退使用 debug keystore 签名，保证 assembleRelease 产物
// 为已签名的 app-release.apk，可直接安装（适合 CI 分发构建）。
val releaseKeystoreFile = providers.environmentVariable("MCHAT_KEYSTORE_FILE").orNull
val releaseKeystorePassword = providers.environmentVariable("MCHAT_KEYSTORE_PASSWORD").orNull
val releaseKeyAlias = providers.environmentVariable("MCHAT_KEY_ALIAS").orNull
val releaseKeyPassword = providers.environmentVariable("MCHAT_KEY_PASSWORD").orNull
val hasReleaseKeystore = listOfNotNull(
    releaseKeystoreFile, releaseKeystorePassword, releaseKeyAlias, releaseKeyPassword,
).size == 4

android {
    namespace = "com.ming.mchat"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.ming.mchat"
        minSdk = 26
        targetSdk = 35
        // 版本号锁定：从 gradle.properties 读取（VERSION_NAME=0.1.0 / VERSION_CODE=1），CI 校验
        versionCode = providers.gradleProperty("VERSION_CODE").get().toInt()
        versionName = providers.gradleProperty("VERSION_NAME").get()
    }

    signingConfigs {
        if (hasReleaseKeystore) {
            create("release") {
                storeFile = file(releaseKeystoreFile!!)
                storePassword = releaseKeystorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            // 未提供正式 keystore 时用 debug keystore 兜底，产物为可直接安装的 app-release.apk
            signingConfig = if (hasReleaseKeystore) {
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
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            excludes += "/META-INF/INDEX.LIST"
            excludes += "/META-INF/io.netty.versions.properties"
        }
    }
}

dependencies {
    implementation(project(":core-common"))
    implementation(project(":core-data"))
    implementation(project(":core-llm"))
    implementation(project(":core-prompt"))
    implementation(project(":core-attachment"))
    implementation(project(":core-theme"))
    implementation(project(":feature-chat"))
    implementation(project(":feature-history"))
    implementation(project(":feature-settings"))
    implementation(project(":feature-title"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.activity.compose)

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons)
    debugImplementation(libs.compose.ui.tooling)

    implementation(libs.androidx.navigation.compose)

    // 全局背景图渲染（AppBackground）与附件图片
    implementation(libs.coil.compose)
    implementation(libs.hilt.navigation.compose)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)

    testImplementation(libs.junit)
}
