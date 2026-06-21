<div align="center">

<img src="https://pr.opop.eu.org/Frame_2.png" width="100" alt="VoxelPort" />

# VoxelPort Fabric Server Mod

**Host your Fabric server without port forwarding.**  
Install the server mod, add your Discord-issued token, and share a normal join address with vanilla players.

[![Discord](https://img.shields.io/badge/Discord-Join-5865f2?style=flat-square&logo=discord&logoColor=white)](https://discord.gg/Fbqx76j5US)
[![Website](https://img.shields.io/badge/Website-voxelport.in-00FFB2?style=flat-square)](https://www.voxelport.in)
[![Status](https://img.shields.io/badge/Status-Page-00FFB2?style=flat-square)](https://www.voxelport.in/#/status)
[![License: MIT](https://img.shields.io/badge/License-MIT-00FFB2?style=flat-square)](https://github.com/VOXELPORT)

</div>

---

## Overview

VoxelPort connects your dedicated Fabric server to VoxelPort-operated relay infrastructure. Your server opens an outbound encrypted WebSocket connection, the relay assigns a public TCP port, and players join that address from vanilla Minecraft.

Players do not need to install the mod.

<p align="center">
  <img src="https://traz.arge.in/how-it-works.gif" alt="Animated VoxelPort relay flow" width="900" />
</p>

---

## Requirements

| Requirement | Version |
|---|---|
| Minecraft | 26.x |
| Fabric Loader | 0.18.6+ |
| Java | 25+ |
| Token | Free from the VoxelPort Discord |

---

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

---

## Commands

```mcfunction
/voxelport token <token>
/voxelport start
/voxelport start <port>
/voxelport status
/voxelport address
/voxelport stop
```

`/voxelport start` uses the server's configured Minecraft port automatically. Use `/voxelport start <port>` only when you need to expose a different local port.

---

## Configuration

The mod saves server settings at:

```text
config/voxelport/settings.properties
```

Example config:

```properties
server_token=vp_...
public_host=play.voxelport.in
server_host=127.0.0.1
max_connections=200
relay_url=wss://voxelport.in
```

Leave `relay_url` blank or set to `wss://voxelport.in` unless you are testing with VoxelPort support.

---

## How It Works

1. The mod opens an outbound WebSocket to the relay.
2. It registers using your Discord-issued server token.
3. The relay validates the token and assigns a stable TCP port.
4. Players join `play.voxelport.in:<assigned-port>` from vanilla Minecraft.
5. Minecraft traffic is bridged through the relay to your Fabric server.

The relay is a bridge, not a game server. Your world, mods, and player data stay on your own server.

The relay service and Discord bot are VoxelPort-operated infrastructure. Their internal implementation details are not documented as public source components here.

---

## Troubleshooting

**No VoxelPort token set**  
Run `/gettoken` in Discord, then run:

```mcfunction
/voxelport token <token>
/voxelport start
```

**Status shows disconnected**  
Make sure your host allows outbound HTTPS/WSS traffic to `voxelport.in` on port `443`.

**Players cannot join**  
Run `/voxelport status` or `/voxelport address`, then make sure players are joining the public address shown there.

**Token rejected**  
Run `/revoketoken` and `/gettoken` again in Discord, save the new token with `/voxelport token <token>`, then run `/voxelport start`.

**Disconnects with compression or packet errors**  
Update to the latest mod release. Current server mode buffers fragmented WebSocket frames and serializes relay writes to prevent packet-stream corruption.

---

## Links

- [Discord](https://discord.gg/Fbqx76j5US)
- [Website](https://www.voxelport.in)
- [Status Page](https://www.voxelport.in/#/status)
- [Join Us](https://www.voxelport.in/#/join)
- [VoxelPort GitHub](https://github.com/VOXELPORT)

---

## License

MIT. See the included license file.

VoxelPort is not affiliated with Mojang, Microsoft, Fabric, CurseForge, Modrinth, or Discord.
