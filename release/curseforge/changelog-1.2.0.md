## VoxelPort 1.2.0

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
