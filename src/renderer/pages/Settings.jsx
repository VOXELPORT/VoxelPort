import React, { useEffect, useMemo, useRef, useState } from "react";
import { Check, ExternalLink, FolderOpen, HandCoins, Info, Save, TriangleAlert, LogOut, Send, ShieldCheck, RefreshCw } from "lucide-react";
import { useAppContext, useToast } from "../App.jsx";

const DEFAULT_RELAY_URL = "wss://voxelportrelay.qzz.io";
const defaultSettings = {
  relayServerUrl: DEFAULT_RELAY_URL,
  defaultRam: 2048,
  defaultJavaPath: "",
  defaultInstallLocation: "",
  theme: "dark",
};

function SettingRow({ label, hint, children }) {
  return (
    <div className="rounded-xl border border-border bg-bg-card p-4 transition hover:border-border-glow/30">
      <div className="mb-3">
        <div className="text-sm font-medium text-text-primary">{label}</div>
        {hint && <div className="mt-0.5 text-xs text-text-muted">{hint}</div>}
      </div>
      {children}
    </div>
  );
}

export default function Settings() {
  const { setSettings, discordAuth, setDiscordAuth } = useAppContext();
  const { showToast } = useToast();
  const [form, setForm] = useState(defaultSettings);
  const [saved, setSaved] = useState(false);
  const [javaMissing, setJavaMissing] = useState(false);
  const [javaFound, setJavaFound] = useState(false);
  const [relayTesting, setRelayTesting] = useState(false);
  const [relayStatus, setRelayStatus] = useState(null);
  // Discord DM verification state
  const [dvUsername, setDvUsername]   = useState("");
  const [dvCode, setDvCode]           = useState("");
  const [dvStep, setDvStep]           = useState("idle");   // idle | sending | code | confirming
  const [dvError, setDvError]         = useState("");
  const codeRef = useRef(null);

  useEffect(() => {
    window.api.getSettings().then((res) => {
      if (res.success) setForm({ ...defaultSettings, ...(res.data || {}) });
    });
  }, []);

  const relayInput = String(form.relayServerUrl || "").trim();
  const relayMissing = relayInput.length === 0;
  const relayWarning = useMemo(() => {
    const value = relayInput.toLowerCase();
    return value.startsWith("ws://") && !value.includes("localhost") && !value.includes("127.0.0.1");
  }, [relayInput]);
  const relayPreview = useMemo(() => {
    if (!relayInput) return "";

    const hasScheme = relayInput.includes("://");
    const normalized = hasScheme
      ? relayInput
      : relayInput.includes(":")
        ? `ws://${relayInput}`
        : `wss://${relayInput}`;

    try {
      const url = new URL(normalized);
      if (url.protocol === "http:") url.protocol = "ws:";
      if (url.protocol === "https:") url.protocol = "wss:";
      if (!url.pathname || url.pathname === "/") url.pathname = "/relay";
      return url.toString();
    } catch {
      return "";
    }
  }, [relayInput]);

  const save = async () => {
    if (relayMissing) {
      showToast("Enter your VPS relay URL before saving.", "error");
      return;
    }
    const res = await window.api.saveSettings(form);
    if (res.success) {
      setSettings(res.data);
      setSaved(true);
      setTimeout(() => setSaved(false), 2000);
      showToast("Settings saved.", "success");
    } else {
      showToast(res.error || "Failed to save settings.", "error");
    }
  };

  const field = (key) => ({
    value: form[key],
    onChange: (e) => setForm((prev) => ({ ...prev, [key]: e.target.value }))
  });

  const inputCls =
    "w-full rounded-lg border border-border bg-bg-input px-3 py-2 text-sm text-text-primary placeholder:text-text-faint transition focus:border-accent/50";

  return (
    <div className="mx-auto max-w-2xl animate-fade-in">
      <div className="mb-6">
        <h1 className="font-pixel text-2xl text-accent">Settings</h1>
        <p className="mt-1 text-sm text-text-muted">Configure VoxelPort's behaviour</p>
      </div>

      <div className="space-y-3">
        <SettingRow
          label="Relay Server URL"
          hint="The WebSocket relay used for multiplayer room sharing. Defaults to the free public VoxelPort relay."
        >
          <div className="flex gap-2">
            <input
              {...field("relayServerUrl")}
              className={`${inputCls} flex-1`}
              placeholder={DEFAULT_RELAY_URL}
            />
            <button
              type="button"
              disabled={relayMissing || relayTesting}
              onClick={async () => {
                if (relayMissing) {
                  setRelayStatus({ ok: false, error: "Relay server URL is required" });
                  return;
                }
                setRelayTesting(true);
                setRelayStatus(null);
                const res = await window.api.testRelay(form.relayServerUrl);
                setRelayTesting(false);
                if (res.success) {
                  setRelayStatus({ ok: true, ms: res.data?.latencyMs, url: res.data?.url });
                } else {
                  setRelayStatus({ ok: false, error: res.error });
                }
              }}
              className="rounded-lg border border-border px-3 py-2 text-xs text-text-muted transition hover:border-diamond/40 hover:text-diamond disabled:cursor-not-allowed disabled:opacity-50"
            >
              {relayTesting ? "Testing..." : "Test Connection"}
            </button>
          </div>

          {relayPreview && (
            <div className="mt-2 rounded-lg border border-border/80 bg-bg-input px-3 py-2 text-[11px] text-text-muted">
              Effective relay endpoint: <span className="font-mono text-text-primary">{relayPreview}</span>
            </div>
          )}

          <div className="mt-3 rounded-xl border border-border bg-bg-panel px-3 py-3 text-xs text-text-muted">
            <div className="flex items-start gap-2">
              <Info size={14} className="mt-0.5 shrink-0 text-diamond" />
              <div>
                VoxelPort uses the hosted public relay by default. You can replace it with your own VPS relay here.
                If you omit the path, VoxelPort will use
                <span className="font-mono"> /relay</span> automatically.
              </div>
            </div>
          </div>

          {relayWarning && (
            <div className="mt-3 rounded-xl border border-warning/30 bg-warning/10 px-3 py-3 text-xs text-warning">
              <div className="flex items-start gap-2">
                <TriangleAlert size={14} className="mt-0.5 shrink-0" />
                <div>
                  Warning: Unencrypted relay - all game traffic will be visible on the network.
                  Use wss:// for any public relay server.
                </div>
              </div>
            </div>
          )}

          {relayStatus?.ok && (
            <div className="mt-3 rounded-lg border border-accent/30 bg-accent/10 px-3 py-2 text-xs text-accent">
              Connected in {relayStatus.ms}ms
            </div>
          )}
          {relayStatus && !relayStatus.ok && (
            <div className="mt-3 rounded-lg border border-danger/30 bg-danger/10 px-3 py-2 text-xs text-danger">
              Could not connect: {relayStatus.error || "check the URL"}
            </div>
          )}
        </SettingRow>

        <SettingRow
          label="Default RAM (MB)"
          hint="Amount of memory allocated to new server processes."
        >
          <div className="flex items-center gap-4">
            <input
              type="range"
              min={512}
              max={16384}
              step={512}
              value={form.defaultRam}
              onChange={(e) => setForm((prev) => ({ ...prev, defaultRam: Number(e.target.value) }))}
              className="flex-1 accent-accent"
            />
            <div className="w-24 rounded-lg border border-border bg-bg-input px-3 py-1.5 text-center text-sm font-mono text-accent">
              {(form.defaultRam / 1024).toFixed(form.defaultRam >= 1024 ? 0 : 1)} GB
            </div>
          </div>
          <div className="mt-1 flex justify-between text-[10px] text-text-faint">
            <span>512 MB</span>
            <span>8 GB</span>
            <span>16 GB</span>
          </div>
        </SettingRow>

        <SettingRow
          label="Java Executable Path"
          hint="Leave blank to auto-detect from PATH and common install locations."
        >
          <div className="flex gap-2">
            <input
              {...field("defaultJavaPath")}
              className={`${inputCls} flex-1`}
              placeholder="Auto-detected if empty"
            />
            <button
              type="button"
              onClick={async () => {
                const res = await window.api.detectJava();
                if (res.success && res.data?.javaPath) {
                  setForm((prev) => ({ ...prev, defaultJavaPath: res.data.javaPath }));
                  setJavaMissing(false);
                  setJavaFound(true);
                  setTimeout(() => setJavaFound(false), 2000);
                } else {
                  setJavaMissing(true);
                  setJavaFound(false);
                }
              }}
              className="rounded-lg border border-border px-3 py-2 text-xs text-text-muted transition hover:border-accent/40 hover:text-accent"
            >
              Auto-detect
            </button>
          </div>
          {javaFound && (
            <div className="mt-2 flex items-center gap-1.5 text-xs text-accent">
              <Check size={11} /> Java detected successfully
            </div>
          )}
          {javaMissing && (
            <div className="mt-2 text-xs text-danger">
              Java not found.{" "}
              <button
                type="button"
                className="inline-flex items-center gap-1 text-diamond underline hover:no-underline"
                onClick={() => window.api.openExternal("https://adoptium.net")}
              >
                Download from adoptium.net <ExternalLink size={10} />
              </button>
            </div>
          )}
        </SettingRow>

        {/* ── Discord DM Verification ── */}
        <div className="rounded-xl border border-[#5865F2]/25 bg-[#5865F2]/5 p-4 space-y-4">

          {/* Header */}
          <div className="flex items-center gap-2.5">
            <svg width="18" height="18" viewBox="0 0 24 24" fill="#5865F2" aria-hidden>
              <path d="M20.317 4.37a19.791 19.791 0 0 0-4.885-1.515.074.074 0 0 0-.079.037c-.21.375-.444.864-.608 1.25a18.27 18.27 0 0 0-5.487 0 12.64 12.64 0 0 0-.617-1.25.077.077 0 0 0-.079-.037A19.736 19.736 0 0 0 3.677 4.37a.07.07 0 0 0-.032.027C.533 9.046-.32 13.58.099 18.057a.082.082 0 0 0 .031.057 19.9 19.9 0 0 0 5.993 3.03.078.078 0 0 0 .084-.028 14.09 14.09 0 0 0 1.226-1.994.076.076 0 0 0-.041-.106 13.107 13.107 0 0 1-1.872-.892.077.077 0 0 1-.008-.128 10.2 10.2 0 0 0 .372-.292.074.074 0 0 1 .077-.01c3.928 1.793 8.18 1.793 12.062 0a.074.074 0 0 1 .078.01c.12.098.246.198.373.292a.077.077 0 0 1-.006.127 12.299 12.299 0 0 1-1.873.892.077.077 0 0 0-.041.107c.36.698.772 1.362 1.225 1.993a.076.076 0 0 0 .084.028 19.839 19.839 0 0 0 6.002-3.03.077.077 0 0 0 .032-.054c.5-5.177-.838-9.674-3.549-13.66a.061.061 0 0 0-.031-.03zM8.02 15.33c-1.183 0-2.157-1.085-2.157-2.419 0-1.333.956-2.419 2.157-2.419 1.21 0 2.176 1.096 2.157 2.42 0 1.333-.956 2.418-2.157 2.418zm7.975 0c-1.183 0-2.157-1.085-2.157-2.419 0-1.333.955-2.419 2.157-2.419 1.21 0 2.176 1.096 2.157 2.42 0 1.333-.946 2.418-2.157 2.418z"/>
            </svg>
            <span className="text-sm font-medium text-text-primary">Discord Account</span>
          </div>

          {/* ── Verified state ── */}
          {discordAuth ? (
            <div className="rounded-lg border border-[#5865F2]/30 bg-[#5865F2]/10 p-3 flex items-center gap-3">
              {discordAuth.avatar && (
                <img
                  src={discordAuth.avatar}
                  alt="avatar"
                  className="h-10 w-10 rounded-full ring-2 ring-[#5865F2]/40 shrink-0"
                  onError={(e) => { e.currentTarget.style.display = "none"; }}
                />
              )}
              <div className="min-w-0 flex-1">
                <div className="flex items-center gap-1.5">
                  <span className="text-sm font-semibold text-text-primary">
                    {discordAuth.globalName || discordAuth.username}
                  </span>
                  <ShieldCheck size={13} className="text-[#5865F2]" />
                </div>
                <div className="text-[11px] text-[#5865F2] mt-0.5">
                  Verified via Discord DM · bot detects you automatically
                </div>
                {discordAuth.roles?.length > 0 && (
                  <div className="mt-1.5 flex flex-wrap gap-1">
                    {discordAuth.roles.slice(0, 5).map((r) => (
                      <span
                        key={r.id}
                        className="rounded px-1.5 py-0.5 text-[9px] font-medium"
                        style={{
                          background: r.color && r.color !== "#000000" ? `${r.color}22` : "rgba(88,101,242,0.15)",
                          color:      r.color && r.color !== "#000000" ? r.color       : "#5865F2",
                          border:     `1px solid ${r.color && r.color !== "#000000" ? `${r.color}44` : "rgba(88,101,242,0.3)"}`,
                        }}
                      >
                        {r.name}
                      </span>
                    ))}
                    {discordAuth.roles.length > 5 && (
                      <span className="text-[9px] text-text-faint">
                        +{discordAuth.roles.length - 5} more
                      </span>
                    )}
                  </div>
                )}
              </div>
              <button
                type="button"
                onClick={async () => {
                  const confirmed = window.confirm(
                    "Unlinking will lock the app until you verify a Discord account again. Continue?"
                  );
                  if (!confirmed) return;
                  const res = await window.api.discordAuthLogout();
                  if (res.success) {
                    setDiscordAuth(null);
                    setDvStep("idle");
                    setDvUsername("");
                    setDvCode("");
                  }
                }}
                className="shrink-0 flex items-center gap-1.5 rounded-lg border border-danger/30 px-3 py-1.5 text-xs text-danger transition hover:bg-danger/10"
              >
                <LogOut size={11} />
                Switch Account
              </button>
            </div>

          ) : dvStep === "code" || dvStep === "confirming" ? (
            /* ── Step 2: enter the code ── */
            <div className="space-y-3">
              <div className="rounded-lg border border-[#5865F2]/20 bg-[#5865F2]/8 px-3 py-2.5 text-xs text-text-muted">
                📨 A 6-digit code was sent to <strong className="text-text-primary">@{dvUsername}</strong>'s Discord DMs.
                Check your messages and enter it below.
              </div>

              <div className="flex gap-2">
                <input
                  ref={codeRef}
                  value={dvCode}
                  onChange={(e) => setDvCode(e.target.value.replace(/\D/g, "").slice(0, 6))}
                  onKeyDown={(e) => e.key === "Enter" && dvCode.length === 6 && document.getElementById("dv-confirm-btn").click()}
                  className={`${inputCls} flex-1 text-center font-mono tracking-[0.4em] text-lg`}
                  placeholder="000000"
                  maxLength={6}
                  inputMode="numeric"
                  autoFocus
                />
                <button
                  id="dv-confirm-btn"
                  type="button"
                  disabled={dvCode.length !== 6 || dvStep === "confirming"}
                  onClick={async () => {
                    setDvStep("confirming");
                    setDvError("");
                    const res = await window.api.discordVerifyConfirm(dvCode);
                    if (res.success) {
                      setDiscordAuth(res.data);
                      setDvStep("idle");
                      setDvUsername("");
                      setDvCode("");
                      showToast(`Verified as ${res.data.globalName || res.data.username}!`, "success");
                    } else {
                      setDvStep("code");
                      setDvCode("");
                      setDvError(res.error || "Verification failed.");
                    }
                  }}
                  className="inline-flex items-center gap-2 rounded-lg bg-[#5865F2] px-4 py-2 text-sm font-semibold text-white transition hover:bg-[#4752c4] disabled:cursor-not-allowed disabled:opacity-50"
                >
                  {dvStep === "confirming"
                    ? <RefreshCw size={13} className="animate-spin" />
                    : <ShieldCheck size={13} />
                  }
                  Verify
                </button>
              </div>

              {dvError && (
                <p className="text-xs text-danger">{dvError}</p>
              )}

              <button
                type="button"
                className="text-[11px] text-text-faint underline hover:text-text-muted"
                onClick={() => { setDvStep("idle"); setDvCode(""); setDvError(""); }}
              >
                ← Start over
              </button>
            </div>

          ) : (
            /* ── Step 1: enter username ── */
            <div className="space-y-3">
              <p className="text-xs text-text-muted">
                Enter your Discord username. The bot will send you a verification code via DM —
                no passwords or tokens involved.
              </p>
              <div className="flex gap-2">
                <input
                  value={dvUsername}
                  onChange={(e) => setDvUsername(e.target.value)}
                  onKeyDown={(e) => e.key === "Enter" && dvUsername.trim() && document.getElementById("dv-send-btn").click()}
                  className={`${inputCls} flex-1`}
                  placeholder="your_username"
                  autoComplete="off"
                  spellCheck={false}
                  disabled={dvStep === "sending"}
                />
                <button
                  id="dv-send-btn"
                  type="button"
                  disabled={!dvUsername.trim() || dvStep === "sending"}
                  onClick={async () => {
                    setDvStep("sending");
                    setDvError("");
                    const res = await window.api.discordVerifyStart(dvUsername.trim());
                    if (res.success) {
                      setDvStep("code");
                      setTimeout(() => codeRef.current?.focus(), 50);
                    } else {
                      setDvStep("idle");
                      setDvError(res.error || "Could not send code.");
                    }
                  }}
                  className="inline-flex items-center gap-2 rounded-lg bg-[#5865F2] px-4 py-2 text-sm font-semibold text-white transition hover:bg-[#4752c4] disabled:cursor-not-allowed disabled:opacity-50"
                >
                  {dvStep === "sending"
                    ? <RefreshCw size={13} className="animate-spin" />
                    : <Send size={13} />
                  }
                  Send Code
                </button>
              </div>
              {dvError && (
                <p className="text-xs text-danger">{dvError}</p>
              )}
            </div>
          )}
        </div>

        <SettingRow
          label="Default Install Location"
          hint="Where new servers are installed by default."
        >
          <div className="flex gap-2">
            <input
              {...field("defaultInstallLocation")}
              className={`${inputCls} flex-1`}
              placeholder="e.g. C:\\Users\\You\\MinecraftServers"
            />
            <button
              type="button"
              onClick={async () => {
                const res = await window.api.selectFolder();
                if (res.success && res.data?.path) {
                  setForm((prev) => ({ ...prev, defaultInstallLocation: res.data.path }));
                }
              }}
              className="inline-flex items-center gap-1.5 rounded-lg border border-border px-3 py-2 text-xs text-text-muted transition hover:border-accent/40 hover:text-accent"
            >
              <FolderOpen size={13} />
              Browse
            </button>
          </div>
        </SettingRow>
      </div>

      <div className="mt-6 flex items-center gap-3">
        <button
          type="button"
          id="save-settings-btn"
          onClick={save}
          className={`inline-flex items-center gap-2 rounded-lg px-5 py-2.5 text-sm font-medium transition-all ${
            saved
              ? "bg-accent/20 text-accent ring-1 ring-accent/40"
              : "bg-accent/10 text-accent ring-1 ring-accent/30 hover:bg-accent hover:text-bg-primary hover:shadow-glow-green"
          }`}
        >
          {saved ? <Check size={14} /> : <Save size={14} />}
          {saved ? "Saved!" : "Save Settings"}
        </button>
        <button
          type="button"
          onClick={() => window.api.openExternal("https://github.com/sponsors/trazhub")}
          className="inline-flex items-center gap-2 rounded-lg bg-gold/10 px-5 py-2.5 text-sm font-medium text-gold ring-1 ring-gold/30 transition hover:bg-gold hover:text-bg-primary hover:shadow-glow-gold"
        >
          <HandCoins size={14} />
          Sponsor
        </button>
      </div>
    </div>
  );
}
