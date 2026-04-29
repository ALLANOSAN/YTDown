plugins {
    id("com.android.application")             version "8.7.0"        apply false
    id("com.android.library")                 version "8.7.0"        apply false
    id("org.jetbrains.kotlin.android")        version "2.0.0"        apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.0.0"        apply false
    id("com.google.devtools.ksp")             version "2.0.0-1.0.21" apply false
    id("com.google.dagger.hilt.android")      version "2.51.1"       apply false
    id("com.google.gms.google-services")      version "4.4.2"        apply false
    id("com.google.firebase.crashlytics")     version "3.0.2"        apply false
    id("com.chaquo.python")                   version "17.0.0"       apply false
}

// Apenas repositórios globais — configurações de compilação ficam no módulo app
allprojects {
    repositories {
        google()
        mavenCentral()
        maven { url = uri("https://chaquo.com/maven") }
    }
}

tasks.register<Delete>("clean") {
    delete(rootProject.layout.buildDirectory)
}
