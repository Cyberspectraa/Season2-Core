# Season2 Core

Private-server gameplay systems for **Minecraft 1.20.1 Forge**.

## Status

- **Latest pre-release:** `0.5.0-alpha.5 — Town Life`
- **Current development line:** `0.6.0-alpha.6-dev — Core polish`
- **Java:** 17
- **Forge:** 47.4.x
- **Distribution:** GitHub Releases

Season2 Core ships Dragon Currency, Spectral Mail and Town Life together in one Forge 1.20.1 JAR.

## Modules

| Module | Purpose |
| --- | --- |
| `dragoncurrency` | Dragon-themed physical currency and persistent Dragon Bank |
| `spectralmail` | Physical player mail, postal blocks, EasyNPC courier and Discord Post Office |
| `townlife` | EasyNPC resident schedules, homes, workplaces, sleeping, needs and lightweight service errands |

Town Life remains intentionally lightweight: it decides **what** a resident should do and where it should go; EasyNPC/Minecraft remains responsible for actual navigation, doors and stairs.

## Requirements

- Minecraft 1.20.1
- Forge 47.4.x
- Java 17
- EasyNPC `>=7.11.0` and `<8.0.0`

## Creative inventory

Development builds from 0.6.0 onward expose the current player-facing content from all three modules in one **Season 2 Core** creative tab:

- Copper, Silver, Gold, Platinum and Dragon Coins
- Letter Paper, Addressed Letter, Sealed Letter and Opened Letter
- Drop Box and Letter Box
- Town Wand and Town Life Dev Clock

Old Dragon Currency pouch GUI helper items remain registered only for registry/world compatibility and are intentionally hidden from the shared tab.

## Current gameplay systems

### Dragon Currency

- Copper, Silver, Gold, Platinum and Dragon coins
- persistent server-authoritative bank balances
- EasyNPC banker integration
- current bank flow is deposit-only
- overflow-safe bank debit handling for future withdrawal/payment features

### Spectral Mail

- writable/addressable physical letters
- public Drop Boxes
- player-owned Letter Boxes
- Letter Box-first courier delivery
- EasyNPC courier integration
- Discord Post Office integration
- persistent offline-safe mail records

### Town Life

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

## Compatibility rules

The 0.6 development line keeps all existing module IDs, registry IDs and persistent data IDs intact. The creative-tab cleanup does not rename `dragoncurrency:*`, `spectralmail:*` or `townlife:*` content.

## Building

GitHub Actions uses Java 17 and ForgeGradle. The repository still contains legacy SRG-named source from the old manual build history, so CI currently performs the SRG-to-Mojang migration before compiling.

The next build-system cleanup is to commit the readable Mojang-mapped Java source directly and then remove the source-rewrite step from ordinary CI.

## Releases

Use the repository's **Releases** page for official JAR downloads. Normal development builds are also available as GitHub Actions artifacts.

## Security

Never commit Discord bot tokens, server configs containing secrets, world saves or private player data. See `SECURITY.md`.
