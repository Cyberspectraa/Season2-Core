# Roadmap

## 0.6.0-alpha.6 — Core polish

The 0.5.0-alpha.5 Town Life integration is complete and runtime-tested. The 0.6 development line focuses on making the combined mod cleaner and safer without rewriting working gameplay systems.

### Completed in the current development line

1. Added one **Season 2 Core** creative tab for current player-facing items and blocks from all three modules.
2. Kept old compatibility-only Dragon Currency pouch/helper registry entries hidden from the creative inventory.
3. Hardened Dragon Bank arithmetic while preserving existing balances.
4. Preserved Spectral Mail server authority, mail/world persistence and courier behavior.
5. Preserved Town Life's real-bed sleeping and HOME / COMMUTING / WORK / ERRAND / SLEEPING separation.
6. Kept EasyNPC/Minecraft responsible for physical movement, stairs and doors.
7. Kept specialised banker/courier NPCs outside generic resident scheduling.
8. Standardised the three Forge module versions to the single Season2 Core build version.
9. Regression-tested the combined 0.6.0-alpha.6-dev JAR in game.
10. Converted the remaining legacy SRG-named Java source once and committed readable Mojang-mapped source directly.
11. Removed SRG source rewriting from normal build and release workflows while keeping the SRG audit as a guardrail.

## Next improvements

### Dragon Currency

- planned withdrawal/payment flow once the economy needs it
- shared payment API for Town Life services without exposing raw balance mutation
- optional transaction logging/admin diagnostics

### Spectral Mail

- split large courier/Discord classes into smaller services without changing persistence format
- improve diagnostics for stuck/invalid postal records
- keep postal routing and Discord authority server-side

### Town Life

- richer professions and service providers
- blacksmith/tavern/market interactions
- guards and patrol behaviour
- NPC conversations and social interactions
- optional Dragon Currency payments for services
- optional Spectral Mail-related resident jobs
- data-driven profession templates

## Design rule

Season2 Core should **not become a custom NPC pathfinding mod**.

Town Life controls destination, schedule, intention and resident state. EasyNPC/Minecraft controls path calculation, stairs, doors and physical movement. The obsolete staged-navigation, staircase scanning, travel-hop and custom door systems should remain removed unless a concrete regression proves otherwise.
