# Season2 Core

Private-server gameplay systems for **Minecraft 1.20.1 Forge**.

## Status

- **Latest pre-release:** `0.5.0-alpha.5 — Town Life`
- **Current development line:** `0.7.0-alpha.7-dev — Town Paths`
- **Java:** 17
- **Forge:** 47.4.x
- **Distribution:** GitHub Releases

Season2 Core ships Dragon Currency, Spectral Mail and Town Life together in one Forge 1.20.1 JAR.

## Modules

| Module | Purpose |
| --- | --- |
| `dragoncurrency` | Dragon-themed physical currency and persistent Dragon Bank |
| `spectralmail` | Physical player mail, postal blocks, EasyNPC courier and Discord Post Office |
| `townlife` | EasyNPC resident schedules, homes, workplaces, sleeping, needs, service errands and preferred town roads |

Town Life remains intentionally lightweight: it decides **what** a resident should do and which registered road it should prefer; EasyNPC/Minecraft remains responsible for physical navigation, doors, stairs and collision avoidance.

## Requirements

- Minecraft 1.20.1
- Forge 47.4.x
- Java 17
- EasyNPC `>=7.11.0` and `<8.0.0`

## Creative inventory

Development builds expose current player-facing content from all three modules in one **Season 2 Core** creative tab:

- Copper, Silver, Gold, Platinum and Dragon Coins
- Letter Paper, Addressed Letter, Sealed Letter and Opened Letter
- Drop Box and Letter Box
- Town Wand, Path Wand and Town Life Dev Clock

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
- Path Wand registration/removal/inspection of preferred road blocks
- high-level Town Path routing for normal commutes and errands
- Dev Clock schedule testing
- specialised banker and courier NPCs remain outside generic Town Life control

### Town Paths

The operator-only Path Wand registers explicit road surface blocks. Normal Town Life travel prefers those registered roads when both the NPC and destination are close enough to the same connected road network.

- right-click a walkable surface to register connected blocks of the same block type
- one-block height changes are included so sloped/stair roads can remain connected
- sneak + right-click removes only the clicked registered road block
- right-click air highlights nearby registered road blocks
- road data is stored separately per dimension in `townlife_paths.dat`
- emergency shelter movement bypasses roads
- Town Life plans road waypoints only; EasyNPC/Minecraft still performs the actual walking

## Compatibility rules

The 0.7 development line keeps all existing module IDs, registry IDs and persistent data IDs intact. Town Paths adds `townlife:path_wand` and a separate `townlife_paths.dat`; it does not rewrite existing resident, bank or mail data.

## Building

GitHub Actions uses Java 17 and ForgeGradle against the readable Mojang-mapped Java source committed directly in the repository. Normal build and release workflows audit for accidental legacy SRG identifiers and compile without rewriting source files first.

The one-time SRG-to-Mojang migration script is retained only as maintenance history/tooling; it is no longer part of ordinary CI or release builds.

## Releases

Use the repository's **Releases** page for official JAR downloads. Normal development builds are also available as GitHub Actions artifacts.

## Security

Never commit Discord bot tokens, server configs containing secrets, world saves or private player data. See `SECURITY.md`.
