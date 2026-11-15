plugins {
    id("com.android.application")
}

android {
    namespace = "com.onessh.app"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.onessh.app"
        minSdk = 26
        // 2.1.0：34 → 35。Android 15（API 35）起 Play 要求 targetSdk ≥ 35；
        // compileSdk 仍 36，minSdk 26（Android 8.0+）不变。
        targetSdk = 35
        versionCode = 4
        versionName = "2.2.0"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    // Release 签名：只在配好 Secrets / 环境变量时启用，没配也不让构建失败。
    // CI Secrets：ONESSH_KEYSTORE_BASE64 / ONESSH_KEYSTORE_PASSWORD /
    //              ONESSH_KEY_ALIAS / ONESSH_KEY_PASSWORD
    // 本地：export 上述变量后，keystore 由 base64 解出写到 build/ 目录（不入库）。
    signingConfigs {
        create("release") {
            val base64 = System.getenv("ONESSH_KEYSTORE_BASE64")
            val storePassword = System.getenv("ONESSH_KEYSTORE_PASSWORD")
            val keyAlias = System.getenv("ONESSH_KEY_ALIAS")
            val keyPassword = System.getenv("ONESSH_KEY_PASSWORD")
            if (!base64.isNullOrBlank() && !storePassword.isNullOrBlank()
                && !keyAlias.isNullOrBlank() && !keyPassword.isNullOrBlank()
            ) {
                val keystoreFile = layout.buildDirectory.file("onessh-release.keystore").get().asFile
                keystoreFile.parentFile?.mkdirs()
                keystoreFile.writeBytes(java.util.Base64.getDecoder().decode(base64))
                storeFile = keystoreFile
                this.storePassword = storePassword
                this.keyAlias = keyAlias
                this.keyPassword = keyPassword
            }
        }
    }

    buildTypes {
        release {
            // 2.1.0：开 R8 + 资源收缩。JSch / org.json / 应用类已在
            // proguard-rules.pro 里保留；没配签名 Secrets 时仍按
            // unsigned 构建（signingConfig 为空时 AGP 会跳过签名）。
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            val hasSigningSecrets = !System.getenv("ONESSH_KEYSTORE_BASE64").isNullOrBlank()
            if (hasSigningSecrets) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }

    buildFeatures {
        buildConfig = true
    }

    // 2.1.0：Release 构建时跑 Lint 检查，但不阻断（离线环境跑不了
    // lintVital 时仍能出包；CI 上能看到 Lint 报告）。
    lint {
        checkReleaseBuilds = true
        abortOnError = false
    }

    packaging {
        resources.excludes += setOf("META-INF/*", "META-INF/versions/9/OSGI-INF/MANIFEST.MF")
    }
}

dependencies {
    // 纯 Java 的 SSH2 实现：密码 / 公钥 / keyboard-interactive、PTY shell、SFTP 全都有，
    // 没有传递依赖，APK 只多 500 KB 左右。
    // 直接引用 libs 里的 jar，构建时不需要联网。
    implementation(files("libs/jsch-2.28.8.jar"))

    // 2.1.0：本地单元测试（纯 Java，不需要真机 / 模拟器 / SSH 服务器）。
    // ./gradlew testDebugUnitTest
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.hamcrest:hamcrest:2.2")
}
