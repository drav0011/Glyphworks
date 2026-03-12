import dev.scaffoldit.hytale.wire.HytaleManifest

rootProject.name = "dev.drav.glyphworks"

plugins {
    id("dev.scaffoldit") version "0.2.+"
}
hytale {
    usePatchline("pre-release")
    useVersion("latest")

    manifest {
        Group = "drav.dev"
        Name = "Glyphworks"
        Version = "0.1.0"
        Main = "dev.drav.glyphworks.GlyphworksPlugin"
        IncludesAssetPack = true
        DisabledByDefault = false
        ServerVersion = "*"
        
        Authors = listOf(
            HytaleManifest.Author("DrAv0011", "", "https://drav.dev")
        )
    }
}