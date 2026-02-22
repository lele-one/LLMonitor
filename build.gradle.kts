// Top-level build file
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.jetbrains.kotlin.android) apply false
    alias(libs.plugins.jetbrains.kotlin.compose) apply false
    // 关键：声明 KSP 插件
    alias(libs.plugins.google.devtools.ksp) apply false
}