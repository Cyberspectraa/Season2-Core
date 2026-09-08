# Roadmap

## 0.5.0-alpha.5 — Town Life integration

Town Life 0.7.1 has now been validated in a combined runtime test with Dragon Currency and Spectral Mail.

The immediate goal is to convert that successful test into a proper single-source ForgeGradle build.

### Integration checklist

1. Merge Town Life 0.7.1 source into the Season2 Core source tree.
2. Preserve the `townlife` mod ID, registry IDs and SavedData ID for compatibility.
3. Preserve the working `SleepService` real-bed implementation.
4. Keep EasyNPC/Minecraft responsible for physical pathfinding, stairs and doors.
5. Keep the Dragon Bank banker and Spectral Mail courier outside generic Town Life scheduling.
6. Require EasyNPC `>=7.11.0` and `<8.0.0` for the Town Life development line.
7. Build the combined JAR through GitHub Actions.
8. Run client/server regression testing.
9. Merge the integration branch into `main` once the source-built JAR matches the working runtime test.

## Town Life foundation already proven

- Town Wand resident registration
- real bed home assignment
- workplace/job assignment
- HOME / COMMUTING / WORK / ERRAND / SLEEPING states
- local wandering only while settled at HOME or WORK
- real vanilla bed sleeping and wake handling
- hunger, energy, fun and social needs
- FOOD / TOOL / ARMOR service errands
- provider availability and reservation logic
- hostile-mob safety interruption
- player-interaction pause
- Dev Clock schedule testing

## Design rule

Season2 Core should **not become a custom NPC pathfinding mod**.

Town Life controls:

- destination
- schedule
- intention
- resident state

EasyNPC/Minecraft controls:

- path calculation
- stairs
- doors
- physical movement

The obsolete custom staircase scanners, staged-navigation systems, travel hops and custom door service should not be restored without a compelling reason.

## After 0.5.0 is stable

Possible Town Life expansion:

- richer professions
- blacksmith services
- taverns and food providers
- guards and patrol behaviour
- NPC conversations and social interactions
- Dragon Currency service/payment integration
- Spectral Mail NPC integration
- town events
- data-driven profession templates

## Build-system cleanup

After Town Life integration is stable:

- commit Mojang-mapped readable Java source directly
- remove SRG conversion from ordinary CI builds
- standardise module/release version metadata
- keep release binaries in GitHub Releases rather than the source repository
- automate release JAR/checksum generation
