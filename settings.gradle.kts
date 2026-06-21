pluginManagement {
    repositories {
        maven { url = uri("https://maven.leclowndu93150.dev/releases") }
        gradlePluginPortal()
        mavenCentral()
        maven { url = uri("https://maven.fabricmc.net/") }
        maven { url = uri("https://maven.neoforged.net/releases") }
        maven { url = uri("https://repo.spongepowered.org/repository/maven-public/") }
        maven { url = uri("https://nexus.gtnewhorizons.com/repository/public/") }
    }
}

plugins {
    // 1.0.0+ is required for Gradle 9: 0.9.0 hard-references JvmVendorSpec.IBM_SEMERU,
    // which Gradle 9 removed, crashing toolchain resolution (e.g. the 1.12.2 Java 8 toolchain).
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
    id("dev.prism.settings") version "+"
}

rootProject.name = "persistence"

prism {
    version("1.21.1") {
        common()
        fabric()
        neoforge()
    }
    version("1.20.1") {
        common()
        fabric()
        forge()
    }
    version("1.12.2") {
        legacyForge()
    }
    version("26.1") {
        common()
        fabric()
        neoforge()
    }
}
