pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        // Artefactos de Robolectric desde el espejo oficial de Google de Maven Central
        // (Maven Central limita con HTTP 429 las descargas grandes de android-all).
        exclusiveContent {
            forRepository { maven("https://maven-central.storage-download.googleapis.com/maven2/") }
            filter { includeGroup("org.robolectric") }
        }
        google()
        mavenCentral()
    }
}
rootProject.name = "GeoDensidad"
include(":core", ":app")
