import React, { useState } from "react";
import {
  Clock, Cpu, FolderOpen, MemoryStick, MoreVertical,
  Pencil, Play, Power, Trash2, Users, Wrench, Zap
} from "lucide-react";
import Console from "./Console.jsx";

const SERVER_TYPE_STYLES = {
  paper:    { color: "text-emerald-400", bg: "bg-emerald-400/10", dot: "bg-emerald-400", ring: "ring-emerald-400/30", bar: "from-emerald-500/60 via-emerald-400 to-emerald-500/60", label: "Paper" },
  purpur:   { color: "text-purple-400",  bg: "bg-purple-400/10",  dot: "bg-purple-400",  ring: "ring-purple-400/30",  bar: "from-purple-500/60 via-purple-400 to-purple-500/60",   label: "Purpur" },
  vanilla:  { color: "text-gold",        bg: "bg-gold/10",        dot: "bg-gold",        ring: "ring-gold/30",        bar: "from-gold/60 via-gold to-gold/60",                     label: "Vanilla" },
  fabric:   { color: "text-sky-400",     bg: "bg-sky-400/10",     dot: "bg-sky-400",     ring: "ring-sky-400/30",     bar: "from-sky-500/60 via-sky-400 to-sky-500/60",            label: "Fabric" },
  forge:    { color: "text-orange-400",  bg: "bg-orange-400/10",  dot: "bg-orange-400",  ring: "ring-orange-400/30",  bar: "from-orange-500/60 via-orange-400 to-orange-500/60",  label: "Forge" },
  neoforge: { color: "text-orange-300",  bg: "bg-orange-300/10",  dot: "bg-orange-300",  ring: "ring-orange-300/30",  bar: "from-orange-400/60 via-orange-300 to-orange-400/60",  label: "NeoForge" },
};

function StatPill({ icon: Icon, value, label, color = "text-text-muted" }) {
  return (
    <div className="flex items-center gap-1.5 rounded-lg border border-border/70 bg-bg-primary/60 px-2.5 py-1.5 text-xs transition-colors hover:border-border">
      <Icon size={11} className={`shrink-0 ${color}`} />
      <span className={`font-semibold tabular-nums ${color}`}>{value}</span>
      {label && <span className="text-text-faint">{label}</span>}
    </div>
  );
}

function formatUptime(seconds) {
  if (!seconds) return "0s";
  const h = Math.floor(seconds / 3600);
  const m = Math.floor((seconds % 3600) / 60);
  const s = seconds % 60;
  if (h > 0) return `${h}h ${m}m`;
  if (m > 0) return `${m}m ${s}s`;
  return `${s}s`;
}

