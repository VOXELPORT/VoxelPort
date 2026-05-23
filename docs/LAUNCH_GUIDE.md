# VoxelPort v1.2.0 — Public Launch Guide

Everything you need to publish this mod correctly across every platform.
Follow the steps in order — GitHub first, then Modrinth, then CurseForge.

---

## Pre-Launch Checklist

Run through this before touching any platform.

- [ ] `build/libs/voxelport-mod-1.2.0.jar` exists and is the correct build
- [ ] `build/libs/voxelport-mod-1.2.0-sources.jar` exists
- [ ] You have tested the mod in-game: verify, host, and join all work
- [ ] The VPS bot is running the latest `notify.js` (with `/relay/url`, `/session/validate`, CORS)
- [ ] `http://voxelportrelay.qzz.io:2525/relay/url` returns `{"url":"wss://voxelportrelay.qzz.io/relay"}`
- [ ] The relay is online: `wss://voxelportrelay.qzz.io/relay` accepts WebSocket connections
- [ ] You have a Modrinth account at modrinth.com
- [ ] You have a CurseForge author account at curseforge.com
- [ ] You have a GitHub account and the repo `trazhub/VoxelPort` exists with the code pushed

---

## Part 1 — GitHub

### Step 1: Push your code

Make sure everything is committed and pushed to `main`:

```bash
git add .
git commit -m "Release v1.2.0"
git push origin main
```

### Step 2: Create the release tag

This triggers the GitHub Actions workflow which auto-publishes to Modrinth and CurseForge
(after you set up the secrets — see Part 2 and Part 3).

```bash
git tag v1.2.0
git push origin v1.2.0
```

### Step 3: Add GitHub Secrets (one-time setup)

Go to: `github.com/trazhub/VoxelPort` → Settings → Secrets and variables → Actions → New repository secret

| Secret name | Where to get it |
|---|---|
| `MODRINTH_TOKEN` | modrinth.com → Settings → API Access → Generate PAT (scope: `CREATE_VERSION`) |
| `MODRINTH_PROJECT_ID` | From your Modrinth project URL after creating it — e.g. `AABBccdd` |
| `CURSEFORGE_TOKEN` | curseforge.com → Account Settings → API Keys → Generate |
| `CURSEFORGE_PROJECT_ID` | From your CurseForge project URL after creating it — numeric ID |

> **Note:** Create your Modrinth and CurseForge project pages FIRST (Parts 2 and 3),
> get their IDs, add the secrets, THEN push the tag. Or push the tag after adding secrets
> and the workflow will auto-run.

### Step 4: What the GitHub Release looks like

The workflow auto-creates the release with the changelog pulled from `CHANGELOG.md`.
It attaches both JARs automatically. After the tag push, go to:
`github.com/trazhub/VoxelPort/releases` and verify it looks correct.

**The GitHub release title:** `VoxelPort v1.2.0`

**What GitHub release body should contain** (auto-generated from CHANGELOG.md):

```markdown
### Added
- Settings screen — set a custom relay server URL from inside Minecraft (⚙ button on the multiplayer screen)
- Live relay ping shown in the tab list header with color-coded latency (green/yellow/red)
- Relay URL fetched dynamically from the VoxelPort bot at startup — relay server can change without a mod update

### Changed
- Discord verification cache reduced from 2 days to 12 hours
- Auth cache is now machine-bound (SHA-256 fingerprint) — auth files can no longer be shared between computers

### Fixed
- Per-session Discord validation was defined but never called — now correctly fires before hosting and joining

### Security
- Per-session Discord validation against the bot before every host or join
- Relay URL XOR-encoded in the JAR — no longer visible as a plain string
- Bot endpoints now properly protected by BOT_SECRET middleware
```

**Files attached to release:**
- `voxelport-mod-1.2.0.jar` ← the one players install
- `voxelport-mod-1.2.0-sources.jar` ← for developers only

