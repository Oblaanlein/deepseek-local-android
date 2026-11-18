plugins {
    id("com.android.application") version "9.2.1" apply false
    // AGP 9 内置 Kotlin（KGP 2.2.10），Compose 编译器插件需单独声明且版本对齐
    id("org.jetbrains.kotlin.plugin.compose") version "2.2.10" apply false
}
