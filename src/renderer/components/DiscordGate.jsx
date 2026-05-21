import React, { useRef, useState } from "react";
import { RefreshCw, Send, ShieldCheck } from "lucide-react";
import AppLogo from "./AppLogo.jsx";

const inputCls =
  "w-full rounded-lg border border-border bg-bg-input px-3 py-2.5 text-sm text-text-primary placeholder:text-text-faint transition focus:border-[#5865F2]/60 focus:outline-none";

function DiscordIcon() {
  return (
    <svg width="20" height="20" viewBox="0 0 24 24" fill="#5865F2" aria-hidden>
      <path d="M20.317 4.37a19.791 19.791 0 0 0-4.885-1.515.074.074 0 0 0-.079.037c-.21.375-.444.864-.608 1.25a18.27 18.27 0 0 0-5.487 0 12.64 12.64 0 0 0-.617-1.25.077.077 0 0 0-.079-.037A19.736 19.736 0 0 0 3.677 4.37a.07.07 0 0 0-.032.027C.533 9.046-.32 13.58.099 18.057a.082.082 0 0 0 .031.057 19.9 19.9 0 0 0 5.993 3.03.078.078 0 0 0 .084-.028 14.09 14.09 0 0 0 1.226-1.994.076.076 0 0 0-.041-.106 13.107 13.107 0 0 1-1.872-.892.077.077 0 0 1-.008-.128 10.2 10.2 0 0 0 .372-.292.074.074 0 0 1 .077-.01c3.928 1.793 8.18 1.793 12.062 0a.074.074 0 0 1 .078.01c.12.098.246.198.373.292a.077.077 0 0 1-.006.127 12.299 12.299 0 0 1-1.873.892.077.077 0 0 0-.041.107c.36.698.772 1.362 1.225 1.993a.076.076 0 0 0 .084.028 19.839 19.839 0 0 0 6.002-3.03.077.077 0 0 0 .032-.054c.5-5.177-.838-9.674-3.549-13.66a.061.061 0 0 0-.031-.03zM8.02 15.33c-1.183 0-2.157-1.085-2.157-2.419 0-1.333.956-2.419 2.157-2.419 1.21 0 2.176 1.096 2.157 2.42 0 1.333-.956 2.418-2.157 2.418zm7.975 0c-1.183 0-2.157-1.085-2.157-2.419 0-1.333.955-2.419 2.157-2.419 1.21 0 2.176 1.096 2.157 2.42 0 1.333-.946 2.418-2.157 2.418z" />
    </svg>
  );
}

