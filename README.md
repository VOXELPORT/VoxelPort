<div align="center">

<img src="https://pr.opop.eu.org/Frame_2.png" width="100" alt="VoxelPort" />

# VoxelPort Fabric Mod

**Host your Fabric server without port forwarding.**
Install the mod and share a normal join address with vanilla players. No signup, no Discord, no token to copy — it just works.

[![Website](https://img.shields.io/badge/Website-voxelport.in-00FFB2?style=flat-square)](https://www.voxelport.in)
[![Status](https://img.shields.io/badge/Status-Page-00FFB2?style=flat-square)](https://www.voxelport.in/#/status)
[![License: MIT](https://img.shields.io/badge/License-MIT-00FFB2?style=flat-square)](https://github.com/VOXELPORT)

</div>

---

## Overview

VoxelPort connects a Fabric server or an Open to LAN singleplayer world to VoxelPort-operated relay infrastructure. Your game opens an outbound encrypted WebSocket connection, the relay assigns a public TCP port, and players join that address from vanilla Minecraft.

Players do not need to install the mod.

<p align="center">
  <img src="https://raw.githubusercontent.com/VOXELPORT/VoxelPort/main/docs/how-it-works.svg" alt="How VoxelPort connects a host to players through the relay" width="900" />
</p>

---

## Requirements

| Requirement | Version |
|---|---|
| Minecraft | 26.1, 26.1.1, 26.1.2, 26.2, 26.3 (one jar for all of 26.x) |
| Fabric Loader | 0.18.6+ |
| Java | 25+ |

A device token is generated automatically on first launch — nothing to request or paste.

---

## Quick Start

### Singleplayer world

1. Install the VoxelPort mod in your Fabric client.
2. Open Minecraft and load your singleplayer world.
3. Use Minecraft's **Open to LAN** button.
4. Press **Open to VoxelPort** from the pause menu.
5. Share the copied address: `play.voxelport.in:<port>` (India) or `play-eu.voxelport.in:<port>` (Europe). The closest relay is chosen for you.

Players joining your address do not need the mod.

### Dedicated server

1. Install Fabric Loader on your Minecraft server.
2. Put the VoxelPort mod JAR in your server's `mods/` folder.
3. Restart the server.
4. Run `/voxelport start`.
5. Share the address VoxelPort prints (it is also in `/voxelport status`).

---

## Commands

```mcfunction
/voxelport start
/voxelport start <port>
/voxelport status
/voxelport stop
```

`/voxelport start` uses the server's configured Minecraft port automatically. Use `/voxelport start <port>` only when you need to expose a different local port.

There is nothing to set up: VoxelPort creates a private device token for you on first launch.

---

## Configuration

The mod saves server settings at:

```text
config/voxelport/settings.properties
```

Example config:

```properties
server_token=vp_...        # auto-generated; do not share
public_host=play.voxelport.in
server_host=127.0.0.1
max_connections=200
relay_url=wss://relay.voxelport.in
```

Leave `relay_url` blank (or `wss://relay.voxelport.in`) and `public_host` as `play.voxelport.in` to use the official relays: the mod then picks India or Europe by latency. Setting either to something else turns that off and uses exactly what you configured.

**Using your own token:** set the `VOXELPORT_TOKEN` environment variable before starting the server. It takes priority over the saved token and is never written to disk. Most hosts log typed commands, which is why there is no command for setting a token.

---

## How It Works

1. The mod opens an outbound WebSocket to the relay.
2. It registers using its auto-generated device token.
3. The relay assigns a public TCP port.
4. Players join `play.voxelport.in:<assigned-port>` (India) or `play-eu.voxelport.in:<assigned-port>` (Europe) from vanilla Minecraft.
5. Minecraft traffic is bridged through the relay to your Fabric server.

The relay is a bridge, not a game server. Your world, mods, and player data stay on your own server.

---

## Troubleshooting

**Status shows disconnected**
Make sure your host allows outbound HTTPS/WSS traffic to `relay.voxelport.in` and `eu.voxelport.in` on port `443` (India is also tried directly on `direct.voxelport.in:26499`).

**Players cannot join**
Run `/voxelport status`, then make sure players are joining the public address shown there.

**Disconnects with compression or packet errors**
Update to the latest mod release. Server mode buffers fragmented WebSocket frames and serializes relay writes to prevent packet-stream corruption.

---

## Links

- [Website](https://www.voxelport.in)
- [Desktop app](https://www.voxelport.in) — a separate download that tunnels any local Minecraft server without a mod
- [Status Page](https://www.voxelport.in/#/status)
- [VoxelPort GitHub](https://github.com/VOXELPORT)

---

## License

MIT. See the included license file.

VoxelPort is not affiliated with Mojang, Microsoft, or Fabric.
