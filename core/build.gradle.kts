import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.jvm)
}

// Koordinaten, über die die App dieses Modul (Composite Build) einbindet.
group = "io.github.lgrabowsky.igl"
version = "1.0"

java {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
}

kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.JVM_11
    }
}

dependencies {
    testImplementation(kotlin("test-junit"))
    testImplementation(libs.junit)
}

tasks.test {
    useJUnit()
    // Für die synthetischen Testbilder (java.awt) wird kein Display benötigt.
    systemProperty("java.awt.headless", "true")
}
