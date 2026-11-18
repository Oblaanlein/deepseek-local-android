pluginManagement {
    repositories {
        google()
        // 这台机器上 repo.maven.apache.org 会超时，repo1.maven.org 正常，所以显式指定
        maven { url = uri("https://repo1.maven.org/maven2") }
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        maven { url = uri("https://repo1.maven.org/maven2") }
    }
}

rootProject.name = "dsapp"
include(":app")
