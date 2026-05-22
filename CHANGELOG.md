# Changelog

## [1.2.0] - 2026-05-23

### Added
- Settings screen — set a custom relay server URL from inside Minecraft (⚙ button on the multiplayer screen)
- Live relay ping shown in the tab list header with color-coded latency (green/yellow/red)
- Relay URL fetched dynamically from the VoxelPort bot at startup — relay server can change without a mod update

### Changed
- Discord verification cache reduced from 2 days to 12 hours
- Auth cache is now machine-bound (SHA-256 fingerprint) — auth files can no longer be shared between computers

### Fixed
- Per-session Discord validation (`/session/validate`) was defined but never called — now correctly called before hosting and before joining

### Security
- Added per-session Discord validation against the bot before hosting or joining
- Relay URL is no longer a plain string in the JAR (XOR-encoded fallback)
- Bot HTTP server `/verify/start` and `/verify/confirm` endpoints now properly protected by `BOT_SECRET` middleware (middleware was previously registered after routes, leaving them unprotected)

## [1.1.0] - 2026-05-22

### Added
- Full VoxelPort rebrand (previously LocalMiner)
- Replaced Cloudflare tunnels with VoxelPort relay network (`wss://voxelportrelay.qzz.io/relay`)
- Discord DM verification (bot sends 6-digit code — no OAuth required)
- Removed all cloudflared binaries from the mod JAR
- Room codes: 6-character alphanumeric (e.g. `AB12CD`)

### Security
- POSIX file permission hardening on config directory and auth cache
- Proper room code validation (`^[A-Z0-9]{6}$`)
- Removed blocking `.get()` calls in join proxy flow
