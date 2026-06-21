# VoxelPort Fabric Server Mod

Host your Fabric server without port forwarding. VoxelPort gives your server a public join address through VoxelPort relay infrastructure, while players connect from normal vanilla Minecraft.

Players do **not** need to install this mod.

![How VoxelPort works](https://traz.arge.in/how-it-works.gif)

## What VoxelPort Does

- Exposes a dedicated Fabric server without router setup or port forwarding.
- Uses a Discord-issued token from the VoxelPort bot.
- Assigns a public address like `play.voxelport.in:<assigned-port>`.
- Lets vanilla Minecraft clients join normally.
- Keeps your world, mods, config, and player data on your own server.

## Requirements

| Requirement | Version |
|---|---|
| Minecraft | 26.x |
| Fabric Loader | 0.18.6+ |
| Java | 25+ |
| Discord token | Free from the VoxelPort Discord |

## Quick Start

1. Install Fabric Loader on your Minecraft server.
2. Put the VoxelPort mod JAR in the server `mods/` folder.
3. Restart the server.
4. Join the VoxelPort Discord: https://discord.com/invite/5Q6BRnJYHW
5. Run `/gettoken` with the VoxelPort bot.
6. In the server console or as an operator, run:

```mcfunction
/voxelport token <token>
/voxelport start
```

7. Share the address shown by:

```mcfunction
/voxelport address
```

Keep your token secret. Treat it like a password for your relay slot.

## Commands

```mcfunction
/voxelport token <token>
/voxelport start
/voxelport start <port>
/voxelport status
/voxelport address
/voxelport stop
```

`/voxelport start` uses the server's configured Minecraft port automatically. Use `/voxelport start <port>` only when exposing a different local port.

## How It Works

1. The mod opens an outbound WebSocket connection to the relay.
2. It registers using your Discord-issued server token.
3. The relay validates the token and assigns a stable TCP port.
4. Players join `play.voxelport.in:<assigned-port>` from vanilla Minecraft.
5. Minecraft traffic is bridged through the relay to your Fabric server.

VoxelPort is a bridge, not a game server or hosting provider. Your actual Minecraft server still runs on your own machine or host.

## Troubleshooting

**No VoxelPort token set**  
Run `/gettoken` in Discord, then run `/voxelport token <token>` and `/voxelport start`.

**Players cannot join**  
Run `/voxelport address` and make sure players use the exact public address shown there.

**Status shows disconnected**  
Make sure your host allows outbound HTTPS/WSS traffic to `voxelport.in` on port `443`.

**Compression or packet disconnects**  
Update to the latest VoxelPort release. Current server mode buffers fragmented WebSocket frames and serializes relay writes to prevent packet stream corruption.

## Links

- Discord: https://discord.com/invite/5Q6BRnJYHW
- Website: https://www.voxelport.in
- Status page: https://www.voxelport.in/#/status
- GitHub: https://github.com/VOXELPORT/VoxelPort

VoxelPort is not affiliated with Mojang, Microsoft, Fabric, CurseForge, Modrinth, or Discord.
