plugins {
    id("com.android.application")
}

android {
    namespace = "com.onessh.app"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.onessh.app"
        minSdk = 26
        targetSdk = 34
        versionCode = 2
        versionName = "2.0.0"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }

    buildFeatures {
        buildConfig = true
    }

    // 离线环境里跑不了 lintVital（它要单独下一个 lint-gradle 包），关掉
    lint {
        checkReleaseBuilds = false
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
}
