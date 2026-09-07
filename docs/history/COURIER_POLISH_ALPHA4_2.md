# Season 2 Core 0.4.2-alpha.4 — Courier Polish

## Navigation
- Postal blocks are no longer targeted by their block centre.
- The courier reads each Drop Box / Letter Box horizontal facing and walks to a service point one block in front of the visible slot/door.
- If the front approach stalls or path creation fails, it tries the two front-corner approach points.
- Postal arrival uses a tighter 0.65-block service radius.
- Postal approach speed defaults to 0.78, even on older server configs that still contain the legacy movement speed.
- General movement speed defaults to 0.82. Existing untouched 1.05 legacy defaults are migrated automatically, while custom speed values are preserved.
- Arrival/route checks run four times per second; static postal paths are only re-issued twice per second.
- No chunks are force-loaded and recipient teleport rules are unchanged.

## EasyNPC preset support
- Added `/mail courierpresetbind <player>` for the supplied EasyNPC preset.
- When run from an EasyNPC command action, the named player must be an online server operator within 8 blocks.
- The exact EasyNPC command source is bound, avoiding accidental selection of a nearby banker or other NPC.
- Existing `/mail courier bind`, `sethome`, `return`, `clear`, and `status` remain unchanged.

## Data safety
- Mail SavedData format and mail state constants are unchanged.
- Letter Box-first routing is unchanged.
- Dragon Currency / bank classes are unchanged.
