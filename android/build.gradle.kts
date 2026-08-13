plugins {
    id("com.android.application")             version "9.3.1"        apply false
    id("com.android.library")                 version "9.3.1"        apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.3.21"         apply false
    id("com.google.devtools.ksp")             version "2.3.11"   apply false
    id("com.google.dagger.hilt.android")      version "2.60.1"        apply false
    id("com.google.gms.google-services")      version "4.4.4"         apply false
    id("com.google.firebase.crashlytics")     version "3.0.7"         apply false
    id("com.chaquo.python")                   version "17.0.0"        apply false
}
 
tasks.register<Delete>("clean") {
    delete(rootProject.layout.buildDirectory)
}