export default function ServerCard({
  server, stats, consoleLines, updateCount = 0,
  onStart, onStop, onOpenRoom, onMods, onRename, onRemove, onOpenFolder, onSendCommand, onClearConsole
}) {
  const [menuOpen, setMenuOpen] = useState(false);

  const status   = server.status || "stopped";
  const online   = status === "running";
  const starting = status === "starting";
  const typeKey  = String(server.serverType || "").toLowerCase();
  const type     = SERVER_TYPE_STYLES[typeKey] || {
    color: "text-text-muted", bg: "bg-bg-hover", dot: "bg-text-faint",
    ring: "ring-transparent", bar: "from-border/60 via-border to-border/60",
    label: server.serverType || "Unknown"
  };

  const configuredMb = Number(server.ram || 2048);
  const ramDisplay   = online && stats
    ? `${(Number(stats.ramMb || 0) / 1024).toFixed(1)}/${(configuredMb / 1024).toFixed(0)} GB`
    : `${(configuredMb / 1024).toFixed(0)} GB`;

  return (
    <section className={`
      group relative flex flex-col rounded-xl border transition-all duration-300 animate-slide-up overflow-hidden
      ${online
        ? "border-accent/25 bg-bg-card animate-glow-breathe"
        : "border-border bg-bg-card shadow-card hover:border-accent/15 hover:shadow-card-hover"}
    `}>

      {/* ── Top status bar ── */}
      <div className={`h-[3px] w-full transition-all duration-500 bg-gradient-to-r ${
        online   ? `${type.bar} shadow-[0_1px_10px_rgba(74,222,128,0.5)]` :
        starting ? "from-gold/50 via-gold to-gold/50 animate-pulse" :
        "from-border/0 via-border/40 to-border/0"
      }`} />

      <div className="flex flex-col gap-3.5 p-4">

        {/* ── Header row ── */}
        <div className="flex items-start justify-between gap-2">
          <div className="min-w-0 flex-1">
            <h3 className="truncate text-[15px] font-semibold tracking-tight text-text-primary leading-snug">
              {server.name || server.id}
            </h3>

            <div className="mt-2 flex flex-wrap items-center gap-1.5">
              {/* Server type badge */}
              <span className={`inline-flex items-center gap-1.5 rounded-md px-2 py-0.5 text-[11px] font-medium ring-1 ${type.bg} ${type.ring} ${type.color}`}>
                <span className={`h-1.5 w-1.5 rounded-full ${type.dot}`} />
                {type.label}{server.mcVersion ? ` ${server.mcVersion}` : ""}
              </span>

              {/* Status badge */}
              <span className="inline-flex items-center gap-1.5 rounded-md bg-bg-primary/50 px-2 py-0.5 text-[11px]">
                <span className="relative flex h-2 w-2">
                  {(online || starting) && (
                    <span className={`absolute inset-0 rounded-full animate-status-ping ${online ? "bg-accent" : "bg-gold"} opacity-50`} />
                  )}
                  <span className={`relative block h-2 w-2 rounded-full ${
                    online   ? "bg-accent shadow-[0_0_6px_rgba(74,222,128,0.8)]" :
                    starting ? "bg-gold  shadow-[0_0_6px_rgba(251,191,36,0.7)]" :
                    "bg-text-faint"
                  }`} />
                </span>
                <span className={`font-medium ${
                  online ? "text-accent" : starting ? "text-gold" : "text-text-faint"
                }`}>
                  {online ? "Online" : starting ? "Starting…" : status === "stopping" ? "Stopping…" : "Offline"}
                </span>
              </span>

              {/* Update badge */}
              {updateCount > 0 && (
                <span className="inline-flex items-center gap-1 rounded-md bg-warning/15 px-2 py-0.5 text-[11px] font-semibold text-warning ring-1 ring-warning/25">
                  ↑ {updateCount} update{updateCount === 1 ? "" : "s"}
                </span>
              )}
            </div>
          </div>

          {/* ── Context menu ── */}
          <div className="relative shrink-0">
            <button
              type="button"
              onClick={() => setMenuOpen((v) => !v)}
              className="rounded-lg p-1.5 text-text-faint transition-colors hover:bg-bg-hover hover:text-text-primary"
            >
              <MoreVertical size={15} />
            </button>
            {menuOpen && (
              <>
                <div className="fixed inset-0 z-10" onClick={() => setMenuOpen(false)} />
                <div className="absolute right-0 z-20 mt-1 w-44 animate-fade-in rounded-xl border border-border bg-bg-panel py-1.5 shadow-card text-xs">
                  <button type="button"
                    onClick={() => { onRename?.(); setMenuOpen(false); }}
                    className="flex w-full items-center gap-2.5 px-3 py-2 text-left text-text-muted transition-colors hover:bg-bg-hover hover:text-text-primary">
                    <Pencil size={13} /> Rename
                  </button>
                  <button type="button"
                    onClick={() => { onOpenFolder?.(); setMenuOpen(false); }}
                    className="flex w-full items-center gap-2.5 px-3 py-2 text-left text-text-muted transition-colors hover:bg-bg-hover hover:text-text-primary">
                    <FolderOpen size={13} /> Open Folder
                  </button>
                  <div className="my-1 border-t border-border" />
                  <button type="button"
                    onClick={() => { onRemove?.(); setMenuOpen(false); }}
                    className="flex w-full items-center gap-2.5 px-3 py-2 text-left text-danger transition-colors hover:bg-danger/10">
                    <Trash2 size={13} /> Remove Server
                  </button>
                </div>
              </>
            )}
          </div>
        </div>

        {/* ── Stats ── */}
        <div className="flex flex-wrap gap-1.5">
          <StatPill
            icon={Users}
            value={stats?.playerCount ?? server.playerCount ?? 0}
            label="players"
            color={online ? "text-accent" : "text-text-faint"}
          />
          <StatPill icon={MemoryStick} value={ramDisplay} color="text-diamond" />
          <StatPill
            icon={Cpu}
            value={online && stats ? `${stats.cpuPercent ?? 0}%` : "—"}
            label="cpu"
            color="text-gold"
          />
          {online && stats?.uptime != null && (
            <StatPill icon={Clock} value={formatUptime(stats.uptime)} color="text-emerald" />
          )}
          <StatPill icon={Zap} value={`:${server.port}`} color="text-text-muted" />
        </div>

        {/* ── Action buttons ── */}
        <div className="flex flex-wrap gap-2">
          {online ? (
            <button type="button" onClick={onStop}
              className="inline-flex items-center gap-1.5 rounded-lg bg-danger/10 px-3.5 py-2 text-xs font-medium text-danger ring-1 ring-danger/30 transition-all hover:bg-danger hover:text-white">
              <Power size={12} /> Stop
            </button>
          ) : (
            <button type="button" onClick={onStart}
              className="inline-flex items-center gap-1.5 rounded-lg bg-accent/10 px-3.5 py-2 text-xs font-medium text-accent ring-1 ring-accent/30 transition-all hover:bg-accent hover:text-bg-primary hover:shadow-glow-green">
              <Play size={12} className="fill-current" /> Start
            </button>
          )}

          {online && (
            <button type="button" onClick={onOpenRoom}
              className="inline-flex items-center gap-1.5 rounded-lg bg-diamond/10 px-3.5 py-2 text-xs font-medium text-diamond ring-1 ring-diamond/30 transition-all hover:bg-diamond hover:text-bg-primary hover:shadow-glow-blue">
              <Users size={12} /> Share Room
            </button>
          )}

          <button type="button" onClick={onMods}
            className="inline-flex items-center gap-1.5 rounded-lg bg-gold/10 px-3.5 py-2 text-xs font-medium text-gold ring-1 ring-gold/30 transition-all hover:bg-gold hover:text-bg-primary hover:shadow-glow-gold">
            <Wrench size={12} /> Mods
            {updateCount > 0 && (
              <span className="rounded-full bg-warning/25 px-1.5 py-px text-[10px] font-bold text-warning">
                {updateCount}
              </span>
            )}
          </button>
        </div>

        {/* ── Console ── */}
        <div className="overflow-hidden rounded-xl border border-border/60 bg-[#07090c]">
          {/* Terminal header */}
          <div className="flex items-center justify-between border-b border-border/50 bg-[#0c0e13] px-3 py-1.5">
            <div className="flex items-center gap-2.5">
              <div className="flex gap-1.5">
                <span className="h-2.5 w-2.5 rounded-full bg-redstone/50 transition-colors hover:bg-redstone/80" />
                <span className="h-2.5 w-2.5 rounded-full bg-gold/50     transition-colors hover:bg-gold/80" />
                <span className="h-2.5 w-2.5 rounded-full bg-accent/50   transition-colors hover:bg-accent/80" />
              </div>
              <span className="font-mono text-[10px] tracking-widest text-text-faint select-none">
                {server.name || server.id} — console
              </span>
            </div>
            {online && (
              <span className="flex items-center gap-1.5 text-[10px] font-medium text-accent">
                <span className="h-1.5 w-1.5 animate-pulse-slow rounded-full bg-accent" />
                live
              </span>
            )}
          </div>
          <Console lines={consoleLines} onSendCommand={onSendCommand} onClear={onClearConsole} />
        </div>

      </div>
    </section>
  );
}
