# Season 2 Core 0.4.2-alpha.4 — Runtime Testing

## Courier pathing
1. Place a Drop Box facing north and post an addressed letter.
2. Watch the courier approach the visible front/mail slot. It should stop in the block space in front instead of walking into the Drop Box.
3. Repeat with Drop Boxes facing east, south, and west.
4. Put a solid obstacle directly in front of a Drop Box. The courier should eventually try a front-corner approach rather than continuously pushing into the block.
5. Repeat the facing/obstacle tests with a Letter Box.
6. Confirm the courier now walks at a calmer pace; postal service approach uses a separate 0.78 default speed.

## Delivery routing
7. With a valid Letter Box registered, stay online near the courier. The courier should still deliver to the Letter Box first.
8. Fill the Letter Box and verify the courier falls back to the player.
9. Make both destinations unusable and verify the letter stays pending safely.
10. Confirm Discord-originated mail follows the same route.

## EasyNPC preset
11. Import `spectral_post_courier.npc.snbt` into EasyNPC 7.10.0.
12. As an operator, stand beside the NPC and choose Post Office setup -> Bind this NPC as the courier.
13. Verify `/mail courier status` reports the courier as bound.
14. Confirm a non-operator cannot use the preset binding button successfully.
15. Place another EasyNPC nearby and repeat binding; the preset should bind the exact courier command-source NPC when EasyNPC exposes itself as the command source.

## Regression
16. Restart the server with pending and boxed mail and confirm all mail survives.
17. Confirm Drop Box posting, courier pickup, Letter Box collection, letter reading, and `/mail collect` still work.
18. Confirm Dragon Bank opens and deposits still work.
19. If Discord is enabled, confirm `/mail discordstatus` and the Post Office panel still work.
