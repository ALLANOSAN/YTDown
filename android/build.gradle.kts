plugins {
    id("com.android.application")             version "8.10.0"        apply false
    id("com.android.library")                 version "8.10.0"        apply false
    id("org.jetbrains.kotlin.android")        version "2.2.0"         apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.2.0"         apply false
    id("com.google.devtools.ksp")             version "2.2.0-2.0.2"   apply false
    id("com.google.dagger.hilt.android")      version "2.56.2"        apply false
    id("com.google.gms.google-services")      version "4.4.4"         apply false
    id("com.google.firebase.crashlytics")     version "3.0.7"         apply false
    id("com.chaquo.python")                   version "17.0.0"        apply false
}
 
tasks.register<Delete>("clean") {
    delete(rootProject.layout.buildDirectory)
}