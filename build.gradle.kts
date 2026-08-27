repositories {
    
}

dependencies {
}

fun resolveServerJar() = configurations.getByName("compileClasspath")
    .resolvedConfiguration
    .resolvedArtifacts
    .first { it.moduleVersion.id.module.group == "com.hypixel.hytale" }
    .file

tasks.register<Jar>("testJar") {
    dependsOn("testClasses")
    archiveClassifier.set("tests")
    from(sourceSets["test"].output)
}

tasks.register<Exec>("generateAssetSchemas") {
    val appData = System.getenv("APPDATA") ?: throw GradleException("APPDATA environment variable is not set")
    val assetsZip = file("$appData/Hytale/install/release/package/game/latest/Assets.zip")
    val schemaAssetsDir = layout.projectDirectory.dir("schemas/assets").asFile
    val devServerDir = layout.projectDirectory.dir("devserver").asFile
    val modsSourceDir = layout.projectDirectory.dir("src/main").asFile

    inputs.file(assetsZip)
    inputs.dir(modsSourceDir)
    outputs.dir(schemaAssetsDir)

    workingDir = devServerDir
    executable = "java"
    argumentProviders.add(CommandLineArgumentProvider {
        val serverJar = resolveServerJar()

        listOf(
            "-jar",
            serverJar.absolutePath,
            "--assets",
            assetsZip.absolutePath,
            "--mods",
            modsSourceDir.absolutePath,
            "--generate-asset-schema",
            schemaAssetsDir.absolutePath,
        )
    })

    doFirst {
        require(assetsZip.exists()) { "Hytale assets zip not found: ${assetsZip.absolutePath}" }
        require(devServerDir.exists()) { "Devserver directory not found: ${devServerDir.absolutePath}" }
        require(modsSourceDir.exists()) { "Mods source directory not found: ${modsSourceDir.absolutePath}" }

        schemaAssetsDir.mkdirs()
    }
}

tasks.register<Sync>("prepareTestMods") {
    dependsOn("jar", "testJar")

    from(layout.buildDirectory.dir("libs")) {
        include("dev.drav.glyphworks.jar")
        include("dev.drav.glyphworks-tests.jar")
    }
    into(layout.buildDirectory.dir("test-mods"))
}

tasks.register<Exec>("runTestServer") {
    dependsOn("prepareTestMods")

    val appData = System.getenv("APPDATA") ?: throw GradleException("APPDATA environment variable is not set")
    val assetsZip = file("$appData/Hytale/install/release/package/game/latest/Assets.zip")
    val devServerDir = layout.projectDirectory.dir("devserver").asFile
    val modsDir = layout.buildDirectory.dir("test-mods").get().asFile

    inputs.file(assetsZip)
    inputs.dir(modsDir)

    workingDir = devServerDir
    executable = "java"
    argumentProviders.add(CommandLineArgumentProvider {
        val serverJar = resolveServerJar()

        // Headless test run: pass -Pglyphworks.test.all (or .module/.suite/.name)
        // and the test plugin runs the suites on startup, then exits 0/1.
        val testProps = listOf(
            "glyphworks.test.all",
            "glyphworks.test.module",
            "glyphworks.test.suite",
            "glyphworks.test.name",
        ).mapNotNull { key ->
            (project.findProperty(key) as String?)?.let { value ->
                if (value.isEmpty()) "-D$key=true" else "-D$key=$value"
            }
        }

        testProps + listOf(
            "-jar",
            serverJar.absolutePath,
            "--allow-op",
            "--disable-sentry",
            "--assets",
            assetsZip.absolutePath,
            "--mods",
            modsDir.absolutePath,
        )
    })

    doFirst {
        require(assetsZip.exists()) { "Hytale assets zip not found: ${assetsZip.absolutePath}" }
        require(devServerDir.exists()) { "Devserver directory not found: ${devServerDir.absolutePath}" }
        require(modsDir.exists()) { "Test mods directory not found: ${modsDir.absolutePath}" }
    }
}