**What NOT to put in the GitHub release:**
- Don't put the BOT_SECRET value anywhere
- Don't mention the raw IP `92.4.70.103`
- Don't paste screenshots of your VPS terminal or `.env` file
- Don't include `voxelport-mod-1.1.0.jar` or older JARs — one version per release

---

## Part 2 — Modrinth

### Step 1: Create the project

Go to `modrinth.com/dashboard/projects` → New project

**Basic info:**
| Field | Value |
|---|---|
| Project name | `VoxelPort` |
| Project slug | `voxelport` (or whatever is available) |
| Project type | `Mod` |
| Summary (short) | `Host Minecraft worlds over the internet with a 6-character code. No port forwarding, no VPN — just play.` |
| Visibility | Public |

**Categories/tags to select:**
- `Multiplayer`
- `Utility`
- `Social`

**License:** MIT

**External links:**
- Source code: `https://github.com/trazhub/VoxelPort`
- Discord invite: `https://discord.gg/EuDMWUuGpp`
- Wiki / Docs: (leave blank for now)

### Step 2: The Modrinth project description

Copy this exactly into the description editor. Modrinth supports Markdown.

---

```markdown
# VoxelPort

Host your Minecraft singleplayer world over the internet and invite friends with a **6-character room code** — no port forwarding, no router config, no VPN.

> **Requires:** Fabric Loader · Minecraft 26.x · Java 25+  
> **Side:** Client only  
> ⚠️ You must join the [VoxelPort Discord server](https://discord.gg/EuDMWUuGpp) to use this mod — identity verification is done via Discord DM.

---

## How it works

1. Open a singleplayer world
2. Open the pause menu → click **Open to VoxelPort**
3. Verify your Discord account (one-time — bot sends you a 6-digit DM code)
4. A room code like `G0GI5Z` is copied to your clipboard
5. Share it with your friends — they click **Join via VoxelPort** on the multiplayer screen, paste the code, and connect

No port forwarding. No static IP. No extra software.

---

## Features

- **Relay network** — traffic is routed through the VoxelPort relay so your home IP is never exposed to players
- **Discord verification** — no account creation needed, just a DM from the VoxelPort bot with a 6-digit code
- **6-character room codes** — cleaner than IP addresses or long URLs
- **Live relay ping** — shown in the tab list header with color coding (green/yellow/red)
- **Settings screen** — override the relay server URL from inside Minecraft (⚙ button on the multiplayer screen)
- **Machine-bound auth** — your verification is tied to your computer, so auth files can't be copied between machines
- **Per-session validation** — the bot checks your Discord membership before every session starts

---

## Privacy

VoxelPort stores a local auth cache and machine fingerprint in your Minecraft config folder (`config/voxelport/`). The following is sent to the VoxelPort bot server:

- Your Discord username (to look you up in the server)
- Your Discord user ID + machine ID (for session validation)

No passwords. No email. No game data. No analytics. Full privacy policy: [voxelport.qzz.io/legal](https://voxelport.qzz.io)

---

## Why not PlayIt.gg or Essential?

| | VoxelPort | PlayIt.gg | Essential |
|---|---|---|---|
| Works inside Minecraft | ✓ Pause menu | ✗ Separate app | ✓ But needs account |
| No new account needed | ✓ Discord only | ✗ PlayIt account | ✗ Essential account |
| Open source | ✓ MIT on GitHub | ✗ Closed | ✗ Closed |
| Your IP hidden from friends | ✓ Always | ✓ Always | ✗ P2P exposes IP |
| Paid features | ✗ Free forever | ✓ Paid tiers | ✓ Paid cosmetics |

**Honest downside:** Our relay is currently on a single server in India. Players in South/Southeast Asia get excellent latency. Europe and North America will see higher relay overhead (~100–250ms). We plan to expand if the project grows.

---

## Requirements

- Minecraft: `>=26 <27` (Fabric)
- Fabric Loader: `>=0.18.6`
- Java: `>=25`
- Environment: **Client only** — the host does not need a dedicated server

---

## Links

- [Discord](https://discord.gg/EuDMWUuGpp) — get verified, get support, follow updates
- [GitHub](https://github.com/trazhub/VoxelPort) — source code, issues, PRs
- [Status Page](https://voxelport.qzz.io/#/status) — check relay health from your location
- [Sponsor](https://github.com/sponsors/trazhub) — help fund more relay regions

---

*VoxelPort is not affiliated with Mojang, Microsoft, Fabric, or Discord. "Minecraft" is a trademark of Mojang AB.*
```

