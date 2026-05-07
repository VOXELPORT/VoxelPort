<div align="center">
  <h1>⛏️ VoxelPort</h1>
  <p><strong>A standalone, lightweight Java Minecraft server manager for Windows.</strong></p>

  [![Version](https://img.shields.io/badge/version-1.0.0-blue.svg)]()
  [![Platform](https://img.shields.io/badge/platform-Windows-0078d7.svg)]()
  [![Java](https://img.shields.io/badge/java-8%20|%2017%20|%2021-orange.svg)]()
  [![Sponsor](https://img.shields.io/badge/Sponsor-💖-ff69b4.svg)](https://github.com/sponsors/trazhub)

  *Manage your servers, backups, plugins, and Cloudflare tunnels in one place.*
</div>

---

## ✨ Features

- **Server Installation:** Automatically download and set up Paper, Purpur, Fabric, or Forge Minecraft servers.
- **Java Management:** Automatically detects and manages the correct Java runtime (Java 8, 17, or 21) based on the server version.
- **Modrinth Integration:** Search and install plugins and mods directly from Modrinth within the UI.
- **Cloudflare Tunnels:** Instantly start a secure Cloudflare tunnel to share a room code with friends—no router port-forwarding required.
- **Live Console:** View server logs in real-time and execute commands.
- **Backups:** Create full server or world-only zip backups with a single click.
- **Portable:** No heavy dependencies; built with Swing and FlatLaf.

---

## 🚀 Installation & Usage

VoxelPort is currently supported on **Windows only** for v1.0.0.

### Download

Grab the latest release executable from the [Releases page](../../releases) and simply run `VoxelPort.exe`.

### Hosting a Server
1. Click **+ Install New Server** and select your desired server software and version.
2. Adjust the **RAM Allocation** using the slider.
3. Click **▶ Start Server** to launch the server instance.

### Joining a Server via Tunnel
1. If your friend is hosting using VoxelPort's Cloudflare tunnel, go to the **Join Room** tab.
2. Enter the Room Code they provided and click **Connect**.
3. Launch Minecraft and connect to `localhost:25565`.

---

## 🛠️ Build From Source

VoxelPort uses a dependency-free build process with simple PowerShell scripts. No Maven or Gradle required!

### Prerequisites
- JDK 21+
- Windows (PowerShell)

### Run for Development
```powershell
.\run.ps1
```

### Build Executable Image
Builds a self-contained Windows application image with a runnable `.exe`.
```powershell
.\build.ps1
```

**Output:**
```text
dist\VoxelPort\VoxelPort.exe
```

---

## 💖 Support the Project

If you find VoxelPort useful, consider supporting the development!

[![Sponsor trazhub](https://img.shields.io/badge/Sponsor_trazhub-💖-ff69b4.svg?style=for-the-badge&logo=github-sponsors)](https://github.com/sponsors/trazhub)

---
<div align="center">
  <sub>Built with 💚 and dirt blocks by <a href="https://github.com/trazhub">trazhub</a></sub>
</div>
