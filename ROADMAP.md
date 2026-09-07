# Roadmap

## 0.5.0-alpha.5 — Town Life foundation

The next major feature is a lightweight persistent town-life director for EasyNPC residents.

Planned first-stage scope:

1. Persistent Town NPC registration by EasyNPC UUID.
2. Per-NPC home location.
3. Per-NPC work location.
4. Optional social/activity location.
5. Minecraft-time schedule phases.
6. Role templates such as `resident`, `worker` and `guard`.
7. Lightweight movement requests rather than continuous per-tick decision logic.
8. Return-home behavior and safe schedule reconciliation after chunks reload.
9. Admin status/debug commands.
10. Restart persistence and recovery behavior.

The existing banker and postal courier remain specialized systems initially; the generic Town Life scheduler must not take over their behavior.

## Build-system milestone — must happen before large Town Life code growth

Move the codebase from manual production/SRG compilation to a normal ForgeGradle development build:

- Minecraft 1.20.1
- Forge 47.4.x
- Java 17 toolchain
- official mappings
- no hand-authored Minecraft/Brigadier stubs
- automated compile verification
- dedicated-server smoke test
- client launch smoke test
- release artifact/checksum generation

See `docs/BUILD_MIGRATION.md`.

## Later Town Life expansion

After the foundation is proven on the real server, add reusable roles such as:

- blacksmith
- farmer
- fisherman
- shopkeeper
- innkeeper
- librarian
- guard patrol variants
- healer/priest

Possible polish after stability:

- role/time/weather-aware ambient dialogue
- controlled local wandering around work/home anchors
- explicit guard patrol waypoints
- shared town social locations
- admin visual/debug markers
- data-driven role templates

## Longer-term Season2 Core polish

- Mail/admin recovery and diagnostic commands.
- Stronger SavedData schema/version migration.
- Transaction/audit logging for bank operations.
- Config validation with clear startup warnings.
- Optional localization and data/resource-pack customization.
- GitHub release automation after ForgeGradle migration.