---

### Step 3: Upload the version

After creating the project, click **Create version**:

| Field | Value |
|---|---|
| Version number | `1.2.0` |
| Version title | `VoxelPort v1.2.0` |
| Release channel | `Release` |
| Loaders | `Fabric` |
| Game versions | `26.1.2` (add whatever shows in the picker that matches) |
| Primary file | `voxelport-mod-1.2.0.jar` |
| Additional file | `voxelport-mod-1.2.0-sources.jar` |
| Dependencies | None required (Fabric Loader is implied) |

**Version changelog** (paste this):

```markdown
### Added
- Settings screen — override the relay URL from inside Minecraft (⚙ on multiplayer screen)
- Live relay ping in the tab list header — color-coded green/yellow/red
- Relay URL fetched from the bot at startup — no mod update needed if relay changes

### Changed
- Discord auth cache: 2 days → 12 hours
- Auth is now machine-bound — fingerprinted with SHA-256(userId + machineId)

### Fixed
- Per-session Discord validation was wired up but never actually called — fixed

### Security
- Bot `/verify/start` and `/verify/confirm` endpoints were unprotected (middleware registered after routes) — fixed
- Relay URL is XOR-encoded in the JAR — not visible as a plain string
```

### What NOT to do on Modrinth:
- Don't upload `voxelport-mod-1.1.0.jar` as a file attachment in 1.2.0
- Don't set release channel to `Beta` or `Alpha` — this is a proper release
- Don't skip the description — Modrinth reviewers read it and mods without descriptions get rejected or buried
- Don't forget to add the Discord link — reviewers flag mods that verify against a Discord server but don't link it
- Don't use the category `Adventure` or `World Generation` — these will mislead people and may get your tags edited

---

## Part 3 — CurseForge

### Step 1: Create the project

Go to `curseforge.com/dashboard` → Create Project → Minecraft: Java Edition → Mod

**Basic info:**
| Field | Value |
|---|---|
| Project name | `VoxelPort` |
| Summary | `Host Minecraft worlds over the internet with a 6-character code. Fabric client mod — no port forwarding needed.` |
| Categories | `Multiplayer`, `Utility` |

**Links:**
- Source code: `https://github.com/trazhub/VoxelPort`
- Discord: `https://discord.gg/EuDMWUuGpp`

### Step 2: CurseForge description

CurseForge supports HTML and BBCode. Use the same content as Modrinth above — paste the Markdown and use the rich text editor to format it. Key sections to include:

1. **What it does** — one paragraph, plain English, no jargon
2. **How to use** — numbered steps (Install → Discord verify → Host/Join)
3. **Feature list** — bullet points
4. **Requirements box** — Minecraft version, Fabric version, Java version, Client-only note
5. **Discord requirement notice** — make this prominent; CurseForge flags mods that require Discord without disclosing it
6. **Privacy notice** — what data is stored locally, what is sent to the server
7. **Links** — GitHub, Discord, Status page

> **Important for CurseForge specifically:** They require that if your mod contacts an external server,
> you must clearly state it in the description. Add a section like:
>
> **External connections:** This mod contacts `voxelportrelay.qzz.io` (the VoxelPort bot and relay server)
> for Discord verification and session routing. See the privacy notice for full details.

### Step 3: Upload the file

