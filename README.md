# Season2 Core

Private-server gameplay systems for **Minecraft 1.20.1 Forge**, maintained for the Minecraft Season 2 server.

Season2 Core currently ships as one client + server JAR containing two Forge mod IDs:

- **`dragoncurrency`** — physical dragon-themed coins and the persistent Dragon Bank.
- **`spectralmail`** — physical player mail, postal blocks, EasyNPC courier integration, Discord Post Office integration, and client-side letter presentation.

## Current baseline

**Season2 Core 0.4.4-alpha.4** is the current repository baseline.

Target environment:

- Minecraft **1.20.1**
- Minecraft Forge **47.4.x**
- Java **17**
- EasyNPC **7.10.0** integration
- Combined client/server JAR

The tested release JAR and its verification/checksum files are in [`release/0.4.4-alpha.4`](release/0.4.4-alpha.4/).

## Dragon Currency

Dragon Currency retains the stable `1.4.5` bank implementation.

Coin values:

| Coin | Value |
| --- | ---: |
| Copper Coin | 1 |
| Silver Coin | 10 |
| Gold Coin | 100 |
| Platinum Coin | 1,000 |
| Dragon Coin | 10,000 |

The bank uses a persistent server-authoritative balance. The current intended bank flow is **deposit only**; withdrawals are deliberately not part of the current design.

## Spectral Mail

Spectral Mail includes:

- Letter Paper, Addressed Letter, Sealed Letter and Opened Letter items.
- Persistent/offline-safe mail records.
- Public outgoing **Drop Boxes**.
- Player-owned incoming **Letter Boxes**.
- Letter Box-first courier routing with player fallback.
- EasyNPC courier binding and movement integration.
- Bundled courier skin and EasyNPC presets.
- Parchment-style client letter reader.
- Server-side Discord Post Office integration.
- Directional postal models, model-matched voxel shapes, interaction sounds and particles.

The server owns authoritative mail state. Client code is only used where Minecraft/Forge requires client-side registered content or presentation.

## EasyNPC presets

Ready-to-import presets are kept in [`presets/`](presets/) and are also bundled as data presets inside the mod resources.

- `spectral_post_courier.npc.snbt`
- `dragon_bank_banker.npc.snbt`

The courier uses the bundled resource texture:

`assets/spectralmail/textures/entity/postman.png`

## Build status

The **0.4.4-alpha.4 runtime JAR is the known baseline**, but its source history came from a manual production/SRG compilation workflow. Some Java source therefore still contains production SRG method names.

Before major Town Life development, this project should be migrated to a normal **ForgeGradle + official mappings + Java 17** development build. See [`docs/BUILD_MIGRATION.md`](docs/BUILD_MIGRATION.md).

Until that migration is completed, do not assume a fresh IDE/ForgeGradle build of the legacy source will succeed without mapped-name cleanup.

## Roadmap

The next major development line is **0.5.0-alpha.5 — Town Life**: persistent EasyNPC residents with homes, jobs, social locations, schedules and lightweight role-driven routines.

See [`ROADMAP.md`](ROADMAP.md).

## Distribution

The intended distribution route is GitHub Releases for the official Season2 Core JAR, with CurseForge modpack inclusion handled as an approved third-party/non-CurseForge file where applicable.

See [`docs/DISTRIBUTION.md`](docs/DISTRIBUTION.md).

## Security

Never commit Discord bot tokens, production server configs, world saves, player data or secrets. See [`SECURITY.md`](SECURITY.md).

## License

See [`LICENSE`](LICENSE). The current project license remains unchanged from the 0.4.4 source baseline.
