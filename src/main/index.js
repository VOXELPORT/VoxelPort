import { app, BrowserWindow, Menu, dialog } from "electron";
import path from "node:path";
import os from "node:os";
import { fileURLToPath } from "node:url";
import Store from "electron-store";
import ServerManager from "./server.js";
import InstallManager from "./installer.js";
import ModManager from "./modManager.js";
import { registerIpc } from "./ipc.js";

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);
const DEFAULT_RELAY_URL = "wss://voxelportrelay.qzz.io";

const store = new Store({
  defaults: {
    servers: [],
    settings: {
      relayServerUrl: DEFAULT_RELAY_URL,
      defaultRam: 2048,
      defaultJavaPath: "",
      defaultInstallLocation: path.join(app.getPath("documents"), "MinecraftServers"),
      theme: "dark"
    },
    windowBounds: {
      width: 1100,
      height: 700
    }
  }
});

// ─── Discord bot notification ─────────────────────────────────────────────────
function postToBot(endpoint, payload) {
  const auth = store.get("discordAuth", null);
  const discordTag = auth?.username ? String(auth.username).trim() : "";
  if (!discordTag) return;

  const settings  = store.get("settings", {});
  const botUrl    = String(settings.botUrl    || "http://92.4.70.103:2525").trim();
  const botSecret = String(settings.botSecret || "a8048edfed4f9bfcaca216b5b1217f5eb7e521c52c343698ea2b988c0969a0a6").trim();

  const target  = new URL(endpoint, botUrl.endsWith("/") ? botUrl : `${botUrl}/`);
  const headers = { "Content-Type": "application/json" };
  if (botSecret) headers["x-bot-secret"] = botSecret;

  fetch(target.toString(), {
    method: "POST",
    headers,
    body: JSON.stringify({ discordTag, ...payload }),
    signal: AbortSignal.timeout(8_000),
  }).catch(() => {}); // silently ignore — bot may not be reachable
}

let mainWindow = null;
const serverManager = new ServerManager();
const installManager = new InstallManager();
const modManager = new ModManager((serverId) => {
  const servers = store.get("servers", []);
  return servers.find((server) => server.id === serverId);
});

function createMainWindow() {
  const bounds = store.get("windowBounds");
  const width = Math.max(800, Number(bounds?.width || 1100));
  const height = Math.max(600, Number(bounds?.height || 700));
  const iconPath = path.join(app.getAppPath(), "assets", "icon.png");

  mainWindow = new BrowserWindow({
    width,
    height,
    x: Number.isInteger(bounds?.x) ? bounds.x : undefined,
    y: Number.isInteger(bounds?.y) ? bounds.y : undefined,
    minWidth: 800,
    minHeight: 600,
    backgroundColor: "#0a0c10",
    icon: iconPath,
    frame: false,
    titleBarStyle: "hidden",
    trafficLightPosition: { x: -100, y: -100 }, // hide macOS traffic lights (we draw our own)
    webPreferences: {
      preload: path.join(__dirname, "..", "preload.cjs"),
      contextIsolation: true,
      nodeIntegration: false
    }
  });

  mainWindow.on("close", () => {
    if (!mainWindow) return;
    store.set("windowBounds", mainWindow.getBounds());
  });

  mainWindow.on("maximize",   () => mainWindow?.webContents.send("window-maximize-change", { maximized: true  }));
  mainWindow.on("unmaximize", () => mainWindow?.webContents.send("window-maximize-change", { maximized: false }));

  if (!app.isPackaged) {
    const devUrl = process.env.VITE_DEV_SERVER_URL || "http://127.0.0.1:5173";
    mainWindow.loadURL(devUrl);
  } else {
    mainWindow.loadFile(path.join(app.getAppPath(), "dist", "renderer", "index.html"));
    Menu.setApplicationMenu(null);
  }
}

app.whenReady().then(async () => {
  createMainWindow();

  // Notify bot that the app just opened
  postToBot("/notify", {
    hostname: os.hostname(),
    timezone: Intl.DateTimeFormat().resolvedOptions().timeZone,
    version: app.getVersion()
  });
  // Prevent system sleep on Linux (relay server machine)
  if (process.platform === "linux") {
    const { powerSaveBlocker } = await import("electron");
    powerSaveBlocker.start("prevent-app-suspension");
  }
  registerIpc({
    getMainWindow: () => mainWindow,
    store,
    serverManager,
    installManager,
    modManager,
    app,
    dialog
  });

  app.on("activate", () => {
    if (BrowserWindow.getAllWindows().length === 0) createMainWindow();
  });
});


app.on("window-all-closed", () => {
  if (process.platform !== "darwin") app.quit();
});

app.on("before-quit", async () => {
  postToBot("/close", {});
  const servers = store.get("servers", []);
  await Promise.allSettled(servers.map((server) => serverManager.stopServer(server.id)));
});
