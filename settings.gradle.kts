pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()

        // Configuración para Mapbox en Kotlin DSL
        maven {
            url = uri("https://api.mapbox.com/downloads/v2/releases/maven")
            credentials {
                // El usuario siempre es "mapbox"
                username = "mapbox"
                // Reemplaza con tu SECRET TOKEN (el que empieza con sk)
                password = "sk.eyJ1IjoidW5wcm9ncmFtYWRvciIsImEiOiJjbWw4bnJwZmYwOTd0M2dxMWZucWdwa3JmIn0.Ehnzc2Cf2_nVkHvYiGGZDQ"
            }
        }
    }
}

rootProject.name = "gocar"
include(":app")
 
