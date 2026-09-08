# Town Life 0.7.1 integration contract

Town Life decides resident schedules, needs, state, intentions and destinations. EasyNPC/Minecraft remains responsible for physical navigation, stairs and doors.

The first Season2 Core integration preserves the tested 0.7.1 identifiers and persistence: mod ID `townlife`, SavedData ID `townlife`, `townlife:town_wand`, and `townlife:dev_clock`.

The removed custom staged/stair-aware navigation and custom door systems must not be reintroduced. `SleepService` continues to use real vanilla beds with no floor-sleep fallback.

The currently bound Spectral Mail courier is explicitly protected from Town Life registration and scheduling. The banker remains a specialist NPC and should not be registered with the Town Wand.

Town Life does not force-load chunks. Resident AI continues only while the relevant chunks are loaded/ticking.
