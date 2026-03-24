import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.jetbrains.kotlin.android)
    alias(libs.plugins.jetbrains.kotlin.compose)
    alias(libs.plugins.google.devtools.ksp)
}

val localProperties = Properties().apply {
    val localPropertiesFile = rootProject.file("local.properties")
    if (localPropertiesFile.exists()) {
        localPropertiesFile.inputStream().use { load(it) }
    }
}

val releaseStoreFile = localProperties.getProperty("RELEASE_STORE_FILE")
val releaseStorePassword = localProperties.getProperty("RELEASE_STORE_PASSWORD")
val releaseKeyAlias = localProperties.getProperty("RELEASE_KEY_ALIAS")
val releaseKeyPassword = localProperties.getProperty("RELEASE_KEY_PASSWORD")
val hasLocalReleaseSigning = listOf(
    releaseStoreFile,
    releaseStorePassword,
    releaseKeyAlias,
    releaseKeyPassword
).all { !it.isNullOrBlank() }

android {
    namespace = "com.lele.llmonitor"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.lele.llmonitor"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
    }

    signingConfigs {
        create("release") {
            if (hasLocalReleaseSigning) {
                storeFile = file(releaseStoreFile!!)
                storePassword = releaseStorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
            } else {
                initWith(getByName("debug"))
            }
        }
    }

    buildTypes {
        release {
            signingConfig = signingConfigs.getByName("release")
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
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
        buildConfig = true
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }

    sourceSets.getByName("main").assets.srcDir(
        layout.buildDirectory.dir("generated/openSource/assets")
    )
}

val openSourceGeneratedDir = layout.buildDirectory.dir("generated/openSource")
val openSourceAssetsDir = openSourceGeneratedDir.map { it.dir("assets") }
val openSourceInventoryFile =
    openSourceGeneratedDir.map { it.file("intermediates/open_source_inventory.json") }
val thirdPartyNoticesFile = openSourceAssetsDir.map { it.file("THIRD_PARTY_NOTICES.txt") }

val generateReleaseOpenSourceInventory = tasks.register<GenerateOpenSourceInventoryTask>(
    "generateReleaseOpenSourceInventory"
) {
    group = "open source"
    description = "Generate release open source dependency inventory for bundled runtime artifacts."
    configurationName.set("releaseRuntimeClasspath")
    outputFile.set(openSourceInventoryFile)
}

val generateReleaseOpenSourceNotices = tasks.register<GenerateOpenSourceNoticesTask>(
    "generateReleaseOpenSourceNotices"
) {
    group = "open source"
    description = "Generate grouped open source notices and bundled license assets."
    dependsOn(generateReleaseOpenSourceInventory)
    inventoryFile.set(openSourceInventoryFile)
    outputDirectory.set(openSourceAssetsDir)
}

tasks.named("preBuild").configure {
    dependsOn(generateReleaseOpenSourceNotices)
}

afterEvaluate {
    val runtimeConfiguration = configurations.getByName("releaseRuntimeClasspath")
    generateReleaseOpenSourceInventory.configure {
        runtimeArtifacts.from(runtimeConfiguration)
        directDependencyCoordinates.set(
            runtimeConfiguration.dependencies
                .withType(org.gradle.api.artifacts.ExternalModuleDependency::class.java)
                .mapNotNull { dependency ->
                    dependency.group?.takeIf(String::isNotBlank)?.let { group ->
                        "$group:${dependency.name}"
                    }
                }
        )
        gradleCacheDirPath.set(
            gradle.gradleUserHomeDir.resolve("caches/modules-2/files-2.1").absolutePath
        )
    }
}

dependencies {
    // 基础组件
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)

    // 导航：只保留这一个稳定引用，解决 MainActivity 报错
    implementation("androidx.navigation:navigation-compose:2.8.5")

    // Compose（与 LLClass 同款显式版本链）
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation("com.google.android.material:material:1.12.0")
    implementation(libs.androidx.compose.material.icons.extended)
    implementation("io.coil-kt:coil-compose:2.7.0")
    implementation("com.google.code.gson:gson:2.11.0")

    // Room 数据库
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    implementation(libs.firebase.ai)
    implementation(libs.androidx.glance)
    ksp(libs.androidx.room.compiler)

    // YCharts 图表库
    implementation("co.yml:ycharts:2.1.0")

    implementation("androidx.glance:glance-appwidget:1.1.0")
    implementation("androidx.glance:glance-material3:1.1.0") // Material 3 支持

    // 测试依赖
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.ui.test.junit4)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)
}
