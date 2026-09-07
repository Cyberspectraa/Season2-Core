# 0.4.3-alpha.4 — Command startup compatibility fix

This hotfix corrects the Brigadier JVM descriptors used by `MailCommands.class`.

0.4.2-alpha.4 introduced `/mail courierpresetbind`, which required recompiling
MailCommands. The temporary Brigadier compile stubs used Object-return/argument
signatures for several builder methods. Those signatures do not match the
Brigadier classes shipped by Minecraft 1.20.1 and caused a NoSuchMethodError
during RegisterCommandsEvent, preventing dedicated-server startup.

0.4.3-alpha.4 recompiles only MailCommands.class with Brigadier-compatible
signatures. Courier navigation, postal routing, SavedData, Discord, postal
models, PostalBlock, and Dragon Currency are unchanged.
