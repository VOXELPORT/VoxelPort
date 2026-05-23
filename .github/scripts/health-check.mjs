import WebSocket from "ws";
import nodemailer from "nodemailer";

const RELAY_URL  = "wss://voxelportrelay.qzz.io/relay";
const BOT_URL    = "http://voxelportrelay.qzz.io:2525";
const WEBSITE    = "https://voxelport.qzz.io";
const REPO       = process.env.REPO || "trazhub/VoxelPort";
const GH_TOKEN   = process.env.GITHUB_TOKEN;
const WEBHOOK    = process.env.DISCORD_WEBHOOK;
const GMAIL_USER = process.env.GMAIL_USER;
const GMAIL_PASS = process.env.GMAIL_APP_PASSWORD;

// ── helpers ────────────────────────────────────────────────────────────────────

async function checkHttp(url) {
  const start = Date.now();
  try {
    const r = await fetch(url, { signal: AbortSignal.timeout(10_000) });
    return { ok: r.ok, status: r.status, ms: Date.now() - start, data: await r.json().catch(() => null) };
  } catch (e) {
    return { ok: false, status: 0, ms: Date.now() - start, error: e.message };
  }
}

function checkRelay() {
  return new Promise((resolve) => {
    const start = Date.now();
    const ws = new WebSocket(RELAY_URL);
    const timer = setTimeout(() => {
      ws.terminate();
      resolve({ ok: false, ms: 10_000, error: "timeout" });
    }, 10_000);
    ws.on("open", () => {
      clearTimeout(timer);
      ws.close();
      resolve({ ok: true, ms: Date.now() - start });
    });
    ws.on("error", (e) => {
      clearTimeout(timer);
      resolve({ ok: false, ms: Date.now() - start, error: e.message });
    });
  });
}

async function getRecentIssues() {
  const since = new Date(Date.now() - 6 * 60 * 60 * 1000).toISOString();
  try {
    const r = await fetch(
      `https://api.github.com/repos/${REPO}/issues?state=open&since=${since}&per_page=20`,
      { headers: { Authorization: `Bearer ${GH_TOKEN}`, "User-Agent": "voxelport-monitor" } }
    );
    if (!r.ok) return [];
    const issues = await r.json();
    return issues.filter(i => !i.pull_request && new Date(i.created_at) > new Date(since));
  } catch {
    return [];
  }
}

function emoji(ok) { return ok ? "✅" : "❌"; }
function label(ok) { return ok ? "Online" : "DOWN"; }

// ── main ──────────────────────────────────────────────────────────────────────

console.log("Running VoxelPort health check...");

const [website, botHealth, relay, newIssues] = await Promise.all([
  checkHttp(WEBSITE),
  checkHttp(`${BOT_URL}/health`),
  checkRelay(),
  getRecentIssues(),
]);

const websiteOk     = website.status >= 200 && website.status < 400;
const relayOk       = relay.ok;
const botOk         = botHealth.data?.ok === true;
const botReady      = botHealth.data?.botReady === true;
const activePlayers = botHealth.data?.activePlayers ?? "?";
const allGood       = websiteOk && relayOk && botOk && botReady;
const now           = new Date().toUTCString();

// ── Discord report ─────────────────────────────────────────────────────────────
if (WEBHOOK) {
  const issueLines = newIssues.length
    ? newIssues.map(i => `• [#${i.number}](${i.html_url}) ${i.title} — @${i.user.login}`).join("\n")
    : "None in the last 6 hours";

  const embed = {
    title: `${allGood ? "✅" : "⚠️"} VoxelPort — 6-Hour Health Report`,
    color: allGood ? 0x00ffb2 : 0xed4245,
    fields: [
      { name: "🌐 Website",         value: `${emoji(websiteOk)} ${label(websiteOk)} (${website.ms}ms)`, inline: true },
      { name: "📡 Relay WS",        value: `${emoji(relayOk)} ${label(relayOk)} (${relay.ms}ms)`,       inline: true },
      { name: "🤖 Bot",             value: `${emoji(botReady)} ${label(botReady)}`,                       inline: true },
      { name: "🟢 Active Players",  value: String(activePlayers),                                          inline: true },
      { name: "📋 New Issues",      value: issueLines },
    ],
    footer: { text: `Checked at ${now} (IST: ${new Date(Date.now() + 5.5 * 3600000).toUTCString().replace("GMT", "IST")})` },
  };

  await fetch(WEBHOOK, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ embeds: [embed] }),
  }).catch(e => console.error("Discord webhook failed:", e.message));

  console.log("Discord report sent.");
}

// ── Email report ───────────────────────────────────────────────────────────────
if (GMAIL_USER && GMAIL_PASS) {
  const issueText = newIssues.length
    ? newIssues.map(i => `  #${i.number}: ${i.title}\n  ${i.html_url}`).join("\n\n")
    : "  None in the last 6 hours";

  const istTime = new Date(Date.now() + 5.5 * 3600000).toUTCString().replace("GMT", "IST");

  const subject = allGood
    ? `✅ VoxelPort Health Report — All Systems OK`
    : `⚠️ VoxelPort Health Report — Issues Detected`;

  const text = `VoxelPort — 6-Hour Health Report
Generated: ${istTime}

SERVICES
  Website    : ${label(websiteOk)} (${website.ms}ms)
  Relay WS   : ${label(relayOk)} (${relay.ms}ms)
  Discord Bot: ${label(botReady)}
  Active Players: ${activePlayers}

NEW GITHUB ISSUES (last 6h)
${issueText}

${allGood ? "All systems green." : "⚠️ One or more services need attention — check your VPS!"}
`;

  const transporter = nodemailer.createTransport({
    service: "gmail",
    auth: { user: GMAIL_USER, pass: GMAIL_PASS },
  });

  await transporter.sendMail({
    from: GMAIL_USER,
    to: GMAIL_USER,
    subject,
    text,
  }).catch(e => console.error("Email failed:", e.message));

  console.log("Email sent.");
}

console.log(`\nSummary: website=${label(websiteOk)} relay=${label(relayOk)} bot=${label(botReady)} newIssues=${newIssues.length}`);
if (!allGood) process.exit(1);
