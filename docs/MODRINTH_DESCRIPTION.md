> ⚠️ **Requires Discord membership** — you must join the [VoxelPort Discord server](https://discord.com/invite/5Q6BRnJYHW) to use this mod. The bot sends a 6-digit code to your DMs to verify your identity. No new account needed.

> 🌐 **External connections** — this mod contacts `voxelportrelay.qzz.io:2525` (Discord verification & session checks) and `wss://voxelportrelay.qzz.io/relay` (game traffic routing). See the Privacy section below.

---

**Host your Minecraft singleplayer world over the internet with a 6-character room code. No port forwarding, no router config, no extra software.**

Open the pause menu → click **Open to VoxelPort** → verify once with Discord → share the code. Your friend pastes it on the multiplayer screen and connects. That's it.

---

## Features

- **Relay network** — all traffic goes through VoxelPort's relay. Your home IP is never visible to players joining your world
- **Discord DM verification** — the bot sends a 6-digit code to your DMs. Enter it once, cached for 12 hours. No account creation needed
- **6-character room codes** — clean codes like `G0GI5Z`. No IP addresses, no long URLs
- **Live relay ping** — tab list header shows relay latency with color coding (green / yellow / red)
- **In-game settings** — override the relay URL from the multiplayer screen (⚙ button) without reinstalling
- **Machine-bound auth** — verification is tied to your computer, auth files can't be copied to another machine
- **Per-session validation** — bot checks your Discord membership before every host or join
- **Zero extra software** — no cloudflared, no separate apps. Just the JAR

---

## How to Use

**Hosting:**
1. Open a singleplayer world → pause menu → **Open to VoxelPort**
2. Enter your Discord username → bot DMs you a 6-digit code → enter it in-game
3. Room code is copied to clipboard — send it to your friend

**Joining:**
1. Multiplayer screen → **Join via VoxelPort**
2. Paste the room code → **Connect**

---

## Requirements

- Minecraft `>=26 <27` · Fabric Loader `>=0.18.6` · Java `>=25`
- **Client side only** — no server installation needed
- Must be a member of the [VoxelPort Discord server](https://discord.com/invite/5Q6BRnJYHW)

---

## Relay Server

The relay currently runs on **one server in India**. Expected latency overhead:

- 🟢 South/Southeast Asia — under 80ms
- 🟡 Europe / Middle East — 100–250ms
- 🟠 North America — 250–400ms

Check live relay health: [voxelport.qzz.io/status](https://voxelport.qzz.io/#/status)

We plan to add more regions as the project grows. [Support the project →](https://github.com/sponsors/trazhub)

---

## Privacy

**Stored locally** in `config/voxelport/` — never uploaded except as listed below:
- Discord username, user ID, avatar hash, login timestamp, machine fingerprint — cached for 12 hours
- A randomly generated machine UUID — stays on your computer

**Sent to VoxelPort servers:**
- Your Discord username + machine ID → bot uses this to find and DM you
- Your Discord user ID + machine ID → checked before each session (membership validation)
- Raw game packets during session → proxied to the other player, never inspected

**Not collected:** passwords, email, chat, inventory, world data, analytics, crash reports.

Full privacy policy: [voxelport.qzz.io/legal](https://voxelport.qzz.io/#/legal)

---

## Source & License

Open source under MIT — [github.com/trazhub/VoxelPort](https://github.com/trazhub/VoxelPort)

[Discord](https://discord.com/invite/5Q6BRnJYHW) · [Status](https://voxelport.qzz.io/#/status) · [Sponsor](https://github.com/sponsors/trazhub)

*Not affiliated with Mojang, Microsoft, Fabric, or Discord.*
