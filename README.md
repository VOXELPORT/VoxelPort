<div align="center">

<img src="src/main/resources/assets/voxelport/icon.png" width="96" alt="VoxelPort" />

# VoxelPort

**Host Minecraft worlds over the internet with a 6-character room code.**  
No port forwarding. No VPN. No router config. Just play.

[![Minecraft](https://img.shields.io/badge/Minecraft-26.x-62b47a?style=flat-square&logo=minecraft&logoColor=white)](https://minecraft.net)
[![Fabric](https://img.shields.io/badge/Fabric-0.18.6+-dbb997?style=flat-square)](https://fabricmc.net)
[![Java](https://img.shields.io/badge/Java-25+-e76f00?style=flat-square&logo=openjdk&logoColor=white)](https://adoptium.net)
[![License: MIT](https://img.shields.io/badge/License-MIT-00FFB2?style=flat-square)](LICENSE)
[![Discord](https://img.shields.io/badge/Discord-Join-5865f2?style=flat-square&logo=discord&logoColor=white)](https://discord.gg/dYXqe6tvSN)

[Download](#install) · [How It Works](#how-it-works) · [Discord](https://discord.gg/dYXqe6tvSN) · [Status](https://voxelport.qzz.io/#/status)

</div>

---

## What is VoxelPort?

VoxelPort is a **Fabric client mod** that lets you share your Minecraft singleplayer world with friends over the internet using a short room code — the same way you'd share a Wi-Fi password. It handles everything through a relay network so neither player needs to touch their router.

```
You press "Open to VoxelPort"  →  code G0GI5Z copied to clipboard
Friend pastes G0GI5Z           →  connected in seconds
```

---

## Features

| Feature | Description |
|---|---|
| **Relay network** | Traffic routes through VoxelPort's relay — your home IP is never exposed |
| **Discord verification** | One-time DM verification — no new accounts, no OAuth |
| **6-character room codes** | Clean alphanumeric codes like `G0GI5Z`. No IP addresses. |
| **Live relay ping** | Tab list header shows relay latency with color coding |
| **In-game settings** | Override the relay server URL from the multiplayer screen (⚙ button) |
| **Machine-bound auth** | Auth cache fingerprinted to your computer — can't be copied between machines |
| **Per-session validation** | Bot checks your Discord membership before every host or join |
| **Zero dependencies** | No extra software, no cloudflared, no separate apps |

---

## Requirements

- **Minecraft:** `>=26 <27` (Java Edition)
- **Fabric Loader:** `>=0.18.6`
- **Java:** `>=25`
- **Environment:** Client only — your friends do not need a dedicated server
- **Discord:** You must be a member of the [VoxelPort Discord server](https://discord.gg/dYXqe6tvSN)

> VoxelPort is a **client-side mod**. The person hosting does not run a server — they share their singleplayer world directly.

---

## Install

1. Install [Fabric Loader](https://fabricmc.net/use/installer/) `0.18.6` or newer
2. Download the latest `voxelport-mod-X.X.X.jar` from [Releases](https://github.com/trazhub/VoxelPort/releases)
3. Place the JAR in your Minecraft `mods/` folder
4. Launch Minecraft with the Fabric profile
5. Join the [VoxelPort Discord server](https://discord.gg/dYXqe6tvSN) — required for verification

---

## How to Host

1. Open a singleplayer world
2. Open the **pause menu** (Esc)
3. Click **Open to VoxelPort**
4. If it's your first time, enter your Discord username — the bot will DM you a 6-digit code
5. Enter the code in-game → verification cached for 12 hours
6. A room code (e.g. `G0GI5Z`) is copied to your clipboard
7. Send the code to your friend

---

## How to Join

1. Open the **multiplayer screen**
2. Click **Join via VoxelPort**
3. Paste the 6-character room code
4. Click **Connect**

---

## How It Works

```
HOST                          RELAY                        JOINER
────                          ─────                        ──────
Open to VoxelPort    ──ws──▶  voxelportrelay.qzz.io  ◀──ws──  Paste room code
Get code: G0GI5Z     ◀──────  assigns room            ──────▶  Connect to room
Game traffic         ──────▶  proxy bridge            ──────▶  Receives packets
```

1. **Host** connects to the relay via WebSocket and requests a room code
2. **Relay** assigns a 6-character code and holds the connection open
3. **Joiner** connects to the relay with the same code — relay bridges the two sockets
4. **Minecraft packets** flow through the bridge — the relay never reads game content
5. Both sides disconnect when the session ends

The relay is a stateless WebSocket bridge. It does not store or inspect game traffic.

---

## Discord Verification

VoxelPort uses Discord DM verification to gate access to the relay. This prevents anonymous abuse of the relay network.

**Flow:**
1. You enter your Discord username in-game
2. The VoxelPort bot searches the Discord server for your account
3. Bot sends you a 6-digit code via DM
4. You enter the code in-game — you're verified
5. Verification is cached locally for **12 hours**

**What is stored locally** (in `config/voxelport/`):

| File | Contents |
|---|---|
| `discord_auth.properties` | Discord username, user ID, avatar hash, timestamp, machine fingerprint |
| `machine.id` | Random UUID unique to your install |
| `last_code.txt` | Most recent room code (convenience only) |

No passwords, no email, no game data. [Full privacy policy →](https://voxelport.qzz.io/#/legal)

---

## Relay Infrastructure

The VoxelPort relay runs on a single server in **India** (`voxelportrelay.qzz.io`).

| Region | Expected overhead |
|---|---|
| South Asia, Southeast Asia | < 80ms — excellent |
| Middle East, East Asia | 80–150ms — good |
| Europe | 150–250ms — fair |
| North America | 250–400ms — noticeable |
| South America, Australia | > 400ms — high |

We plan to add relay nodes in Europe and North America as the project grows. [Check current relay status →](https://voxelport.qzz.io/#/status)

---

## Building from Source

```bash
git clone https://github.com/trazhub/VoxelPort.git
cd VoxelPort
./gradlew build
```

Output: `build/libs/voxelport-mod-X.X.X.jar`

### Publishing (maintainers only)

Set environment variables, then run:

```bash
export MODRINTH_TOKEN=...
export MODRINTH_PROJECT_ID=...
export CURSEFORGE_TOKEN=...
export CURSEFORGE_PROJECT_ID=...
export RELEASE_CHANGELOG="..."

./gradlew publishAll
```

Or push a tag — the GitHub Actions workflow handles everything automatically:

```bash
git tag v1.2.0
git push origin v1.2.0
```

---

## Contributing

VoxelPort welcomes contributors. See the [Join Us page](https://voxelport.qzz.io/#/join) for open roles.

**Quick start:**
1. Fork the repo
2. Make your changes
3. Open a pull request against `main`

Bug reports and feature requests go in [Issues](https://github.com/trazhub/VoxelPort/issues).

---

## vs PlayIt.gg / Essential

| | VoxelPort | PlayIt.gg | Essential |
|---|---|---|---|
| Works inside Minecraft | ✓ Pause menu | ✗ Separate app | ✓ But needs account |
| No new account | ✓ Discord only | ✗ PlayIt account | ✗ Essential account |
| Open source | ✓ MIT | ✗ Closed | ✗ Closed |
| IP hidden from friends | ✓ Always | ✓ Always | ✗ P2P exposes IP |
| Paid features | ✗ Free forever | ✓ Paid tiers | ✓ Paid cosmetics |
| Relay regions | India (1 node) | Global | N/A (P2P) |

---

## Changelog

See [CHANGELOG.md](CHANGELOG.md) for full version history.

**Latest: v1.2.0**
- In-game relay URL settings screen
- Live relay ping in tab list
- Machine-bound auth cache
- Per-session bot validation
- Security hardening

---

## License

MIT — see [LICENSE](LICENSE).

VoxelPort is not affiliated with Mojang, Microsoft, Fabric, or Discord.  
"Minecraft" is a trademark of Mojang AB.

---

<div align="center">

Built by [trazhub](https://github.com/trazhub) · [Discord](https://discord.gg/dYXqe6tvSN) · [Sponsor](https://github.com/sponsors/trazhub)

</div>
