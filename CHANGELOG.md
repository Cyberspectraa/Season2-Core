# Changelog

This changelog tracks the combined Season2 Core development line.

## 0.5.0-alpha.5 — Town Life

- Integrated Town Life 0.7.1 into the Season2 Core source tree as the `townlife` module.
- Added Town Wand resident registration, real-bed home assignment and workplace assignment.
- Added resident schedule states: HOME, COMMUTING, WORK, ERRAND and SLEEPING.
- Added real vanilla bed sleeping through `SleepService` with no floor-sleep fallback.
- Added hunger, energy, fun and social needs plus lightweight FOOD / TOOL / ARMOR service errands.
- Kept EasyNPC/Minecraft responsible for physical navigation, stairs and doors; the removed custom staged/stair-aware navigation systems remain absent.
- Preserved Town Life IDs and persistence, including the `townlife` mod ID, SavedData ID, `townlife:town_wand` and `townlife:dev_clock`.
- Protected the currently bound Spectral Mail courier from generic Town Life registration and scheduling.
- Confirmed Dragon Currency, Spectral Mail and Town Life coexist in one Forge 1.20.1 JAR through source-integrated in-game testing.
- Requires EasyNPC `>=7.11.0` and `<8.0.0`.

## 0.4.4-alpha.4 — NPC integration

- Bundled the supplied 64x64 postman skin as `spectralmail:textures/entity/postman.png`.
- Added/updated the Spectral Post Courier EasyNPC preset to use the bundled resource-location skin.
- Re-supplied the Dragon Bank Banker EasyNPC preset.
- Corrected banker dialogue to describe the current deposit-only bank accurately.
- Kept all compiled Java classes byte-for-byte unchanged from 0.4.3-alpha.4.

## 0.4.3-alpha.4 — command startup hotfix

- Fixed the Brigadier command-registration JVM descriptor problem introduced by the courier preset binding command.
- Preserved 0.4.2 courier navigation behavior.

## 0.4.2-alpha.4 — courier polish

- Added directional front service points for Drop Boxes and Letter Boxes.
- Added fallback approach positions when the direct service position is obstructed or makes no progress.
- Tightened postal arrival behavior and reduced courier service movement speed.
- Added the preset-friendly courier bind command and initial courier preset.

## 0.4.1-alpha.4 — postal interaction and routing polish

- Fixed ground rendering under inset postal models with non-occluding block behavior.
- Added model-matched directional voxel/collision shapes.
- Added metal/wood material sound behavior.
- Changed courier routing to prefer a usable registered Letter Box before player delivery.
- Added lightweight postal sound/particle feedback.

## 0.4.0-alpha.4 — postal model redesign

- Rebuilt Drop Box and Letter Box visuals with vanilla-like multi-cuboid models and face-specific textures.
- Preserved existing compiled gameplay classes.

## 0.3.3-alpha.3 — directional runtime fix

- Corrected the `BlockState#setValue` production JVM descriptor used by the manually compiled directional postal block implementation.

## 0.3.x-alpha.3 — physical postal system

- Added Drop Box and Letter Box registered blocks.
- Added physical Letter Paper and Addressed Letter workflow.
- Added persistent Drop Box queues and personal Letter Box storage/addressing.
- Added courier collection/delivery routing and safety behavior around broken boxes.

## 0.2.0-alpha.2 — courier and Discord

- Added EasyNPC courier integration.
- Added server-side Discord Post Office integration.

## 0.1.0-alpha.1 — combined core foundation

- Combined Dragon Currency and Spectral Mail into one Forge 1.20.1 client/server JAR.
- Established physical mail items and client parchment reading.
