# Development

Season2 Core targets Minecraft 1.20.1, Forge 47.4.x and Java 17.

## Build flow

1. GitHub Actions checks out the repository.
2. Legacy SRG identifiers in Java code are converted to Mojang mappings.
3. The mapped source is audited.
4. ForgeGradle compiles and reobfuscates the mod.
5. The resulting JAR is uploaded as the `season2-core-build` Actions artifact.

The SRG conversion is transitional. The preferred end state is readable Mojang-mapped source committed directly to the repository.

## Development branches

Keep long-lived branches to a minimum:

- `main` — stable source baseline
- `townlife-integration` — active 0.5.0 Town Life integration until merged

Temporary build branches should be deleted after their result has been preserved in a release, merged commit or source archive.

## Release policy

Compiled JARs, checksums and packaged release bundles belong in GitHub Releases or Actions artifacts. They should not be committed under a `release/` directory in the source repository.

## Town Life integration rules

- preserve the `townlife` mod ID and existing registry/SavedData IDs
- preserve the tested real-bed sleeping implementation
- do not reintroduce custom staircase scanning or custom door handling
- let EasyNPC/Minecraft own physical navigation
- keep the banker and bound postal courier outside generic Town Life scheduling
