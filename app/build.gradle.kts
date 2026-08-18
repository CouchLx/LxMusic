import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

// 本地敏感配置（keystore.properties 不进仓库；缺失时使用占位值）
val localPropsFile = rootProject.file("keystore.properties")
val localProps = Properties()
if (localPropsFile.exists()) {
    localProps.load(localPropsFile.inputStream())
}
fun secretProp(name: String, default: String): String {
    return localProps.getProperty(name)?.takeIf { it.isNotBlank() } ?: default
}
fun secretOrNull(name: String): String? {
    return localProps.getProperty(name)?.takeIf { it.isNotBlank() }
}

android {
    namespace = "com.example.lxmusic"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.example.lxmusic"
        minSdk = 24
        targetSdk = 37
        versionCode = 3
        versionName = "3.7.56"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // 服务器地址与管理员密码由本地 keystore.properties 注入，开源仓库中为占位值
        buildConfigField("String", "LX_SERVER_URL", "\"${secretProp("LX_SERVER_URL", "http://your-server:3000/")}\"")
        buildConfigField("String", "LX_ADMIN_PASSWORD", "\"${secretProp("LX_ADMIN_PASSWORD", "")}\"")
        buildConfigField("String", "LX_UPDATE_VERSION_URL", "\"${secretProp("LX_UPDATE_VERSION_URL", "")}\"")

        // USB 独占播放 native 库（libusb + UAC1/UAC2）
        ndk {
            abiFilters += listOf("arm64-v8a", "armeabi-v7a", "x86_64")
        }
        externalNativeBuild {
            cmake {
                cppFlags += listOf("-fexceptions", "-frtti")
                arguments += listOf("-DANDROID_STL=c++_static")
            }
        }
    }

    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
    }

    signingConfigs {
        create("release") {
            // 相对于项目根目录解析 keystore 路径
            storeFile = rootProject.file(secretProp("STORE_FILE", "release.jks"))
            storePassword = secretOrNull("STORE_PASSWORD")
            keyAlias = secretOrNull("KEY_ALIAS")
            keyPassword = secretOrNull("KEY_PASSWORD")
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            if (secretOrNull("STORE_PASSWORD") != null) {
                signingConfig = signingConfigs.getByName("release")
            }
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
    lint {
        disable += "NullSafeMutableLiveData"
    }
    ksp {
        arg("room.schemaLocation", "$projectDir/schemas")
    }
    packaging {
        resources {
            excludes += arrayOf(
                "DebugProbesKt.bin",
                "kotlin-tooling-metadata.json",
                "META-INF/*.version",
                "META-INF/**/LICENSE.txt"
            )
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)

    // AndroidX Compose BOM
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.animation)
    implementation(libs.androidx.compose.material.icons)
    debugImplementation(libs.androidx.compose.ui.tooling)

    // Kyant Liquid Glass
    implementation(libs.kyant.shapes)
    implementation(libs.backdrop)

    // ComposeMeshGradient（动态背景2：Apple 风格 Mesh 渐变）
    implementation(libs.compose.mesh.gradient)

    // material-kolor（动态取色：种子色 + 取色风格）
    implementation(libs.material.kolor)

    // Haze (frosted glass blur)
    implementation(libs.haze)
    implementation(libs.haze.materials)

    // Android-specific
    implementation("io.coil-kt:coil-compose:2.6.0")
    implementation("androidx.core:core-splashscreen:1.0.1")

    // 专辑封面色提取
    implementation("androidx.palette:palette-ktx:1.0.0")

    // Media3 播放器 + 媒体会话（1.3.1 → 1.5.1：AudioFormat 采样率/位深 API 于 1.5 引入；
    // 注意升级后需回归验证 USB 独占播放）
    implementation("androidx.media3:media3-exoplayer:1.5.1")
    implementation("androidx.media3:media3-session:1.5.1")

    // 新歌词页面（accompanist-lyrics 的 KRC 解码器需要 serialization-json）
    implementation(libs.kotlinx.serialization.json)

    // Room 数据库
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    // Retrofit + OkHttp + Gson（网络请求）
    implementation("com.squareup.retrofit2:retrofit:2.9.0")
    implementation("com.squareup.retrofit2:converter-gson:2.9.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")

    // 拖拽排序库
    implementation("sh.calvin.reorderable:reorderable:3.1.0")

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
