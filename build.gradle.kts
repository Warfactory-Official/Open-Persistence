plugins {
    id("dev.prism")
}

group = "com.norwood"
// CI overrides this via -PmodVersion (snapshots and point releases); defaults to the dev version.
version = (findProperty("modVersion") as String?) ?: "1.0.0"

prism {
    metadata {
        modId = "openpersistence"
        name = "Open Persistence"
        description = "Open source reimplementation of Persistent Players, with fixes and mod compatibility."
        license = "GPL-3.0"
        author("MrNorwood")
        author("dudumalah")
        author("fyrstikkeske")
    }

    // Curios (and other accessory mods) are pulled from the Modrinth maven.
    modrinthMaven()
    maven("BlameJared", "https://maven.blamejared.com")

    version("1.21.1") {
        fabric {
            loaderVersion = "0.18.6"
            fabricApi("0.116.9+1.21.1")
        }
        neoforge {
            loaderVersion = "21.1.222"
            dependencies {
                // Curios is an optional soft dependency (compile-only), enabling extra-slot support.
                modCompileOnly("maven.modrinth:curios:9.5.1+1.21.1")
            }
        }
    }

    version("1.20.1") {
        fabric {
            loaderVersion = "0.18.6"
            fabricApi("0.92.7+1.20.1")
        }
        forge {
            loaderVersion = "47.4.18"
            dependencies {
                // Curios is an optional soft dependency (compile-only), enabling extra-slot support.
                modCompileOnly("maven.modrinth:curios:5.14.1+1.20.1")
            }
        }
    }

    version("1.12.2") {
        legacyForge {
            mcVersion = "1.12.2"
            forgeVersion = "14.23.5.2847"
            mappingChannel = "stable"
            mappingVersion = "39"
            accessTransformer("src/main/resources/META-INF/openpersistence_at.cfg")
            // This mod uses no Mixins; disable the auto-wired MixinBooter + MixinExtras
            // annotation processor (which otherwise fails to find ASM on the processor path).
            mixinBooter = false

            // Prism's accessTransformer() only registers the AT for the dev/deobf workspace; it
            // does NOT add the FMLAT manifest entry that Forge needs to apply the AT at runtime.
            // Wire it manually so the reobf (production) jar actually has its fields widened.
            rawProject {
                tasks.withType(org.gradle.api.tasks.bundling.Jar::class.java).configureEach {
                    manifest.attributes(mapOf("FMLAT" to "openpersistence_at.cfg"))
                }
            }

            dependencies {
                // ModularWarfare (Shining fork) is compile-only soft compat, same as the
                // original Open-Persistence build (compileOnly files('libs/...')).
                localJar("libs/modularwarfare-shining.jar")
            }
        }
    }

    version("26.1") {
        fabric {
            loaderVersion = "0.18.6"
            fabricApi("0.145.2+26.1.1")
        }
        neoforge {
            loaderVersion = "26.1.1.0-beta"
            dependencies {
                // Curios is an optional soft dependency (compile-only), enabling extra-slot support.
                modCompileOnly("maven.modrinth:curios:15.0.0-beta.1+26.1")
            }
        }
    }

}
