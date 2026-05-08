<div align="center">
  <h1>VoxelPort</h1>
  <p><strong>A standalone, lightweight Java Minecraft server manager.</strong></p>

  [![Version](https://img.shields.io/badge/version-1.1.0-blue.svg)]()
  [![Platform](https://img.shields.io/badge/platform-Windows%20%7C%20macOS%20%7C%20Linux-0078d7.svg)]()
  [![Java](https://img.shields.io/badge/java-17%2B-orange.svg)]()
  [![Sponsor](https://img.shields.io/badge/Sponsor-ff69b4.svg)](https://github.com/sponsors/trazhub)

  *Manage your servers, backups, plugins, mods, and Cloudflare tunnels in one place.*
</div>

---

## Features

- **Server installation:** Download and set up Paper, Purpur, Fabric, or Forge Minecraft servers.
- **Java management:** Detects and can download a suitable Java runtime for each Minecraft version.
- **Modrinth integration:** Search and install plugins and mods directly from the UI.
- **Cloudflare tunnels:** Starts a Cloudflare TCP tunnel and downloads the correct `cloudflared` binary for the user's OS and CPU architecture when needed.
- **Live console:** View server logs in real time and execute commands.
- **Backups:** Create full server or world-only zip backups.
- **Portable JAR:** Built with Swing and FlatLaf, with no Maven or Gradle requirement.

---

## Installation & Usage

VoxelPort now supports **Windows, Linux, and macOS** through the portable JAR build.

### Download

Grab the release JAR from the [Releases page](../../releases), then run:

```powershell
java -jar VoxelPort.jar
```

### Windows

1. Install Java 17 or newer from [Adoptium](https://adoptium.net/).
2. Download `VoxelPort.jar` from the [Releases page](../../releases).
3. Open PowerShell in the download folder.
4. Run:

```powershell
java -jar .\VoxelPort.jar
```

### Linux

1. Install Java 17 or newer.

Ubuntu/Debian:

```bash
sudo apt update
sudo apt install openjdk-17-jre
```

Fedora:

```bash
sudo dnf install java-17-openjdk
```

Arch:

```bash
sudo pacman -S jre17-openjdk
```

2. Download `VoxelPort.jar` from the [Releases page](../../releases).
3. Open a terminal in the download folder.
4. Run:

```bash
java -jar VoxelPort.jar
```

### macOS

1. Install Java 17 or newer from [Adoptium](https://adoptium.net/) or with Homebrew:

```bash
brew install openjdk@17
```

2. Download `VoxelPort.jar` from the [Releases page](../../releases).
3. Open Terminal in the download folder.
4. Run:

```bash
java -jar VoxelPort.jar
```

If macOS blocks the app because it was downloaded from the internet, allow it from **System Settings > Privacy & Security**, then run the command again.

### Hosting a Server

1. Click **+ Install New Server** and select your desired server software and version.
2. Adjust the **RAM Allocation** using the slider.
3. Click **Start Server** to launch the server instance.

### Joining a Server via Tunnel

1. If your friend is hosting using VoxelPort's Cloudflare tunnel, go to the **Join Room** tab.
2. Enter the room code they provided and click **Connect**.
3. Launch Minecraft and connect to `localhost:25565`.

---

## Build From Source

VoxelPort uses a dependency-free build process with simple PowerShell scripts.

### Prerequisites

- JDK 17 or newer
- PowerShell

### Run for Development

```powershell
.\run.ps1
```

### Build Portable JAR

```powershell
.\build.ps1
```

**Output:**

```text
dist\VoxelPort.jar
dist\VoxelPort-1.1.0-portable.zip
```

---

## Support the Project

If you find VoxelPort useful, consider supporting the development.

[![Sponsor trazhub](https://img.shields.io/badge/Sponsor_trazhub-ff69b4.svg?style=for-the-badge&logo=github-sponsors)](https://github.com/sponsors/trazhub)
