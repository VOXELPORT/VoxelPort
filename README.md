# VoxelPort

Standalone Java Minecraft server manager (Windows-only for v1.0.0).

## Current Features

- Install Paper/Purpur Minecraft servers
- Start and stop a local server
- Console output
- Open server and plugins folders
- Delete a server from VoxelPort and from disk
- Zip world backups
- Start a Cloudflare tunnel using `bin/tunnel-daemon.exe`
- Join a room code through a local proxy on `localhost:25565`
- Build a Windows app image with a runnable `.exe`

## Run From Source

```powershell
.\run.ps1
```

## Build App Image

```powershell
.\build.ps1
```

Output:

```text
dist\VoxelPort\VoxelPort.exe
```

This first version is dependency-free Swing so it can build without Gradle or Maven.

## Platform Support

This first release is supported on Windows only.
