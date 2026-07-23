# VoxelPort

**Host your Minecraft world or Fabric server over the internet — no port forwarding, no router config, no signup.**

Install the mod, use Minecraft's **Open to LAN**, then click **Open to VoxelPort**. You get a public `play.voxelport.in:<port>` address to share. Friends join from **vanilla Minecraft** — they don't need the mod installed.

> ⚙️ **This mod requires VoxelPort's relay service.** It routes your game traffic through VoxelPort-operated relay servers (`wss://relay.voxelport.in`) so players can connect without you opening ports or exposing your home IP. It is not a standalone/offline LAN tool — an internet connection to the relay is required for it to work.

---

## Features

- **No accounts, no Discord** — a private device token is generated automatically on first launch. Nothing to request, verify, or paste.
- **Vanilla players** — anyone joins the public address from an unmodified Minecraft client.
- **Singleplayer or dedicated server** — works client-side (Open to LAN) or on a Fabric server (`/voxelport start`).
- **Live relay ping** — the tab-list header shows relay latency, color-coded (green / yellow / red).
- **In-game settings** — change the relay URL from the ⚙ button without reinstalling.
- **IP blocklist** — drop unwanted player connections at the relay.

---

## How to use

**Singleplayer world**
1. Load your world → pause menu → **Open to LAN** → **Open to VoxelPort**.
2. The public address is copied to your clipboard — send it to your friends.

**Dedicated server**
1. Put the JAR in your server's `mods/` folder and restart.
2. Run `/voxelport start`, then share the address from `/voxelport address`.

Players joining your address do **not** need the mod.

---

## Commands

```
/voxelport start [port]
/voxelport status
/voxelport address
/voxelport stop
```

`/voxelport start` uses the server's configured port automatically; pass a port only to expose a different local one.

---

## Requirements

- Minecraft `26.x` · Fabric Loader `0.18.6+` · Java `25+`
- Outbound HTTPS/WSS to `relay.voxelport.in` on port `443`

---

## Privacy

**Stored locally** (never uploaded): an auto-generated device token (`vp_…`).

**Sent to the relay:** your device token (to authenticate the tunnel and assign a port) and raw game packets during a session (proxied to the other player, never inspected).

**Not collected:** passwords, email, Discord identity, chat, inventory, world data, or analytics.

Full privacy policy: <https://voxelport.in/#/legal>

---

## Source & license

Open source under the MIT license — <https://github.com/VOXELPORT/VoxelPort>

*Not affiliated with Mojang, Microsoft, Fabric, CurseForge, or Modrinth.*

---

### Also from VoxelPort — separate downloads, not available on Modrinth

If you'd rather not install a mod, the **VoxelPort desktop app** tunnels any local Minecraft server the same way. It is a separate product distributed at [voxelport.in](https://voxelport.in) and is **not** part of this Modrinth project.
