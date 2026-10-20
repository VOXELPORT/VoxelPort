<div align="center">

<img src="https://raw.githubusercontent.com/VOXELPORT/VoxelPort/main/docs/banner.svg" alt="VoxelPort Fabric Mod: open your Minecraft world to friends in one click" width="100%" />

# VoxelPort Fabric Mod

**Open your Minecraft world or Fabric server to friends in one click. No port forwarding, no account.**

[![Download](https://img.shields.io/github/v/release/VOXELPORT/VoxelPort?style=flat-square&label=Download&color=00FFB2)](https://github.com/VOXELPORT/VoxelPort/releases/latest)
[![Website](https://img.shields.io/badge/Website-voxelport.in-00FFB2?style=flat-square)](https://voxelport.in)
[![Status](https://img.shields.io/badge/Relay-Status-00FFB2?style=flat-square)](https://voxelport.in/status)
[![License: MIT](https://img.shields.io/badge/License-MIT-00FFB2?style=flat-square)](LICENSE)

</div>

---

## What it does

VoxelPort connects a Fabric server, or a singleplayer world opened to LAN, to a VoxelPort relay. Your game opens an outbound encrypted connection, the relay hands you a public address, and your friends join it from plain Minecraft. **Players do not need the mod.**

Because nothing has to come *in* to your router, it works where port forwarding doesn't: CGNAT, mobile data, hostel and campus Wi-Fi, and many ISPs.

<p align="center">
  <img src="https://raw.githubusercontent.com/VOXELPORT/VoxelPort/main/docs/how-it-works.svg" alt="How VoxelPort connects a host to players through the relay" width="900" />
</p>

**Two relay regions.** India (`play.voxelport.in`) and Europe (`play-eu.voxelport.in`). The mod measures both and uses the closer one, so you don't choose anything.

---

## Requirements

| | |
|---|---|
| Minecraft | 26.1, 26.1.1, 26.1.2, 26.2 or 26.3 (the same jar works on all of them) |
| Fabric Loader | 0.18.6 or newer |
| Java | 25 or newer |

Only the **host** installs the mod. There is no sign-up and no token to copy: a private device token is created for you on first use.

---

## Quick start

### Singleplayer world

1. Download the latest `VoxelPort-<version>.jar` from the [Releases page](https://github.com/VOXELPORT/VoxelPort/releases/latest) and put it in your Fabric client's `mods` folder.
2. Start Minecraft, open your world, and use **Open to LAN**.
3. Press **Escape**, then **Open to VoxelPort**.
4. The address is copied to your clipboard. Send it to your friends.

The pause-menu button changes to **VoxelPort: Live**. Click it to see the address, uptime and connected players. The relay ping shows in the player-list header (Tab).

### Dedicated server

1. Install Fabric Loader on your server and put the mod jar in `mods/`.
2. Start the server and run `/voxelport start`.
3. Share the address it prints.

---

## Commands

`/voxelport` can also be typed as `/voxel` or `/vp`. They need game-master permissions (operator level 2).

| Command | What it does |
|---|---|
| `/voxelport start` | Go public using the server's own port |
| `/voxelport start <port>` | Go public with a different local port |
| `/voxelport status` | Show the address, uptime, players and relay ping |
| `/voxelport stop` | Stop sharing |

---

## Configuration

Most people never need this. Settings live in `config/voxelport/settings.properties`:

```properties
server_token=vp_...        # created for you; keep it private
public_host=play.voxelport.in
server_host=127.0.0.1
max_connections=200
relay_url=wss://relay.voxelport.in
```

With `relay_url` blank (or the default) and `public_host` left as `play.voxelport.in`, the mod picks India or Europe for you. If you set either to something else, for example a self-hosted relay, the mod uses exactly what you wrote and does no region picking.

**Your own token:** set the `VOXELPORT_TOKEN` environment variable before starting the server. It overrides the saved token and is never written to disk. There is deliberately no command for this, because most hosts log typed commands.

---

## How it works

1. The mod opens an outbound WebSocket to the nearest relay.
2. It registers with its device token and the relay assigns a public TCP port.
3. Players join `play.voxelport.in:<port>` or `play-eu.voxelport.in:<port>` from vanilla Minecraft.
4. The relay passes the traffic to your server. It is a bridge, not a game server: your world and player data stay with you.

If the relay's operator announces something (for example a planned restart), it appears on the Live screen and in the server log.

---

## Good to know

- **Your game has to stay open.** The address works while your world or server is running. For a server that never sleeps, use a paid host.
- **No uptime guarantee.** The relays are run by one student. Check the [status page](https://voxelport.in/status) if something looks wrong.
- **Not affiliated with Mojang or Microsoft.**

---

## Troubleshooting

**"Open to VoxelPort" doesn't connect**
Allow outbound HTTPS/WSS to `relay.voxelport.in` and `eu.voxelport.in` on port 443. India is also tried directly on `direct.voxelport.in:26499`.

**Friends can't join**
Check that they use the exact address shown on the Live screen (or `/voxelport status`) and that your world or server is still running.

**The game crashes on start after a Minecraft update**
Use the newest mod release. Each Minecraft version can move things the mod hooks into, so a new version may need an update.

---

## More from VoxelPort

- [Website](https://voxelport.in) and [relay status](https://voxelport.in/status)
- **VoxelPort app:** hosts any Minecraft server (Java and Bedrock, modpacks, friends-only) without a mod. Windows, via the [Microsoft Store](https://apps.microsoft.com/detail/9NGRX9CFNBD6) or [GitHub](https://github.com/VOXELPORT/VoxelPort-App/releases/latest), and Android from the website.
- [Discord](https://discord.gg/JAUfmey2fP)

---

## License

MIT. See [LICENSE](LICENSE).
