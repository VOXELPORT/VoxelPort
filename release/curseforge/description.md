<div align="center">

<img src="https://pr.opop.eu.org/Frame_2.png" width="100" alt="VoxelPort" />

# VoxelPort Fabric Server Mod

**Host your Fabric server without port forwarding.**  
Install the server mod, add your Discord-issued token, and share a normal join address with vanilla players.

[Discord](https://discord.gg/Fbqx76j5US) | [Website](https://www.voxelport.in) | [Status Page](https://www.voxelport.in/#/status) | [GitHub](https://github.com/VOXELPORT/VoxelPort)

</div>

## Overview

VoxelPort connects your dedicated Fabric server to VoxelPort-operated relay infrastructure. Your server opens an outbound encrypted WebSocket connection, the relay assigns a public TCP port, and players join that address from vanilla Minecraft.

Players do not need to install the mod.

<p align="center">
  <img src="https://traz.arge.in/how-it-works.gif" alt="Animated VoxelPort relay flow" width="900" />
</p>

## Requirements

| Requirement | Version |
|---|---|
| Minecraft | 26.x |
| Fabric Loader | 0.18.6+ |
| Java | 25+ |
| Token | Free from the VoxelPort Discord |

## Quick Start

1. Install Fabric Loader on your Minecraft server.
2. Join the [VoxelPort Discord](https://discord.gg/Fbqx76j5US).
3. Run `/gettoken` with the VoxelPort bot.
4. Put the VoxelPort mod JAR in your server's `mods/` folder.
5. Restart the server.
6. In the server console or as an operator, run `/voxelport token <token>`.
7. Run `/voxelport start`.
8. Share the address shown by `/voxelport address`.

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

## How It Works

1. The mod opens an outbound WebSocket to the relay.
2. It registers using your Discord-issued server token.
3. The relay validates the token and assigns a stable TCP port.
4. Players join `play.voxelport.in:<assigned-port>` from vanilla Minecraft.
5. Minecraft traffic is bridged through the relay to your Fabric server.

The relay is a bridge, not a game server. Your world, mods, and player data stay on your own server.

VoxelPort is not affiliated with Mojang, Microsoft, Fabric, CurseForge, Modrinth, or Discord.
