# Changelog

## [1.4.3] - 2026-07-23

### Changed
- **Pause-menu layout:** the **Open to VoxelPort** and **Settings** buttons now sit at the bottom of the pause menu instead of overlapping other controls.

### Docs
- Revised the Modrinth store description for Content-Rules §2 (Description Clarity): the listing now leads with the mod itself, and promotion of the separate desktop app is minimized and clearly marked below the main body.

## [1.4.2] - 2026-07-15

### Fixed
- **`/voxel start` intermittently failed with "Timed out waiting for relay to assign a public port."** The `register` frame is enqueued from the WebSocket `onOpen` callback, but the start routine cleared the send queue *after* connecting — a race that could delete the register frame before the sender thread transmitted it. The relay then never replied and the mod timed out. The queue is now cleared *before* connecting, so the register is always sent. (More likely to trigger on fast/low-latency relay connections.)

## [1.4.1] - 2026-07-15

### Fixed
- **Hosts could not go live / players could not connect.** The relay's WebSocket control endpoint moved to the `/ws` path, but the mod was still connecting to the bare root (`wss://relay.voxelport.in`), so the handshake was rejected and `/voxel start` failed. The mod now connects to `wss://relay.voxelport.in/ws`, matching the desktop app.

### Removed
- **Delhi relay region.** `del.voxelport.in` was never deployed, so the "Delhi" settings preset and `/voxel region delhi` command pointed at a host that does not resolve — selecting them broke hosting. The single Mumbai relay (`relay.voxelport.in`) is now the only endpoint; the `/voxel region` command and region preset buttons have been removed. Custom/self-hosted relay URLs are still supported via the Settings screen.

## [1.4.0] - 2026-07-09

### Changed
- **No Discord, no signup.** VoxelPort now generates a private device token automatically on first launch. The `/gettoken` Discord flow is gone — hosting just works after install.
- Default relay control endpoint is now `wss://relay.voxelport.in` (players still join `play.voxelport.in:<port>`).
- Settings screen: the token field is now an optional advanced override; it is auto-managed by default.

### Removed
- All Discord membership / verification requirements and related UI/links.

## [1.3.1] - 2026-06-28

### Fixed
- **Random player disconnects** ("WebSocket send failed: Send pending"): replaced fire-and-forget `sendText` calls with a single-threaded sender queue so concurrent player I/O threads, the ping scheduler, and close-frame senders can never overlap on the WebSocket — Java's WebSocket only allows one outstanding send at a time.

## [1.3.0] - 2026-06-27

### Added
- **Delhi relay region** (`wss://del.voxelport.in`) — second official relay in India.
- Region preset buttons in the Settings screen: "Mumbai" and "Delhi" fill the relay URL in one click.
- `/voxel region <mumbai|delhi>` server command (op-only) to switch relay region from console or in-game.
- "Test" button in Settings to verify WebSocket reachability before saving.
- "↩ Default" button in Settings to clear any custom relay URL.

### Changed
- Settings screen layout updated to accommodate region presets and test button.
- `RelayUrlResolver` now recognises both `play.voxelport.in` and `del.voxelport.in` as official hosts.

## [1.2.0] - 2026-06-21

### Added
- Dedicated Fabric server support through `/voxelport` commands.
- Discord-issued server token flow matching the Paper plugin.
- Stable assigned public address flow: `play.voxelport.in:<assigned-port>`.
- Server-side config at `config/voxelport/settings.properties`.

### Fixed
- Buffered fragmented WebSocket text frames before parsing relay messages.
- Serialized outgoing WebSocket writes so relay frames cannot overlap under load.
- Fixed random Minecraft compression disconnects caused by corrupted packet streams.
- Fixed the Windows Gradle wrapper so builds no longer pause at the end.

### Security
- Removed exposed client-side bot secrets from the mod source and release JAR.
- Token is stored locally on the server and should be treated like a password.

## [1.1.0] - 2026-05-22

### Added
- VoxelPort relay network support.
- Discord verification flow.
- Room-code style client UI for legacy client mode.

### Security
- Hardened local config and auth cache handling.
- Added room code validation.
