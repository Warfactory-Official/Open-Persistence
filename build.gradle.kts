import com.gtnewhorizons.retrofuturagradle.minecraft.RunMinecraftTask
import com.gtnewhorizons.retrofuturagradle.util.Distribution

plugins {
    id("dev.prism")
}

group = "com.norwood"
// CI overrides this via -PmodVersion (snapshots and point releases); defaults to the dev version.
version = (findProperty("modVersion") as String?) ?: "1.0.0"

// A second dev client ("runClient2"), logged in as "Player2" with its own game directory, lets two
// clients connect to the dev server at once (they get different offline UUIDs): log out on one and
// kill the resulting persistent body with the other. ModDevGradle targets (NeoForge + 1.20.1 Forge)
// go through rawProject because Prism's own runs {} DSL reflectively calls gameDirectory.set(File)
// on MDG's Directory-typed property, which throws — so we register the run on MDG's own container,
// where gameDirectory accepts a proper Directory. NeoForge exposes this as the `neoForge` extension;
// 1.20.1 Forge (MDG's legacy-forge plugin) exposes the same RunModel container as `legacyForge`.
fun org.gradle.api.Project.registerSecondClientMdg(extensionName: String) {
    val ext = extensions.findByName(extensionName) ?: return
    ext.withGroovyBuilder {
        "runs" {
            "register"("client2") {
                "client"()
                setProperty("gameDirectory", layout.projectDirectory.dir("run/client2"))
                getProperty("programArguments").withGroovyBuilder { "addAll"("--username", "Player2") }
            }
        }
    }
    // MDG (re)generates each run's `@…RunVmArgs.txt` argfile from its `prepare<Run>Run` task and, for
    // its own runs, registers that task as an IntelliJ/Eclipse *sync* trigger so the file exists before
    // the IDE ever launches the run config. A run added here (after MDG's own setup) misses that, so an
    // IDE-launched client2 finds no argfile and dies with "Could not find or load main class @…txt".
    // Re-use MDG's own IdeIntegration to register client2's prepare task as a sync trigger too. (Running
    // via the Gradle `runClient2` task already works without this — it depends on the prepare task.)
    try {
        val ide = net.neoforged.moddevgradle.internal.IdeIntegration.of(
            this, net.neoforged.moddevgradle.internal.Branding.MDG)
        ide.runTaskOnProjectSync(tasks.named("prepareClient2Run"))
    } catch (e: Throwable) {
        logger.warn("Open Persistence: could not register client2 as an IDE sync task ({}); " +
            "run `gradlew {}:runClient2` from the terminal, or run `prepareClient2Run` before the IDE launch.",
            e.message, path)
    }
}

// Fabric equivalent, configured on Loom's own `runs` container (Prism's runs {} DSL reflectively
// mis-invokes Loom's getRuns and throws). `client()` makes it inherit the standard client run.
fun org.gradle.api.Project.registerSecondClientLoom() {
    val loom = extensions.findByName("loom") ?: return
    loom.withGroovyBuilder {
        "runs" {
            "register"("client2") {
                "client"()
                "configName"("Minecraft Client 2")
                "runDir"("run/client2")
                "programArgs"("--username", "Player2")
            }
        }
    }
}

// 1.12.2 (RetroFuturaGradle) equivalent. RFG exposes no run container and registers `runClient` as a
// RunMinecraftTask that recomputes its launch command from its own properties at execution time, so we
// register a second one the same way RFG does and copy the launch config that RFG + Prism wired onto
// `runClient` — deferring every read through providers so it is immune to configurator ordering — then
// override only the username and game directory. systemProperties / maxHeapSize are plain (non-lazy)
// JavaExec state, so those are copied in doFirst once `runClient` is fully configured.
fun org.gradle.api.Project.registerSecondClientRfg() {
    val src = tasks.named("runClient", RunMinecraftTask::class.java)
    tasks.register("runClient2", RunMinecraftTask::class.java, Distribution.CLIENT, gradle).configure {
        group = "retrofuturagradle"
        description = "Runs a second 1.12.2 client (Player2) alongside runClient for testing bodies"
        dependsOn(src.map { it.dependsOn })
        mainClass.set(src.flatMap { it.mainClass })
        classpath = files(src.map { it.classpath })
        tweakClasses.set(src.flatMap { it.tweakClasses })
        extraArgs.set(src.flatMap { it.extraArgs })
        extraJvmArgs.set(src.flatMap { it.extraJvmArgs })
        cmdlineArgs.set(src.flatMap { it.cmdlineArgs })
        cmdlineJvmArgs.set(src.flatMap { it.cmdlineJvmArgs })
        mcExtExtraRunJvmArguments.set(src.flatMap { it.mcExtExtraRunJvmArguments })
        lwjglVersion.set(src.flatMap { it.lwjglVersion })
        assetsDirectory.set(src.flatMap { it.assetsDirectory })
        mcVersion.set(src.flatMap { it.mcVersion })
        userUUID.set(src.flatMap { it.userUUID })
        accessToken.set(src.flatMap { it.accessToken })
        // Overrides that make this a distinct second client.
        username.set("Player2")
        setWorkingDir(layout.projectDirectory.dir("run/client2").asFile)
        doFirst {
            val s = src.get()
            s.systemProperties.forEach { (k, v) -> systemProperty(k, v) }
            s.maxHeapSize?.let { setMaxHeapSize(it) }
        }
    }
}

