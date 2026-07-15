> 🌐 **External connections** — this mod contacts `wss://relay.voxelport.in` (game traffic routing) and shows relay status from `voxelport.in`. See the Privacy section below.

---

**Host your Minecraft world or Fabric server over the internet. No port forwarding, no router config, no Discord, no signup.**

Install the mod → open the pause menu → click **Open to VoxelPort** → share the address. Your friend pastes `play.voxelport.in:<port>` on the multiplayer screen and connects. That's it. Players don't need the mod.

---

## Features

- **No accounts, no Discord** — a private device token is generated automatically on first launch. Nothing to request, verify, or paste
- **Relay network** — all traffic goes through VoxelPort's relay, so your home IP is never visible to players joining your world
- **Vanilla players** — anyone joins the public address from an unmodified Minecraft client
- **Live relay ping** — the tab list header shows relay latency with color coding (green / yellow / red)
- **In-game settings** — override the relay URL from the ⚙ button without reinstalling
- **Blocklist** — drop unwanted player IPs at the relay
- **Zero extra software** — no cloudflared, no separate apps. Just the JAR

---

## How to Use

**Singleplayer:**
1. Load your world → pause menu → **Open to LAN** → **Open to VoxelPort**
2. The public address is copied to your clipboard — send it to your friends

**Dedicated server:**
1. Drop the JAR in `mods/`, restart, run `/voxelport start`
2. Share the address from `/voxelport address`

---

## Requirements

- Minecraft `>=26 <27` · Fabric Loader `>=0.18.6` · Java `>=25`
- Works client-side (Open to LAN) or server-side (dedicated)

---

## Prefer an app?

The **VoxelPort desktop app** tunnels any local Minecraft server the same way, without installing a mod. Download it at [voxelport.in](https://www.voxelport.in).

---

## Privacy

**Stored locally** in `config/voxelport/` and never uploaded:
- An auto-generated device token (`vp_…`), encrypted at rest

**Sent to VoxelPort servers:**
- Your device token → used to authenticate your tunnel and assign a port
- Raw game packets during a session → proxied to the other player, never inspected

**Not collected:** passwords, email, Discord identity, chat, inventory, world data, analytics.

Full privacy policy: [voxelport.in/#/legal](https://www.voxelport.in/#/legal)

---

## Source & License

Open source under MIT — [github.com/VOXELPORT/VoxelPort](https://github.com/VOXELPORT/VoxelPort)

[Website](https://www.voxelport.in) · [Status](https://www.voxelport.in/#/status)

*Not affiliated with Mojang, Microsoft, Fabric, or Modrinth.*
