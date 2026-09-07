# Season 2 Core 0.3.2-alpha.3

Directional postal block update.

- Drop Box and Letter Box now have a horizontal `facing` blockstate.
- When placed, the front faces toward the player.
- Only NORTH/SOUTH/EAST/WEST are valid.
- Vanilla structure rotation/mirroring is inherited from `HorizontalDirectionalBlock`.
- Existing courier, Discord, mail storage, bank, textures, and postal interaction logic are unchanged.
