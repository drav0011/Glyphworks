// Dedicated configuration for dependencies we bundle INTO the plugin JAR.
// This avoids accidentally bundling the entire Hytale Server dependency.
val bundled: Configuration by configurations.creating {
    isTransitive = false
}

repositories {
    maven { url = uri("https://www.cursemaven.com") }
}

dependencies {
    implementation("org.ow2.asm:asm:9.7.1")
    bundled("org.ow2.asm:asm:9.7.1")

    // Hot swapping support - only needed if not using ScaffoldIt plugin
    // Since we're using ScaffoldIt plugin, this is optional but ensures devtools is available
    runtimeOnly("dev.scaffoldit:devtools:0.2.+")
}

tasks.withType<Jar> {
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    from({ bundled.map { if (it.isDirectory) it else zipTree(it) } })
}