# Current baseline: 0.4.4-alpha.4

This repository package is based on the existing `Season2-Core-0.4.4-alpha.4-forge-1.20.1.jar` and source archive.

## Known-good design state carried forward

- Dragon Currency bank data/behavior from the stable 1.4.5 line.
- Combined one-JAR client/server installation.
- Persistent Spectral Mail SavedData.
- Physical mail items and parchment reader.
- Drop Box outgoing queues.
- Personal Letter Box storage/addressing.
- Directional postal blocks and model-matched shapes.
- Terrain-under-model rendering fix.
- Letter Box-first courier routing.
- Courier front service-point navigation polish.
- Server-only Discord Post Office behavior.
- Bundled courier skin and current banker/courier EasyNPC presets.

## Known technical debt

- Legacy Java source contains production/SRG names from manual compilation.
- `SpectralMail.VERSION` in the unchanged compiled Java source and the presentation version in `mods.toml` are not perfectly aligned in the 0.4.x history. Do not change the runtime baseline solely to make the string prettier; correct versioning as part of the ForgeGradle migration.
- Manual compile stubs must not be revived for future major features.
