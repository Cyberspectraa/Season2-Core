# Release checklist

## Build identity

- [ ] Minecraft 1.20.1 target confirmed.
- [ ] Exact Forge 47.4.x target recorded.
- [ ] Java 17 / class major 61 confirmed.
- [ ] `mods.toml` versions updated intentionally.
- [ ] No registry IDs changed accidentally.

## Dedicated server

- [ ] Server reaches ready state without command-registration errors.
- [ ] `/bal` works.
- [ ] Bank NPC opens the bank.
- [ ] Bank deposit persists after restart.
- [ ] `/mail courier status` works.
- [ ] Drop Box posting works.
- [ ] Courier pickup works without walking into the postal block indefinitely.
- [ ] Letter Box-first delivery works.
- [ ] Mail remains safe when no route is available.
- [ ] Discord service starts/reloads with token kept server-side.

## Client

- [ ] Coins render correctly.
- [ ] Drop Box and Letter Box models render correctly in all directions.
- [ ] Terrain under postal models renders correctly.
- [ ] Postal collision/selection matches the model.
- [ ] Sealed/opened letter reader works.
- [ ] Banker and courier EasyNPC presets/skins render correctly.

## Persistence / safety

- [ ] Existing world loads without data conversion loss.
- [ ] Pending mail survives restart.
- [ ] Letter Box contents survive restart.
- [ ] Breaking/replacing postal blocks does not lose or duplicate mail.
- [ ] No credentials or world data are included in the release archive.

## Release files

- [ ] JAR checksum generated.
- [ ] Verification notes updated.
- [ ] Testing checklist updated.
- [ ] Source archive generated.
- [ ] GitHub tag/release notes created.
