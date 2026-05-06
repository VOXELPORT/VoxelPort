# VoxelPort - Project Memory

## Project Overview
Standalone Java rewrite of VoxelPort using dependency-free Swing.

## Current State (2026-05-06)
- **Stable Build:** `build.ps1` (Windows) and `build.sh` (Linux) are functional.
- **Architecture:** Modular service-oriented model:
- `LocalMJava.java`: UI and Orchestration.
  - `org.localm.service`: `ServerStore`, `VersionService`, `ServerProcessManager`, `TunnelService`, `BackupService`, `ConfigService`, `ModrinthService`.
  - `org.localm.model`: `ServerVersion`, `RamPreset`, `ModrinthProject`.

## Key Features
- **Server Support:** Paper, Purpur, Fabric, and **Forge** (including 1.17+ installer/launch support).
- **Console:** Interactive input, ANSI color support, auto-scroll, and persistent logging to `data/logs/`.
- **Properties Editor:** Built-in editor for `server.properties` with a table interface.
- **Plugin/Mod Manager:** Integrated **Modrinth** search and one-click installation.
- **Resource Monitoring:** Real-time **CPU usage** display in the server list.
- **Backups:** "World Only" vs "Full Server" options and "Auto-backup on Stop".
- **Public Rooms:** Port-mapped Cloudflare Tunnels with encrypted room codes.
- **UI:** Classic Java (Metal) look for a standard utility feel.

## CLI Usage
- `java -jar VoxelPort.jar --list`
- `java -jar VoxelPort.jar --start "ServerName"`

## Pending / Future Ideas
- Support for more server types (BungeeCord/Velocity).
- Real-time RAM usage monitoring (requires OS-specific calls or JNA).
- Auto-update check for the application itself.
