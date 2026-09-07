# Distribution plan

## Goal

Keep Season2 Core off CurseForge as its own public mod project while still making it possible to include the official JAR in the Season 2 modpack.

## GitHub Releases

Use GitHub Releases as the canonical download location for official builds.

For each release:

1. Create a tag matching the build, for example `v0.4.4-alpha.4`.
2. Create a GitHub Release from that tag.
3. Upload the official Forge JAR from `release/<version>/`.
4. Upload the source archive, checksum file, verification notes and testing checklist.
5. Keep the release notes explicit about Minecraft, Forge and Java versions.

The files under `release/0.4.4-alpha.4/` in this repository package are ready to use when creating the first release manually.

## CurseForge modpack inclusion

The intended route is to use CurseForge's process for an approved non-CurseForge/third-party mod and point reviewers at the official GitHub project/release and its licensing/permission information.

Platform rules can change, so re-check CurseForge's current third-party modpack policy before submitting the pack.

## During active development

For testers, using the GitHub Release JAR directly is preferable to publishing every alpha as a standalone CurseForge mod project.
