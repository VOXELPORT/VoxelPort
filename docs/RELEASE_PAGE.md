# VoxelPort

> ⚠️ **Discord membership required** — you must join the [VoxelPort Discord server](https://discord.com/invite/5Q6BRnJYHW) before using this mod. The bot sends a 6-digit code to your Discord DMs to verify your identity. No new account needed — just Discord.

> 🌐 **External connections** — this mod contacts `voxelportrelay.qzz.io` (bot server on port 2525) for Discord verification and session validation, and `wss://voxelportrelay.qzz.io/relay` for game traffic routing. Full details in the Privacy section below.

---

Host your Minecraft singleplayer world over the internet and invite friends with a **6-character room code** — no port forwarding, no router config, no VPN, no extra software.

```
You click "Open to VoxelPort"  →  code G0GI5Z copied to clipboard
Friend pastes G0GI5Z           →  connected in seconds
```

---

## Features

- **Relay network** — all traffic routes through VoxelPort's relay. Your home IP is never exposed to players joining your world.
- **Discord DM verification** — no new account creation. The VoxelPort bot sends you a 6-digit code via Discord DM. Enter it once, cached for 12 hours.
- **6-character room codes** — clean codes like `G0GI5Z`. No IP addresses, no long URLs.
- **Live relay ping** — tab list header shows your relay latency with color coding (green / yellow / red).
- **In-game settings screen** — override the relay URL from the multiplayer screen (⚙ button) without reinstalling the mod.
- **Machine-bound auth** — your verification is fingerprinted to your computer. Auth files can't be copied to another machine.
- **Per-session validation** — the bot checks your Discord server membership before every host or join.
- **Zero extra software** — no cloudflared, no agents, no separate apps. Just the mod JAR.

---

## Requirements

| | |
|---|---|
| Minecraft | `>=26 <27` (Java Edition) |
| Fabric Loader | `>=0.18.6` |
| Java | `>=25` |
| Side | **Client only** — the host does not run a dedicated server |
| Discord | Must be a member of the [VoxelPort Discord server](https://discord.com/invite/5Q6BRnJYHW) |

---

## How to Install

1. Install [Fabric Loader](https://fabricmc.net/use/installer/) `0.18.6` or newer
2. Join the [VoxelPort Discord server](https://discord.com/invite/5Q6BRnJYHW) — required for verification
3. Drop `voxelport-mod-1.2.0.jar` into your Minecraft `mods/` folder
4. Launch Minecraft with the Fabric profile

---

## How to Host

1. Open a singleplayer world
2. Open the pause menu → click **Open to VoxelPort**
3. Enter your Discord username when prompted
4. The VoxelPort bot sends you a **6-digit code via DM** — enter it in-game
5. Verification is cached for **12 hours** — you won't be asked again until it expires
6. A room code (e.g. `G0GI5Z`) is copied to your clipboard — send it to your friend

## How to Join

1. Open the multiplayer screen → click **Join via VoxelPort**
2. Paste the 6-character room code
3. Click **Connect**

---

## How It Works

```
HOST                       RELAY SERVER                  JOINER
────                       ────────────                  ──────
Open to VoxelPort  ──ws──▶ voxelportrelay.qzz.io ◀──ws── Paste room code
Get code: G0GI5Z   ◀───── assigns room           ──────▶ Bridge connected
Game traffic       ──────▶ proxied through relay  ──────▶ Receives packets
```

The relay is a stateless WebSocket bridge. It does not read, store, or inspect your game traffic — it only moves bytes between two connections.

---

## vs Other Solutions

| | VoxelPort | PlayIt.gg | Essential | Port Forward |
|---|---|---|---|---|
| Works from pause menu | ✅ | ❌ Separate app | ✅ | ❌ Router config |
| No new account | ✅ Discord only | ❌ PlayIt account | ❌ Essential account | ✅ |
| Your IP hidden | ✅ Always | ✅ Always | ❌ P2P exposes IP | ❌ Exposed |
| Open source | ✅ MIT | ❌ Closed | ❌ Closed | N/A |
| Free, no paid tiers | ✅ | ❌ Paid tiers | ❌ Paid cosmetics | ✅ |

**Honest limitation:** The relay currently runs on a single server in India. Players in South/Southeast Asia get excellent latency. Europe and North America will see higher relay overhead (100–300ms typical). We plan to expand to more regions as the project grows — check the [status page](https://voxelport.qzz.io/#/status) for current relay health.

---

## Privacy & Data

### What is stored locally on your device

All files are written inside `config/voxelport/` in your Minecraft directory. They never leave your machine except as described below.

| File | Contents | Retention |
|---|---|---|
| `discord_auth.properties` | Discord username, user ID, avatar hash, login timestamp, machine fingerprint | Expires after 12 hours |
| `machine.id` | A randomly generated UUID unique to your install | Until you delete it |
| `last_code.txt` | Your most recent room code | Overwritten each session |

### What is sent to the VoxelPort server

| When | Data sent | Why |
|---|---|---|
| Startup | None (anonymous GET to `/relay/url`) | Fetches current relay address |
| Verification step 1 | Your Discord username + machine ID | Bot finds your Discord account to send the DM |
| Verification step 2 | The 6-digit code + machine ID | Confirms your identity |
| Before hosting/joining | Your Discord user ID + machine ID | Checks you're still in the Discord server |
| During session | Raw Minecraft game packets only | Relayed to the other player — not inspected |

### What is NOT collected

- Passwords or email addresses
- Your IP address (visible to the relay server during connection as a technical requirement of TCP — not logged or stored)
- Minecraft chat, inventory, world data, or game state
- Crash reports or analytics of any kind

No data is sold or shared with third parties. Delete `config/voxelport/` to remove all locally stored data.

---

## Changelog

### v1.2.0
- In-game settings screen — override relay URL without reinstalling
- Live relay ping in the tab list with color coding
- Dynamic relay URL fetched from bot at startup
- Auth cache reduced from 2 days → 12 hours
- Auth is now machine-bound (SHA-256 fingerprint)
- Per-session Discord membership validation
- Relay URL XOR-encoded in JAR — not a plain readable string
- Bot endpoints properly secured with secret middleware

### v1.1.0
- Full VoxelPort rebrand (previously LocalMiner)
- Replaced Cloudflare tunnels with VoxelPort relay network
- Discord DM verification (no OAuth)
- Removed all cloudflared binaries from the JAR
- 6-character alphanumeric room codes

---

## Links

- [Discord](https://discord.com/invite/5Q6BRnJYHW) — get verified, get support, follow updates
- [GitHub](https://github.com/trazhub/VoxelPort) — full source code, MIT licensed
- [Status page](https://voxelport.qzz.io/#/status) — check relay health from your location
- [Sponsor](https://github.com/sponsors/trazhub) — help fund more relay regions

---

*VoxelPort is not affiliated with Mojang AB, Microsoft, the Fabric project, or Discord Inc. "Minecraft" is a trademark of Mojang AB.*
