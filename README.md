# Season2 Core

Private-server gameplay systems for **Minecraft 1.20.1 Forge**.

## Status

- **Stable release:** `0.4.4-alpha.4`
- **Current development line:** `0.5.0-alpha.5 — Town Life`
- **Java:** 17
- **Forge:** 47.4.x
- **Distribution:** GitHub Releases

The stable 0.4.4 release contains Dragon Currency and Spectral Mail. Town Life 0.7.1 has been validated in a combined runtime test and is being integrated properly into the source tree for 0.5.0.

## Modules

| Module | Purpose |
| --- | --- |
| `dragoncurrency` | Dragon-themed physical currency and persistent Dragon Bank |
| `spectralmail` | Physical player mail, postal blocks, EasyNPC courier and Discord Post Office |
| `townlife` | EasyNPC resident schedules, homes, workplaces, sleeping, needs and lightweight service errands |

Town Life remains intentionally lightweight: it decides **what** a resident should do and where it should go; EasyNPC/Minecraft remains responsible for actual navigation, doors and stairs.

## Requirements

### Stable 0.4.4

- Minecraft 1.20.1
- Forge 47.4.x
- Java 17
- EasyNPC 7.10.x compatible setup

### 0.5.0 Town Life development

- Minecraft 1.20.1
- Forge 47.4.x
- Java 17
- EasyNPC `>=7.11.0` and `<8.0.0`

## Repository layout

```text
Season2-Core/
├── .github/workflows/   GitHub Actions build
├── docs/                development notes
├── presets/             EasyNPC presets
├── scripts/             source/build maintenance tools
├── src/main/java/       mod source
├── src/main/resources/  assets, data and Forge metadata
├── build.gradle
├── gradle.properties
├── settings.gradle
├── CHANGELOG.md
├── ROADMAP.md
└── README.md
```

Release JARs are stored in **GitHub Releases**, not committed into the repository.

## Current gameplay systems

### Dragon Currency

- Copper, Silver, Gold, Platinum and Dragon coins
- persistent server-authoritative bank balances
- EasyNPC banker integration
- current bank flow is deposit-only

### Spectral Mail

- writable/addressable physical letters
- public Drop Boxes
- player-owned Letter Boxes
- Letter Box-first courier delivery
- EasyNPC courier integration
- Discord Post Office integration
- persistent offline-safe mail records

### Town Life — 0.5 development

- Town Wand resident registration
- real bed home assignment
- workplace/job assignment
- HOME / COMMUTING / WORK / ERRAND / SLEEPING states
- real vanilla bed sleeping through `SleepService`
- hunger, energy, fun and social needs
- FOOD / TOOL / ARMOR service errands
- automatic EasyNPC resident movement setup
- local home/work roaming
- Dev Clock schedule testing
- specialised banker and courier NPCs remain outside generic Town Life control

## Building

GitHub Actions uses Java 17 and ForgeGradle. The repository still contains legacy SRG-named source from the old manual build history, so CI currently performs the SRG-to-Mojang migration before compiling.

Long-term cleanup is to commit the readable Mojang-mapped source directly and remove that conversion from ordinary builds.

## Releases

Use the repository's **Releases** page for official JAR downloads. Development build artifacts are produced by GitHub Actions when enabled by the active workflow.

## Security

Never commit Discord bot tokens, server configs containing secrets, world saves or private player data. See `SECURITY.md`.
