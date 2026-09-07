# Security

Season2 Core contains server-side Discord integration and persistent player/economy data. Treat server configuration and world data as private.

## Never commit

- Discord bot tokens.
- `config/spectralmail-server.toml` from a production server if it contains credentials.
- Server console logs containing secrets.
- World saves or `spectralmail_mail.dat` from a live server.
- Player UUID/name caches unless intentionally sanitized.
- Passwords, API keys or webhook secrets.

The repository `.gitignore` excludes the normal Spectral Mail production config path, but always review staged files before pushing.

## Reporting a problem

For a private server project, report security-sensitive issues directly to the project owner rather than posting credentials or private server data in a public GitHub issue.