**File:** `voxelport-mod-1.2.0.jar`  
**Display name:** `VoxelPort 1.2.0`  
**Release type:** `Release`  
**Game version:** Select `1.26.1` or whatever the picker shows for your version  
**Mod loader:** `Fabric`  
**Java version:** `Java 25`  
**Additional file:** Upload `voxelport-mod-1.2.0-sources.jar` as an additional file

**Changelog:** Use same text as Modrinth version changelog above.

### What NOT to do on CurseForge:
- Don't mark it as `Alpha` or `Beta`
- Don't forget the external connections disclosure — CurseForge will reject or flag it without this
- Don't set both `Fabric` and `Forge` as supported loaders — this is Fabric only
- Don't skip the Discord requirement notice — users who don't know they need to join your Discord will leave angry reviews
- Don't set an incorrect Java version — Java 25 is required, do not say Java 17 or 21

---

## Part 4 — After Launch

### Post in your Discord

Post this in your announcements channel:

```
@everyone

🎉 VoxelPort v1.2.0 is now public on Modrinth and CurseForge!

What's new:
⚙️ In-game settings screen — change the relay URL without reinstalling
📡 Live relay ping in the tab list (green/yellow/red)
🔒 Machine-bound auth — auth files can't be copied between computers
🛡️ Per-session bot validation before every host/join

Download:
→ Modrinth: [link]
→ CurseForge: [link]
→ GitHub: https://github.com/trazhub/VoxelPort/releases/tag/v1.2.0

To use the mod you need to be in this server — which you already are 👍
```

### Monitor after launch

- Watch the Discord for bug reports in the first 24 hours
- Check `http://voxelportrelay.qzz.io:2525/health` to confirm the bot is handling the new traffic
- Check `http://voxelportrelay.qzz.io:2525/stats` to see active players
- Keep an eye on Modrinth reviews — respond to every negative review professionally

### Common complaints to be ready for

| Complaint | Your response |
|---|---|
| "Why do I need to join your Discord?" | "It's our identity verification system. No OAuth, no account creation — just a DM with a code. This gates access so random people can't abuse the relay." |
| "High ping / lag" | "Our relay is in India. If you're in Europe or North America you'll see higher latency. We're working on expanding to more regions as the project grows." |
| "Is this safe? It contacts an external server" | "Yes — the full source is on GitHub (MIT). The mod only contacts our relay and bot server. No passwords, no game data, no analytics. Privacy policy is at voxelport.qzz.io/legal." |
| "Why not use PlayIt.gg?" | "VoxelPort lives inside Minecraft (pause menu button), is fully open source, and doesn't need a separate app or account. PlayIt requires a separate agent process." |
| "This is AI-generated code" | "Claude (AI) was used as a coding assistant. The relay architecture, verification system, and product decisions were all designed by me. Full source is on GitHub — read it and judge for yourself." |

---

## Automation: future releases

Once the GitHub secrets are set up, future releases are one command:

```bash
# 1. Update mod_version in gradle.properties
# 2. Add entry to CHANGELOG.md
# 3. Commit and push
git add .
git commit -m "Release v1.3.0"
git push origin main

# 4. Tag — this triggers the workflow which builds + publishes everywhere
git tag v1.3.0
git push origin v1.3.0
```

The GitHub Actions workflow will:
- Build the JAR
- Create a GitHub Release with the JAR attached and changelog auto-extracted
- Publish to Modrinth
- Publish to CurseForge

You only have to push a tag.

---

## Files reference

| File | Purpose |
|---|---|
| `build/libs/voxelport-mod-1.2.0.jar` | The mod — what players install |
| `build/libs/voxelport-mod-1.2.0-sources.jar` | Source JAR — upload as additional file on both platforms |
| `CHANGELOG.md` | Source of truth for all changelogs — keep this updated |
| `RELEASE_PAGE.md` | Long-form release description — reference for writing platform descriptions |
| `.github/workflows/publish.yml` | CI/CD — runs on every `v*` tag push |
| `gradle.properties` | Change `mod_version` here before each release |

---

*Good luck with the launch. The mod is solid — relay works, verification works, JAR is built. Go ship it.*
