plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.jobspaul.ytauto"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.jobspaul.ytauto"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"
    }

    buildFeatures {
        buildConfig = true
    }

    flavorDimensions += "mode"
    productFlavors {
        // แอปจริง: ประเภทนำทาง (ได้ Surface สำหรับวาดวิดีโอ)
        create("nav") {
            dimension = "mode"
            manifestPlaceholders["carCategory"] = "androidx.car.app.category.NAVIGATION"
            manifestPlaceholders["appLabel"] = "YT Auto"
            buildConfigField("boolean", "NAV", "true")
        }
        // APK ทดสอบ: ประเภท POI แค่เช็กว่า Android Auto ยอมแสดงแอปที่ติดตั้งเองไหม
        create("poitest") {
            dimension = "mode"
            applicationIdSuffix = ".test"
            manifestPlaceholders["carCategory"] = "androidx.car.app.category.POI"
            manifestPlaceholders["appLabel"] = "YT Test"
            buildConfigField("boolean", "NAV", "false")
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            // ใช้ debug key เซ็น release ด้วย เพื่อให้ติดตั้งเองได้ทันที (ใช้ส่วนตัวเท่านั้น)
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    implementation("androidx.car.app:app:1.4.0")
    implementation("androidx.car.app:app-projected:1.4.0")
    implementation("androidx.core:core-ktx:1.13.1")
}
