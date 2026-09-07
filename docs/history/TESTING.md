# Season 2 Core alpha.3 test plan

Back up the test world first. Replace alpha.2 with alpha.3 on the server and clients. Do not also install standalone Dragon Currency or Spectral Mail JARs.

## 1. Regression

- `/bal` reports the existing balance.
- Existing EasyNPC banker opens the 1x1 bank.
- Coin deposits still work.
- `/mail send`, Minecraft courier delivery, Discord courier delivery, sealed/opened letters and parchment reader still behave as alpha.2.

## 2. Blocks and items

Use recipes or `/give` to obtain:

- `spectralmail:drop_box`
- `spectralmail:letter_box`
- `spectralmail:letter_paper`

Confirm both blocks place, render and drop themselves when broken.

## 3. Physical outgoing Drop Box

1. Place a Drop Box somewhere within the courier route.
2. Hold Letter Paper in the main hand.
3. Run `/mail write <recipient> hello from the drop box`.
4. The held paper should become an Addressed Letter (or one addressed item should enter inventory when holding a stack).
5. Its tooltip should say who it is addressed to.
6. Right-click the Drop Box with it.
7. The item should be consumed only after the server accepts it.
8. The Drop Box should report one queued letter when right-clicked empty-handed.
9. The courier should physically walk to the Drop Box, visibly pick up a sealed letter, then continue toward the recipient/Letter Box.
10. Break a Drop Box while mail is waiting: the mail must remain pending and must not disappear.

## 4. Personal Letter Box

1. Recipient places a Letter Box. Action bar should say it is now their active Letter Box.
2. Send/post mail while recipient is offline, too far from the courier, or in a different dimension.
3. If the Letter Box is loaded, in the courier's dimension and within route distance, the courier should use it instead of failing the delivery.
4. Right-click the Letter Box as its owner: sealed letters should move into inventory.
5. Another player right-clicking it must not collect the mail.
6. Fill the owner's inventory; letters must remain safely in the box.
7. Fill the logical Letter Box capacity (default 9); additional mail must stay PENDING rather than being deleted.
8. Break the active Letter Box while it contains mail. Those records must return to pending delivery.
9. Place a new Letter Box: it becomes the active delivery address and pending mail is re-queued.

## 5. Courier fallbacks

- Online and reachable: courier attempts player first.
- Player inventory full: courier falls back to Letter Box when valid.
- Player path timeout: courier falls back to Letter Box when valid.
- Player offline: courier can route directly to Letter Box.
- Letter Box chunk unloaded / different dimension / out of route / full: mail stays pending.
- Courier never teleports to player or postal blocks.
- Return timeout may teleport home only.

## 6. Discord

Repeat an offline Discord delivery with a registered Letter Box. It should enter the same courier route and be deposited as the same sealed-letter mail.

## Current deliberate limitation

Alpha.3 uses `/mail write <player> <message>` to turn held Letter Paper into an Addressed Letter. This proves the physical posting/storage/routing model without adding a second custom networked writing screen in the same update. A dedicated writing GUI can replace this command later without changing Drop Box or courier storage formats.
