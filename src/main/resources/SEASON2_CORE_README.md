# Season 2 Core - alpha.3

One Forge 1.20.1 JAR containing:

- `dragoncurrency` 1.4.5 (stable bank module preserved from the user-tested combined alpha.2 JAR)
- `spectralmail` 1.0.0-alpha.3

## Spectral Mail physical postal system

Alpha.3 keeps the working alpha.2 Minecraft mail, Discord Post Office, EasyNPC courier and parchment reader, then adds two real registered blocks:

- `spectralmail:drop_box` - outgoing public mail collection
- `spectralmail:letter_box` - one active personal delivery address per player

It also adds:

- `spectralmail:letter_paper`
- `spectralmail:addressed_letter`

### Physical outgoing flow

1. Craft/obtain Letter Paper.
2. Hold it in the main hand.
3. `/mail write <player> <message>` converts one sheet into an Addressed Letter.
4. Right-click a Drop Box while holding the Addressed Letter.
5. The server validates the sender, recipient and message, then creates the authoritative mail record and stores its ID in that Drop Box's persistent queue.
6. The courier must physically walk to the loaded Drop Box before the letter leaves it.
7. The courier then attempts recipient delivery.

`/mail send` remains available as the quick/fallback command route.

### Personal Letter Box flow

Placing a Letter Box makes it that player's active delivery address. One active address is tracked per player; placing a new one moves future delivery to the new box.

Courier priority is:

1. reachable online recipient
2. registered Letter Box if the player is offline, too far away, changes dimension, times out, or has a full inventory
3. leave the authoritative mail pending safely if neither route can be completed

The Letter Box holds 9 logical server-owned mail slots by default. Right-clicking your own Letter Box transfers sealed-letter items into your inventory until it is full. Other players cannot collect the contents.

Breaking an active Letter Box returns boxed records to PENDING rather than deleting them. Breaking a Drop Box releases its queued records back to normal Post Office pending delivery. Mail is never deliberately dropped on the ground as a courier failure fallback.

## EasyNPC courier

Operator commands remain:

- `/mail courier bind`
- `/mail courier sethome`
- `/mail courier return`
- `/mail courier clear`
- `/mail courier status`

The courier remains one trip at a time, does not force-load chunks, does not cross dimensions, never teleports to a recipient/Drop Box/Letter Box, and only optionally teleports *home* after a stuck return timeout.

## Discord Post Office

Discord remains entirely server-side and uses the same persistent mail/courier routing. Offline Discord recipients can now be delivered to their registered Letter Box when it is a valid loaded route.

Admin commands remain:

- `/mail discordsetup`
- `/mail discordstatus`
- `/mail discordreload`
- `/mail discordpanel`

Configuration: `config/spectralmail-server.toml`

## Persistence / compatibility

Alpha.1/alpha.2 `spectralmail_mail.dat` data remains readable. Postal state is additive:

- active Letter Box addresses
- Letter Box mail IDs
- Drop Box locations/queues
- existing mail records keep the same core fields

Dragon Currency classes are carried from the tested alpha.2 combined build unchanged.
