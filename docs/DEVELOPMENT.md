# Development

Season2 Core targets Minecraft 1.20.1, Forge 47.4.x and Java 17.

## Build flow

1. GitHub Actions checks out the repository.
2. The committed Java source is audited for accidental legacy SRG identifiers.
3. ForgeGradle compiles the Mojang-mapped source directly and reobfuscates the output JAR for production.
4. The resulting JAR is uploaded as the `season2-core-build` Actions artifact.

The old per-build SRG-to-Mojang rewrite has been removed from normal CI and release workflows. `scripts/migrate_srg_to_mojang.py` remains only as historical/maintenance tooling; `scripts/check_legacy_srg.py` stays active as a guardrail.

## Active branches

Keep long-lived branches to a minimum:

- `main` — released/stable source baseline
- short-lived feature or maintenance branches — opened only for review/testing, then deleted after merge

Temporary build branches should be deleted after their result has been preserved in a release, merged commit or source archive.

## Versioning

The physical JAR contains three Forge mod IDs (`dragoncurrency`, `spectralmail`, `townlife`), but development builds inject the single `mod_version` value from `gradle.properties` into all three `mods.toml` entries. This removes stale independent metadata without renaming any mod ID or registry ID.

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

## Source mappings

Production source is now stored in readable Mojang mappings. New code should use normal Mojang names directly; do not add SRG-style identifiers such as `m_12345_` or `f_12345_` to committed Java source.

## Release policy

Compiled JARs, checksums and packaged release bundles belong in GitHub Releases or Actions artifacts. They should not be committed under a `release/` directory in the source repository.
