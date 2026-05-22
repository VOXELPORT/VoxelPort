# VoxelPort v1.2.0

Host and join Minecraft singleplayer worlds over the internet — no port forwarding, no router config needed. VoxelPort handles the connection through its relay network.

## Requirements

- Minecraft: `>=26 <27`
- Fabric Loader: `>=0.19.2`
- Java: `>=25`
- Environment: Client only
- **Discord:** You must be a member of the [VoxelPort Discord server](https://discord.gg/dYXqe6tvSN) to use this mod

## How To Install

1. Install Minecraft with Fabric Loader `0.19.2` or newer
2. Put `voxelport-mod-1.2.0.jar` in your Minecraft `mods` folder
3. Start Minecraft with the Fabric profile

## How To Host

1. Open a singleplayer world
2. Open the pause menu
3. Click **Open to VoxelPort**
4. Verify your Discord account when prompted (one-time, re-verified every 12 hours)
5. A 6-character room code is copied to your clipboard automatically — send it to whoever wants to join

## How To Join

1. Open the multiplayer screen
2. Click **Join via VoxelPort**
3. Paste the 6-character room code
4. Click **Connect**

## What's New in 1.2.0

- **Settings screen** — set a custom relay server URL from inside Minecraft (⚙ button on the multiplayer screen)
- **Live relay ping** shown in the tab list header with color-coded latency (green / yellow / red)
- **Dynamic relay URL** — fetched from the VoxelPort server at startup so the relay can change without a mod update
- Discord auth cache reduced from 2 days to **12 hours**
- Auth cache is now **machine-bound** — auth files can't be copied between computers
- Per-session bot validation before every host or join attempt
- Relay URL no longer visible as a plain string in the JAR
- Bot endpoints properly secured

## Privacy & Data Notice

VoxelPort stores the following **locally on your device** (in your Minecraft config folder):

| File | Contents | Purpose |
|------|----------|---------|
| `discord_auth.properties` | Discord username, user ID, avatar hash, login timestamp, machine fingerprint | Avoids re-verifying every session |
| `machine.id` | A randomly generated UUID unique to your install | Prevents auth files being shared between computers |
| `last_code.txt` | The most recent room code | Convenience only |

**What is sent to the VoxelPort server:**

- Your Discord username (to look you up in the server and send a DM code)
- Your Discord user ID + machine ID (to validate your session before hosting/joining)

**What is NOT collected:** passwords, email addresses, IP addresses, game data, chat logs.

No data is sold or shared with third parties. You can delete all stored files by removing the `voxelport` folder from your Minecraft config directory.

## Important Notes

- Hosting requires an internet connection
- You must be a member of the VoxelPort Discord server — the bot sends you a 6-digit DM code to verify your identity
- Room codes should only be shared with people you trust
- The relay server address can be changed in the ⚙ settings without reinstalling the mod

## Links

- Discord: https://discord.gg/dYXqe6tvSN
- Source: https://github.com/voxelport/voxelport-mod
- License: MIT