export default function DiscordGate({ onVerified }) {
  const [dvUsername, setDvUsername] = useState("");
  const [dvCode, setDvCode]         = useState("");
  const [dvStep, setDvStep]         = useState("idle"); // idle | sending | code | confirming
  const [dvError, setDvError]       = useState("");
  const codeRef = useRef(null);

  const sendCode = async () => {
    if (!dvUsername.trim()) return;
    setDvStep("sending");
    setDvError("");
    const res = await window.api.discordVerifyStart(dvUsername.trim());
    if (res.success) {
      setDvStep("code");
      setTimeout(() => codeRef.current?.focus(), 60);
    } else {
      setDvStep("idle");
      setDvError(res.error || "Could not send code.");
    }
  };

  const confirmCode = async () => {
    if (dvCode.length !== 6) return;
    setDvStep("confirming");
    setDvError("");
    const res = await window.api.discordVerifyConfirm(dvCode);
    if (res.success) {
      onVerified(res.data);
    } else {
      setDvStep("code");
      setDvCode("");
      setDvError(res.error || "Verification failed.");
    }
  };

  return (
    <div className="flex h-screen flex-col items-center justify-center bg-bg-primary px-4 pixel-bg">

      {/* Glow backdrop */}
      <div className="pointer-events-none absolute inset-0 flex items-center justify-center">
        <div className="h-[500px] w-[500px] rounded-full bg-[#5865F2]/5 blur-[120px]" />
      </div>

      <div className="relative w-full max-w-sm">

        {/* Logo */}
        <div className="mb-8 flex flex-col items-center gap-3">
          <div className="flex h-16 w-16 items-center justify-center rounded-2xl bg-accent/10 ring-1 ring-accent/25 shadow-[0_0_32px_rgba(74,222,128,0.15)]">
            <AppLogo size={52} className="h-13 w-13 rounded-xl" />
          </div>
          <div className="text-center">
            <div className="font-pixel text-2xl text-gradient">VoxelPort</div>
            <div className="mt-1 text-xs text-text-faint">Server Manager</div>
          </div>
        </div>

        {/* Card */}
        <div className="rounded-2xl border border-border bg-bg-panel p-6 shadow-2xl">

          {/* Header */}
          <div className="mb-5 flex items-center gap-2.5">
            <DiscordIcon />
            <div>
              <h2 className="text-sm font-semibold text-text-primary">
                Verify your Discord account
              </h2>
              <p className="text-[11px] text-text-faint mt-0.5">
                Required to use VoxelPort
              </p>
            </div>
          </div>

          {dvStep === "code" || dvStep === "confirming" ? (
            /* ── Step 2: enter code ── */
            <div className="space-y-3">
              <div className="rounded-lg border border-[#5865F2]/20 bg-[#5865F2]/8 px-3 py-2.5 text-xs text-text-muted leading-relaxed">
                📨 Code sent to <strong className="text-text-primary">@{dvUsername}</strong> via Discord DM.
                Check your messages.
              </div>

              <input
                ref={codeRef}
                value={dvCode}
                onChange={(e) => setDvCode(e.target.value.replace(/\D/g, "").slice(0, 6))}
                onKeyDown={(e) => e.key === "Enter" && confirmCode()}
                className={`${inputCls} text-center font-mono tracking-[0.5em] text-xl`}
                placeholder="000000"
                maxLength={6}
                inputMode="numeric"
              />

              {dvError && (
                <p className="text-xs text-danger">{dvError}</p>
              )}

              <button
                type="button"
                disabled={dvCode.length !== 6 || dvStep === "confirming"}
                onClick={confirmCode}
                className="flex w-full items-center justify-center gap-2 rounded-lg bg-[#5865F2] py-2.5 text-sm font-semibold text-white transition hover:bg-[#4752c4] disabled:cursor-not-allowed disabled:opacity-50"
              >
                {dvStep === "confirming"
                  ? <><RefreshCw size={14} className="animate-spin" /> Verifying…</>
                  : <><ShieldCheck size={14} /> Verify & Enter App</>
                }
              </button>

              <button
                type="button"
                onClick={() => { setDvStep("idle"); setDvCode(""); setDvError(""); }}
                className="w-full text-center text-[11px] text-text-faint underline hover:text-text-muted transition"
              >
                ← Use a different username
              </button>
            </div>

          ) : (
            /* ── Step 1: enter username ── */
            <div className="space-y-3">
              <p className="text-xs text-text-muted leading-relaxed">
                Enter your Discord username. The VoxelPort bot will DM you a
                6-digit code — no passwords or tokens needed.
              </p>

              <input
                value={dvUsername}
                onChange={(e) => setDvUsername(e.target.value)}
                onKeyDown={(e) => e.key === "Enter" && sendCode()}
                className={inputCls}
                placeholder="your_username"
                autoComplete="off"
                spellCheck={false}
                disabled={dvStep === "sending"}
                autoFocus
              />

              {dvError && (
                <p className="text-xs text-danger">{dvError}</p>
              )}

              <button
                type="button"
                disabled={!dvUsername.trim() || dvStep === "sending"}
                onClick={sendCode}
                className="flex w-full items-center justify-center gap-2 rounded-lg bg-[#5865F2] py-2.5 text-sm font-semibold text-white transition hover:bg-[#4752c4] disabled:cursor-not-allowed disabled:opacity-50"
              >
                {dvStep === "sending"
                  ? <><RefreshCw size={14} className="animate-spin" /> Sending…</>
                  : <><Send size={14} /> Send Code via DM</>
                }
              </button>
            </div>
          )}
        </div>

        <p className="mt-4 text-center text-[10px] text-text-faint leading-relaxed">
          Not in the server yet?{" "}
          <button
            type="button"
            onClick={() => window.api.openExternal("https://discord.gg/dYXqe6tvSN")}
            className="text-[#5865F2] underline hover:no-underline transition"
          >
            Join discord.gg/dYXqe6tvSN
          </button>
          {" "}first, then come back here.
        </p>
      </div>
    </div>
  );
}
