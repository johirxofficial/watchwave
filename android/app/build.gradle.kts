plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

val configuredApplicationId = providers.gradleProperty("watchwave.applicationId").orNull
    ?.takeIf { it.isNotBlank() }
    ?: "com.watchwave.app"
val defaultBaseDomain = providers.gradleProperty("watchwave.baseDomain").orNull.orEmpty()

/**
 * The browser client is built separately under `web/` and copied into the APK so the
 * host serves the real application from the same origin as the API.
 */
val webDistDir = rootProject.file("../web/dist")
val syncWebAssets = tasks.register<Sync>("syncWebAssets") {
    from(webDistDir)
    into(layout.projectDirectory.dir("src/main/assets/web"))
    doFirst {
        if (!webDistDir.isDirectory) {
            logger.lifecycle(
                "web/dist is missing: the APK will serve the built-in fallback page. " +
                    "Run `npm install && npm run build` in web/ to bundle the client.",
            )
        }
    }
}

android {
    namespace = "com.watchwave.app"
    compileSdk = 34

    defaultConfig {
        applicationId = configuredApplicationId
        minSdk = 26
        targetSdk = 34
        versionCode = 4
        versionName = "1.1.2"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        buildConfigField("String", "DEFAULT_BASE_DOMAIN", "\"$defaultBaseDomain\"")
    }

    buildTypes {
        debug {
            applicationIdSuffix = ""
            versionNameSuffix = "-debug"
            isMinifyEnabled = false
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
        freeCompilerArgs += listOf("-opt-in=kotlin.RequiresOptIn")
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        jniLibs {
            // The bundled cloudflared is a static executable, not a linkable shared
            // object. Keep it byte-for-byte and extract it so it can be exec'd.
            useLegacyPackaging = true
            keepDebugSymbols += "**/libcloudflared.so"
        }
        resources {
            excludes += setOf(
                "/META-INF/{AL2.0,LGPL2.1}",
                "/META-INF/INDEX.LIST",
                "/META-INF/DEPENDENCIES",
                "/META-INF/LICENSE*",
                "/META-INF/NOTICE*",
                "/META-INF/*.kotlin_module",
            )
        }
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
    }
}

// Ensure the bundled client is refreshed before assets are merged, including through
// the IDE's "Run" action which invokes the assemble tasks directly.
tasks.named("preBuild") { dependsOn(syncWebAssets) }

dependencies {
    implementation(project(":shared"))
    implementation(project(":data"))
    implementation(project(":streaming"))
    implementation(project(":tunnel"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.documentfile)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    debugImplementation(libs.androidx.compose.ui.tooling)

    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.androidx.media3.session)
    implementation(libs.androidx.media3.ui)
    implementation(libs.androidx.media3.datasource)

    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.okhttp)
    implementation(libs.ktor.client.websockets)
    implementation(libs.ktor.client.content.negotiation)
    implementation(libs.ktor.serialization.kotlinx.json)
    implementation(libs.slf4j.android)

    testImplementation(libs.junit4)
    testImplementation(libs.kotlinx.coroutines.test)

    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}
