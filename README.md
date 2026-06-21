# VoxelPort Fabric Mod

Host a Minecraft server over the internet without port forwarding. Server owners install the mod, connect it to the VoxelPort relay with a Discord-issued token, and players join a normal public address such as `play.voxelport.in:25590`.

## Current Status

- Minecraft: `>=26 <27`
- Fabric Loader: `>=0.18.6`
- Java: `>=25`
- Primary mode: dedicated Fabric server
- Legacy mode: client singleplayer room-code UI is still present, but server mode is the recommended path

## Server Install

1. Install Fabric Loader on your Minecraft server.
2. Put `voxelport-mod-X.X.X.jar` in the server `mods/` folder.
3. Start the server once.
4. In Discord, run `/gettoken` with the VoxelPort bot.
5. In the server console or as an operator, run:

```mcfunction
/voxelport token <token-from-discord-gettoken>
/voxelport start
```

`/voxelport start` uses the server's configured port automatically. To expose a different local port:

```mcfunction
/voxelport start <port>
```

## Commands

```mcfunction
/voxelport token <token>
/voxelport start
/voxelport start <port>
/voxelport status
/voxelport address
/voxelport stop
```

`/voxelport token` saves the token locally in `config/voxelport/settings.properties`. Treat that file like a password.

## How It Works

```text
Minecraft server -> VoxelPort relay -> public TCP port -> vanilla players
```

1. The mod registers with the relay using your server token.
2. The relay assigns a stable public port.
3. Players join `play.voxelport.in:<assigned-port>` from vanilla Minecraft.
4. The mod bridges raw Minecraft traffic between the relay and your local server port.

Players do not need the mod.

## Config

Config is stored at:

```text
config/voxelport/settings.properties
```

Useful keys:

```properties
server_token=vp_...
public_host=play.voxelport.in
server_host=127.0.0.1
max_connections=200
relay_url=wss://voxelport.in
```

Leave `relay_url` blank unless you are testing a custom relay.

## Recent Fixes

- Added dedicated server support through `/voxelport` commands.
- Switched server mode to the same token/public-port relay protocol used by the plugin.
- Fixed random compression disconnects by buffering fragmented WebSocket text frames before parsing.
- Serialized outgoing WebSocket writes so relay frames cannot overlap under load.
- Removed exposed client-side bot secrets from the mod source/JAR.
- Fixed the Windows Gradle wrapper so builds no longer pause at the end.

## Building

```bash
./gradlew build
```

Output:

```text
build/libs/voxelport-mod-X.X.X.jar
build/libs/voxelport-mod-X.X.X-sources.jar
```

On Windows:

```powershell
.\gradlew.bat build
```

## Troubleshooting

### `No VoxelPort token set`

Run `/gettoken` in Discord, then:

```mcfunction
/voxelport token <token>
/voxelport start
```

### `invalid distance too far back`

This points to a corrupted Minecraft packet stream. Update to the latest JAR from this repository; the server relay bridge now buffers fragmented WebSocket frames and serializes relay writes.

### Players cannot join

Run:

```mcfunction
/voxelport status
/voxelport address
```

Make sure players are joining the public address shown by `/voxelport address`, not your private server IP.

## License

MIT. VoxelPort is not affiliated with Mojang, Microsoft, Fabric, or Discord.
