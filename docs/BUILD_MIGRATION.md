# ForgeGradle migration notes

## Why this migration is required

The current 0.4.4-alpha.4 runtime JAR was assembled from a legacy manual compilation workflow using production/SRG names and hand-authored compile stubs. That workflow was useful for recovering the project, but it has already exposed two high-risk failure classes:

- Minecraft generic/JVM descriptor mismatches, such as `StateHolder#setValue` erasure.
- Brigadier command-builder descriptor mismatches, such as compiling `RequiredArgumentBuilder.argument` with `Object` instead of `ArgumentType`.

The runtime JAR is the baseline. The manual build method is not the desired future development environment.

## Target development environment

- Minecraft: `1.20.1`
- Forge: `47.4.x` (keep the exact server target explicit per release)
- Java toolchain: `17`
- ForgeGradle: the supported 6.x line for Forge 1.20.1
- Mappings: `official`, Minecraft `1.20.1`

Forge's 1.20.1 MDK uses Java 17 and ForgeGradle/official mappings. The migration should begin from an official 1.20.1 MDK rather than constructing more compile stubs.

## Important source-state warning

Some current Java files intentionally contain production SRG method names such as `m_..._`. Those names were used to make manually compiled production bytecode link correctly.

A standard ForgeGradle project using official mappings normally expects mapped/development method names. Therefore, simply dropping the current Java source into an MDK and assuming it builds is unsafe.

## Safe migration sequence

1. Keep the released 0.4.4-alpha.4 JAR as the binary reference baseline.
2. Create a clean Forge 1.20.1 MDK project with Java 17.
3. Copy resources unchanged first and verify resource processing.
4. Move Dragon Currency Java classes into the MDK and replace production SRG calls with their official mapped equivalents.
5. Compile and compare runtime behavior with the current stable bank.
6. Move Spectral Mail classes in small groups, converting SRG calls deliberately.
7. Do not infer method descriptors from handwritten stubs; compile against the real Forge/Minecraft dependency graph.
8. Add a dedicated-server launch smoke test before introducing Town Life.
9. Add a client launch smoke test for menus/screens/items/blocks.
10. Only after both launch paths are stable should 0.5.0 Town Life development begin.

## Compatibility rules during migration

Do not rename registry IDs, SavedData IDs, mail state values, NBT field names, menu IDs, item/block IDs or the two existing mod IDs without a deliberate migration plan.

Preserve at minimum:

- `dragoncurrency`
- `spectralmail`
- `spectralmail_mail` SavedData identifier
- all currently registered coin/mail/postal IDs
- existing persistent bank balances
- existing Letter Box addresses and queued mail

## Release acceptance criteria

A migrated build should not replace the current server baseline until it passes:

- Java class version 61 verification.
- dedicated-server startup and command registration.
- client startup and screen registration.
- bank deposit regression test.
- physical mail posting and reading.
- Drop Box courier pickup.
- Letter Box-first delivery.
- restart persistence.
- Discord service startup/reload without exposing the token.
