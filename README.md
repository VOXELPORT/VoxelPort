# VoxelPort

**Manage, mod, and share Minecraft Java servers from one desktop app.**

VoxelPort is an Electron + React desktop application for creating, running, managing, and sharing Minecraft Java servers. It is built for people who host servers locally and want a cleaner workflow for installation, server control, mod/plugin management, and relay-based multiplayer sharing.

---

## Made For

- Players hosting Minecraft servers for friends
- Modded server users who want easier setup and management
- Self-hosters who want to use their own VPS relay instead of relying on a public service

---

## Security First

VoxelPort is designed to reduce avoidable risk in local server management and relay setup.

- Validates and sanitizes server input before it is stored or used
- Protects against path traversal issues during mod install and removal
- Validates external URLs before opening them
- Supports self-hosted relay configuration so you control your own infrastructure

---

## Key Features

- Install Paper, Purpur, Vanilla, Fabric, Forge, and NeoForge servers
- Start and stop servers from a desktop UI
- View live console output and send commands
- Track runtime stats such as RAM, CPU, uptime, and player count
- Browse and install mods/plugins from Modrinth and Hangar
- Update and remove installed mods/plugins
- Create shareable 6-character relay room codes
- Join shared rooms and connect through `localhost`
- Configure relay URL, default RAM, Java path, and install location

---

## Working

- Server install flow for major Minecraft Java server types
- Local server lifecycle management
- Console and runtime monitoring
- Mod and plugin browsing / installation
- Relay room creation and join flow
- Custom VPS relay configuration

---

## How It Works

### 1. Install a Server

Choose a server type, pick a version, configure name/path/port/RAM, accept the Minecraft EULA, and let VoxelPort set up the server.

### 2. Run and Manage

Start or stop servers, watch console output, send commands, and inspect server stats from the main dashboard.

### 3. Manage Mods and Plugins

Search Modrinth or Hangar, install supported mods/plugins, check for updates, and remove old entries when needed.

### 4. Share with Friends

Create a room code for a running server, share the 6-character code, and let others join through the configured relay. Players connect in Minecraft using `localhost`.

---

## Getting Started

### Requirements

- Windows
- Java installed for Minecraft server runtime
- Node.js and npm for development

### Development Setup

```bash
npm install
npm run relay:dev
npm run dev
```

### Production Build

```bash
npm run build
```

Build artifacts are written to `dist-electron/`.

---

## Relay Server

VoxelPort does not depend on a built-in public relay. You should configure your own relay URL in Settings.

Example:

```text
wss://your-relay.example.com
```

If only a host is entered, VoxelPort automatically uses the `/relay` WebSocket path.

### Relay Deployment

#### Docker

```bash
cd relay-server
docker build -t minecraft-relay .
docker run -d --name minecraft-relay -p 4000:4000 minecraft-relay
```

#### Manual Node.js

```bash
cd relay-server
node index.js
```

---

## Built With

- Electron
- React
- Vite
- Tailwind CSS
- Node.js
- socket.io-client

---


<iframe src="https://github.com/sponsors/trazhub/card" title="Sponsor trazhub" height="225" width="600" style="border: 0;"></iframe>



## Contributing

Contributions, bug reports, and feature requests are welcome. Open an issue or submit a pull request if you want to improve VoxelPort.

---

## License

This project is licensed under the MIT License.