// Make the dev dedicated server start in offline mode so the unauthenticated dev clients (runClient /
// runClient2 — "Player" and "Player2") can connect without a Mojang session. A default online-mode=true
// server rejects them with "Failed to log in: Invalid session". Also accepts the EULA so the server can
// start at all. Run directories are git-ignored and (re)generated, so we write the files in a doFirst
// rather than committing them; existing keys are preserved and only online-mode is forced. `runServer`
// (Loom/MDG/RFG) is a JavaExec subtype on every platform, so its workingDir is the server's game dir.
fun org.gradle.api.Project.configureOfflineServer() {
    tasks.matching { it.name == "runServer" }.configureEach {
        val serverTask = this as org.gradle.api.tasks.JavaExec
        doFirst {
            // Resolve the server's actual game directory. Prism standardises Loom/MDG server runs to
            // <projectDir>/runs/<version>/<loader>/server (the JavaExec working dir stays at the project
            // root; the game dir is passed via --gameDir), whereas RetroFuturaGradle (1.12.2) runs with
            // the game dir as its working dir. Writing to the wrong one is silently ignored by the server.
            val workingDir = serverTask.workingDir
            val gameDir = if (workingDir.name == "run") {
                workingDir
            } else {
                java.io.File(workingDir, "runs/${project.parent?.name}/${project.name}/server")
            }
            gameDir.mkdirs()
            java.io.File(gameDir, "eula.txt").writeText("eula=true\n")
            val props = java.io.File(gameDir, "server.properties")
            val settings = linkedMapOf<String, String>()
            if (props.exists()) {
                props.readLines().forEach { line ->
                    val trimmed = line.trim()
                    val eq = trimmed.indexOf('=')
                    if (eq > 0 && !trimmed.startsWith("#")) {
                        settings[trimmed.substring(0, eq)] = trimmed.substring(eq + 1)
                    }
                }
            }
            settings["online-mode"] = "false"
            props.writeText(settings.entries.joinToString("\n") { "${it.key}=${it.value}" } + "\n")
            serverTask.logger.lifecycle("Open Persistence: dev server forced to offline mode (online-mode=false) in {}", gameDir)
        }
    }
}

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
    // henkelmax's maven hosts corelib (the shared Death model used by Corpse + GraveStone).
    // Corpse itself comes from Modrinth; GraveStone is CurseForge-only, so it comes from CurseMaven.
    maven("henkelmax", "https://maven.maxhenkel.de/repository/public")
    maven("CurseMaven", "https://www.cursemaven.com")

    version("1.21.1") {
        fabric {
            loaderVersion = "0.18.6"
            fabricApi("0.116.9+1.21.1")
            // A second dev client (`runClient2`, logged in as "Player2") for testing persistent bodies.
            // No grave mod ships for Fabric, so this exercises the body mechanic itself (items scatter).
            rawProject { registerSecondClientLoom(); configureOfflineServer() }
        }
        neoforge {
            loaderVersion = "21.1.222"
            dependencies {
                // Curios is an optional soft dependency (compile-only), enabling extra-slot support.
                modCompileOnly("maven.modrinth:curios:9.5.1+1.21.1")
                // Grave/corpse compat (optional soft deps, compile-only). Corpse (Modrinth) and
                // GraveStone (CurseForge) each shade their own copy of henkelmax's corelib Death
                // model, so no separate corelib dependency is needed.
                modCompileOnly("maven.modrinth:corpse:neoforge-1.21.1-1.1.13")
                modCompileOnly("curse.maven:gravestone-mod-238551:8056307")

                // Dev-only: load a grave mod into the dev runs so the compat can actually be tested
                // (not bundled into the shipped jar). Swap to gravestone to test GraveStone instead.
                modRuntimeOnly("maven.modrinth:corpse:neoforge-1.21.1-1.1.13")
                // modRuntimeOnly("curse.maven:gravestone-mod-238551:8056307")
            }
            // A second dev client (`runClient2`, logged in as "Player2") for testing persistent bodies.
            rawProject { registerSecondClientMdg("neoForge"); configureOfflineServer() }
        }
    }

    version("1.20.1") {
        fabric {
            loaderVersion = "0.18.6"
            fabricApi("0.92.7+1.20.1")
            // A second dev client (`runClient2`, logged in as "Player2") for testing persistent bodies.
            // No grave mod ships for Fabric, so this exercises the body mechanic itself (items scatter).
            rawProject { registerSecondClientLoom(); configureOfflineServer() }
        }
        forge {
            loaderVersion = "47.4.18"
            dependencies {
                // Curios is an optional soft dependency (compile-only), enabling extra-slot support.
                modCompileOnly("maven.modrinth:curios:5.14.1+1.20.1")
                // Grave/corpse compat (optional soft deps, compile-only). Corpse (Modrinth) and
                // GraveStone (CurseForge) each shade their own copy of corelib's Death model.
                modCompileOnly("maven.modrinth:corpse:forge-1.20.1-1.0.23")
                modCompileOnly("curse.maven:gravestone-mod-238551:7099725")

                // Dev-only: load a grave mod into the dev runs so the compat can actually be tested
                // (not bundled into the shipped jar). Swap to gravestone to test GraveStone instead.
                modRuntimeOnly("maven.modrinth:corpse:forge-1.20.1-1.0.23")
                // modRuntimeOnly("curse.maven:gravestone-mod-238551:7099725")
            }
            // A second dev client (`runClient2`, logged in as "Player2") for testing persistent bodies.
            rawProject { registerSecondClientMdg("legacyForge"); configureOfflineServer() }
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

            // A second dev client (`runClient2`, logged in as "Player2") for testing persistent bodies.
            // The 1.12.2 grave compat is seam-only (items scatter), so this exercises the body mechanic.
            // RetroFuturaGradle has no run container (and Prism's runs {} DSL no-ops here), so clone the
            // fully-configured `runClient` task — RFG recomputes the launch command from the task's own
            // properties at execution, so copying them (plus Prism's FML/GradleStart additions) and
            // overriding only the username + working directory yields an identical second client.
            rawProject { registerSecondClientRfg(); configureOfflineServer() }

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
            // A second dev client (`runClient2`, logged in as "Player2") for testing persistent bodies.
            // No grave mod ships for Fabric, so this exercises the body mechanic itself (items scatter).
            rawProject { registerSecondClientLoom(); configureOfflineServer() }
        }
        neoforge {
            loaderVersion = "26.1.1.0-beta"
            dependencies {
                // Curios is an optional soft dependency (compile-only), enabling extra-slot support.
                modCompileOnly("maven.modrinth:curios:15.0.0-beta.1+26.1")
                // Grave/corpse compat (optional soft deps, compile-only). Corpse (Modrinth) and
                // GraveStone (CurseForge) each shade their own copy of corelib's Death model.
                modCompileOnly("maven.modrinth:corpse:neoforge-1.1.16+26.1.2")
                modCompileOnly("curse.maven:gravestone-mod-238551:8056350")

                // Dev-only: load a grave mod into the dev runs so the compat can actually be tested
                // (not bundled into the shipped jar). Swap to gravestone to test GraveStone instead.
                modRuntimeOnly("maven.modrinth:corpse:neoforge-1.1.16+26.1.2")
                // modRuntimeOnly("curse.maven:gravestone-mod-238551:8056350")
            }
            // A second dev client (`runClient2`, logged in as "Player2") for testing persistent bodies.
            rawProject { registerSecondClientMdg("neoForge"); configureOfflineServer() }
        }
    }

}
