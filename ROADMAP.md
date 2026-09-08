# Roadmap

## 0.6.0-alpha.6 — Core polish

The 0.5.0-alpha.5 Town Life integration is complete and runtime-tested. The current development line focuses on making the combined mod cleaner and safer without rewriting working gameplay systems.

### Current goals

1. Provide one **Season 2 Core** creative tab for current player-facing items and blocks from all three modules.
2. Keep old compatibility-only Dragon Currency pouch/helper registry entries hidden from the creative inventory.
3. Harden Dragon Bank arithmetic and preserve existing balances.
4. Keep Spectral Mail server-authoritative and preserve existing mail/world persistence.
5. Preserve Town Life's real-bed sleeping and existing HOME / COMMUTING / WORK / ERRAND / SLEEPING separation.
6. Keep EasyNPC/Minecraft responsible for physical movement, stairs and doors.
7. Keep specialised banker/courier NPCs outside generic resident scheduling.
8. Standardise the three Forge module versions to the single Season2 Core build version.
9. Regression-test the combined JAR before merging to `main`.

## Next build-system cleanup

Once the 0.6 gameplay-polish build is confirmed in game:

- convert the remaining legacy SRG-named Java source once
- commit the readable Mojang-mapped source directly
- remove the SRG source-rewrite step from normal CI
- keep the SRG audit script temporarily as a guard against regressions

This should be a separate, reviewable maintenance change rather than being mixed into gameplay changes.

## Later improvements

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
