# ForgeGradle migration status

## What this package does

This overlay adds a normal ForgeGradle 6 / Java 17 project foundation for the existing Season2 Core repository:

- Minecraft 1.20.1
- Forge 47.4.10
- Java 17 toolchain
- official mappings
- client and dedicated-server run configurations
- GitHub Actions build job
- an SRG-source audit task/script

It is designed to be copied over the existing `Cyberspectraa/Season2-Core` repository. It does **not** replace `src/main`, `presets`, `docs/history`, or release files.

## Important: the Java migration is not complete yet

The current 0.4.4 source still contains production/SRG identifiers such as `m_41487_`. ForgeGradle's normal development workspace uses official Mojang names (for that example, `Item.Properties#stacksTo`).

That means the build system is now correctly scaffolded, but a clean `gradle build` should not be treated as expected until the remaining SRG identifiers in `src/main/java` have been converted and compile errors resolved against the real Forge dependency graph.

This is intentional: we are no longer going to paper over missing Minecraft/Brigadier APIs with handwritten stubs. The real ForgeGradle compiler is now the source of truth.

## Apply the overlay

1. Extract this ZIP.
2. Copy the files inside `Season2-Core-ForgeGradle-Migration/` into the root of your local GitHub Desktop checkout of `Season2-Core`.
3. Keep all existing `src`, `presets`, `release`, and documentation files when Windows asks about merging folders.
4. Commit with a message such as `Add ForgeGradle migration foundation`.
5. Push to GitHub.
6. GitHub Actions will attempt the real Forge build and show us the exact mapped-name/API errors that remain.

## Next step

Use the Action build errors plus `python scripts/check_legacy_srg.py src/main/java` to convert the legacy source class-by-class. Start with Dragon Currency, then the common Spectral Mail core, then client screens, then postal/courier code. Do not add Town Life until the build is green on both client and dedicated-server launch paths.
