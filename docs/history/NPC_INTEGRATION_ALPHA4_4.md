# 0.4.4-alpha.4 - NPC Integration

This release deliberately does not reimplement fixes already present in 0.4.3.

Changes:
- exact user-supplied 64x64 postman skin bundled at `assets/spectralmail/textures/entity/postman.png`;
- courier preset switched from a remote URL to EasyNPC `RESOURCE_LOCATION`;
- courier and banker presets included as standalone release files and as EasyNPC third-party datapack presets;
- banker preset dialogue corrected to match the current deposit-only bank;
- banker preset no longer contains world-specific Home coordinates.

No Java classes changed. Courier AI/pathing, Letter Box-first routing, postal collision/occlusion, mail persistence, Discord and banking logic are identical to 0.4.3.
