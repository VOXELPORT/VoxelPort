import http from "node:http";
import { shell } from "electron";

const CALLBACK_PORT = 7847;
const REDIRECT_URI = `http://localhost:${CALLBACK_PORT}/callback`;

// ─── OAuth flow ───────────────────────────────────────────────────────────────

export function runOAuthFlow(clientId) {
  const state = Math.random().toString(36).slice(2, 12);

  const params = new URLSearchParams({
    client_id: clientId,
    redirect_uri: REDIRECT_URI,
    response_type: "code",
    scope: "identify",
    state,
  });

  const authUrl = `https://discord.com/oauth2/authorize?${params}`;

  return new Promise((resolve, reject) => {
    let server = null;

    const finish = (err, code) => {
      clearTimeout(timeoutId);
      server?.close();
      server = null;
      if (err) reject(err);
      else resolve(code);
    };

    const timeoutId = setTimeout(() => {
      finish(new Error("Authorization timed out — the browser window was not completed in time."));
    }, 5 * 60 * 1000);

    server = http.createServer((req, res) => {
      if (!req.url?.startsWith("/callback")) {
        res.writeHead(404);
        res.end();
        return;
      }

      const url = new URL(req.url, `http://localhost:${CALLBACK_PORT}`);
      const code = url.searchParams.get("code");
      const returnedState = url.searchParams.get("state");
      const error = url.searchParams.get("error");

      const page = (ok, message) => `<!DOCTYPE html>
<html><head><title>VoxelPort — Discord</title>
<style>
  * { margin: 0; padding: 0; box-sizing: border-box; }
  body { font-family: -apple-system, sans-serif; background: #0a0c10; color: #e4e4e7;
         display: flex; align-items: center; justify-content: center; min-height: 100vh; }
  .card { text-align: center; padding: 2.5rem; border-radius: 1rem;
          border: 1px solid ${ok ? "rgba(74,222,128,.3)" : "rgba(248,113,113,.3)"};
          background: ${ok ? "rgba(74,222,128,.05)" : "rgba(248,113,113,.05)"}; }
  h2 { font-size: 1.4rem; margin-bottom: .5rem;
       color: ${ok ? "#4ade80" : "#f87171"}; }
  p  { font-size: .9rem; color: #71717a; }
</style></head>
<body><div class="card">
  <h2>${ok ? "✅ Discord linked!" : "❌ Authorization failed"}</h2>
  <p>${message}</p>
</div></body></html>`;

      if (error) {
        res.writeHead(200, { "Content-Type": "text/html" });
        res.end(page(false, `Discord returned: ${error}`));
        finish(new Error(error));
        return;
      }

      if (returnedState !== state) {
        res.writeHead(200, { "Content-Type": "text/html" });
        res.end(page(false, "State mismatch — please try again."));
        finish(new Error("OAuth state mismatch"));
        return;
      }

      res.writeHead(200, { "Content-Type": "text/html" });
      res.end(page(true, "You can close this tab and return to VoxelPort."));
      finish(null, code);
    });

    server.on("error", (err) => {
      if (err.code === "EADDRINUSE") {
        finish(new Error(`Port ${CALLBACK_PORT} is already in use. Close other VoxelPort instances and try again.`));
      } else {
        finish(err);
      }
    });

    server.listen(CALLBACK_PORT, "127.0.0.1", () => {
      shell.openExternal(authUrl).catch(() => null);
    });
  });
}

// ─── Token exchange ───────────────────────────────────────────────────────────

export async function exchangeCode(code, clientId, clientSecret) {
  const res = await fetch("https://discord.com/api/oauth2/token", {
    method: "POST",
    headers: { "Content-Type": "application/x-www-form-urlencoded" },
    body: new URLSearchParams({
      client_id: clientId,
      client_secret: clientSecret,
      grant_type: "authorization_code",
      code,
      redirect_uri: REDIRECT_URI,
    }),
  });

  if (!res.ok) {
    let detail = "";
    try { detail = (await res.json())?.error_description || ""; } catch {}
    throw new Error(`Token exchange failed${detail ? `: ${detail}` : ". Check your Client ID and Secret."}`);
  }

  return res.json();
}

// ─── Discord API calls ────────────────────────────────────────────────────────

export async function fetchDiscordUser(accessToken) {
  const res = await fetch("https://discord.com/api/users/@me", {
    headers: { Authorization: `Bearer ${accessToken}` },
  });
  if (!res.ok) throw new Error("Failed to fetch Discord profile.");
  return res.json();
}

export async function fetchMemberRoles(userId, botPort = 2525) {
  try {
    const res = await fetch(`http://127.0.0.1:${botPort}/member/${encodeURIComponent(userId)}`, {
      signal: AbortSignal.timeout(5000),
    });
    if (!res.ok) return null;
    return res.json();
  } catch {
    return null;
  }
}

// ─── Avatar URL helper ────────────────────────────────────────────────────────

export function avatarUrl(user) {
  if (user?.avatar) {
    return `https://cdn.discordapp.com/avatars/${user.id}/${user.avatar}.png?size=64`;
  }
  const index = Number(BigInt(user?.id ?? "0") >> 22n) % 6;
  return `https://cdn.discordapp.com/embed/avatars/${index}.png`;
}
