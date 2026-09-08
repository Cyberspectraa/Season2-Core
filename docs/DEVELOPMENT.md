# Development

Season2 Core targets Minecraft 1.20.1, Forge 47.4.x and Java 17.

## Build flow

1. GitHub Actions checks out the repository.
2. Legacy SRG identifiers in Java code are converted to Mojang mappings.
3. The mapped source is audited.
4. ForgeGradle compiles and reobfuscates the mod.
5. The resulting JAR is uploaded as the `season2-core-build` Actions artifact.

The SRG conversion is transitional. After the 0.6 gameplay-polish build is confirmed in game, the preferred cleanup is a separate maintenance branch that commits the readable Mojang-mapped source directly and removes the normal CI rewrite step.

## Active branches

Keep long-lived branches to a minimum:

- `main` — released/stable source baseline
- `season2-polish-v060` — current 0.6.0 core-polish development branch

Temporary build branches should be deleted after their result has been preserved in a release, merged commit or source archive.

## Versioning

The physical JAR contains three Forge mod IDs (`dragoncurrency`, `spectralmail`, `townlife`), but development builds now inject the single `mod_version` value from `gradle.properties` into all three `mods.toml` entries. This removes stale independent metadata without renaming any mod ID or registry ID.

## Compatibility rules

- preserve all existing `dragoncurrency:*`, `spectralmail:*` and `townlife:*` registry IDs
- preserve existing SavedData and player-persistent NBT keys
- keep legacy compatibility-only Dragon Currency registry entries registered even when they are hidden from the creative tab
- preserve the tested real-bed `SleepService`
- do not reintroduce custom staircase scanning or custom door handling
- let EasyNPC/Minecraft own physical navigation
- keep the banker and bound postal courier outside generic Town Life scheduling

## Creative tab

The shared creative tab is registered under the existing Dragon Currency namespace but can display player-facing items from all three modules. This is presentation-only: item/block ownership and IDs stay unchanged.

## Release policy

Compiled JARs, checksums and packaged release bundles belong in GitHub Releases or Actions artifacts. They should not be committed under a `release/` directory in the source repository.
