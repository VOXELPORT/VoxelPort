import React from "react";
import { Link, useLocation, useNavigate } from "react-router-dom";
import { Download, HandCoins, HardDrive, Package, Plug, Settings, Users } from "lucide-react";
import AppLogo from "./AppLogo.jsx";

const navItems = [
  { to: "/",          label: "Servers",        icon: HardDrive, desc: "Manage servers"  },
  { to: "/install",   label: "Install Server",  icon: Download,  desc: "New server"      },
  { to: "/mods",      label: "Mods & Plugins",  icon: Plug,      desc: "Browse & install"},
  { to: "/join-room", label: "Join Room",        icon: Users,     desc: "Multiplayer"     },
  { to: "/settings",  label: "Settings",         icon: Settings,  desc: "Configuration"   },
];

// Minimal Discord wordmark icon (SVG path)
function DiscordIcon({ size = 14, className = "" }) {
  return (
    <svg
      width={size}
      height={size}
      viewBox="0 0 24 24"
      fill="currentColor"
      className={className}
      aria-hidden
    >
      <path d="M20.317 4.37a19.791 19.791 0 0 0-4.885-1.515.074.074 0 0 0-.079.037c-.21.375-.444.864-.608 1.25a18.27 18.27 0 0 0-5.487 0 12.64 12.64 0 0 0-.617-1.25.077.077 0 0 0-.079-.037A19.736 19.736 0 0 0 3.677 4.37a.07.07 0 0 0-.032.027C.533 9.046-.32 13.58.099 18.057a.082.082 0 0 0 .031.057 19.9 19.9 0 0 0 5.993 3.03.078.078 0 0 0 .084-.028 14.09 14.09 0 0 0 1.226-1.994.076.076 0 0 0-.041-.106 13.107 13.107 0 0 1-1.872-.892.077.077 0 0 1-.008-.128 10.2 10.2 0 0 0 .372-.292.074.074 0 0 1 .077-.01c3.928 1.793 8.18 1.793 12.062 0a.074.074 0 0 1 .078.01c.12.098.246.198.373.292a.077.077 0 0 1-.006.127 12.299 12.299 0 0 1-1.873.892.077.077 0 0 0-.041.107c.36.698.772 1.362 1.225 1.993a.076.076 0 0 0 .084.028 19.839 19.839 0 0 0 6.002-3.03.077.077 0 0 0 .032-.054c.5-5.177-.838-9.674-3.549-13.66a.061.061 0 0 0-.031-.03zM8.02 15.33c-1.183 0-2.157-1.085-2.157-2.419 0-1.333.956-2.419 2.157-2.419 1.21 0 2.176 1.096 2.157 2.42 0 1.333-.956 2.418-2.157 2.418zm7.975 0c-1.183 0-2.157-1.085-2.157-2.419 0-1.333.955-2.419 2.157-2.419 1.21 0 2.176 1.096 2.157 2.42 0 1.333-.946 2.418-2.157 2.418z" />
    </svg>
  );
}

export default function Sidebar({ version, discordAuth }) {
  const location = useLocation();
  const navigate = useNavigate();

  return (
    <aside className="flex w-60 flex-col border-r border-border bg-bg-panel pixel-bg">

      {/* ── Logo ── */}
      <div className="relative overflow-hidden border-b border-border px-4 py-4">
        <div className="pointer-events-none absolute -left-4 -top-4 h-24 w-24 rounded-full bg-accent/6 blur-2xl" />
        <div className="flex items-center gap-3">
          <div className="relative flex h-11 w-11 shrink-0 items-center justify-center overflow-hidden rounded-xl bg-accent/8 ring-1 ring-accent/25 shadow-[0_0_14px_rgba(74,222,128,0.12)]">
            <AppLogo size={40} className="h-10 w-10 rounded-lg" />
            <span className="absolute -right-0.5 -top-0.5 h-2.5 w-2.5 rounded-full bg-accent shadow-[0_0_8px_rgba(74,222,128,0.8)] animate-pulse-slow" />
          </div>
          <div className="min-w-0">
            <div className="font-pixel text-sm font-bold leading-tight tracking-wide text-gradient">
              VoxelPort
            </div>
            <div className="mt-0.5 text-[10px] leading-tight text-text-faint">
              Server Manager
            </div>
          </div>
        </div>
      </div>

      {/* ── Navigation ── */}
      <nav className="flex-1 space-y-0.5 overflow-y-auto p-3">
        <div className="mb-3 px-2 text-[9px] font-bold uppercase tracking-[0.15em] text-text-faint/60">
          Navigation
        </div>

        {navItems.map((item) => {
          const Icon   = item.icon;
          const active = item.to === "/"
            ? location.pathname === item.to
            : location.pathname.startsWith(item.to);

          return (
            <Link
              key={item.to}
              to={item.to}
              className={`
                nav-active-bar group flex items-center gap-3 rounded-lg px-3 py-2.5 text-sm
                transition-all duration-150
                ${active
                  ? "bg-accent/10 text-accent"
                  : "text-text-muted hover:bg-bg-hover hover:text-text-primary"}
              `}
            >
              <div className={`
                flex h-7 w-7 shrink-0 items-center justify-center rounded-md transition-all
                ${active
                  ? "bg-accent/20 ring-1 ring-accent/30 shadow-[0_0_8px_rgba(74,222,128,0.2)]"
                  : "bg-bg-hover/60 group-hover:bg-bg-hover"}
              `}>
                <Icon size={14} className={`transition-colors ${active ? "text-accent" : "text-text-faint group-hover:text-text-muted"}`} />
              </div>
              <div className="min-w-0 flex-1">
                <div className="text-xs font-medium leading-tight">{item.label}</div>
                <div className="text-[10px] leading-tight text-text-faint mt-px">{item.desc}</div>
              </div>
              {active && (
                <span className="h-1.5 w-1.5 shrink-0 rounded-full bg-accent shadow-[0_0_6px_rgba(74,222,128,0.7)] animate-pulse-slow" />
              )}
            </Link>
          );
        })}
      </nav>

      {/* ── Footer ── */}
      <div className="border-t border-border px-3 py-3 space-y-2">

        {/* Discord account chip — always shown (gate ensures it's always linked) */}
        {discordAuth && (
          <button
            type="button"
            onClick={() => navigate("/settings")}
            title="Discord verified — click to manage"
            className="flex w-full items-center gap-2.5 rounded-lg border border-[#5865F2]/30 bg-[#5865F2]/8 px-3 py-2 text-left transition-all hover:border-[#5865F2]/50 hover:bg-[#5865F2]/15"
          >
            {discordAuth.avatar ? (
              <img
                src={discordAuth.avatar}
                alt="avatar"
                className="h-6 w-6 rounded-full ring-1 ring-[#5865F2]/40 shrink-0"
                onError={(e) => { e.currentTarget.style.display = "none"; }}
              />
            ) : (
              <div className="flex h-6 w-6 shrink-0 items-center justify-center rounded-full bg-[#5865F2]/20 ring-1 ring-[#5865F2]/40">
                <DiscordIcon size={12} className="text-[#5865F2]" />
              </div>
            )}
            <div className="min-w-0 flex-1">
              <div className="truncate text-[11px] font-medium text-text-primary leading-tight">
                {discordAuth.globalName || discordAuth.username}
              </div>
              <div className="flex items-center gap-1 mt-0.5">
                <DiscordIcon size={9} className="text-[#5865F2] shrink-0" />
                <span className="text-[9px] text-[#5865F2]">Verified</span>
              </div>
            </div>
          </button>
        )}

        <button
          type="button"
          onClick={() => window.api.openExternal("https://discord.gg/dYXqe6tvSN")}
          className="flex w-full items-center justify-center gap-2 rounded-lg border border-[#5865F2]/25 bg-[#5865F2]/8 px-3 py-2 text-xs font-medium text-[#5865F2] transition-all hover:bg-[#5865F2]/20 hover:border-[#5865F2]/40"
        >
          <DiscordIcon size={13} className="text-[#5865F2]" />
          Join our Discord
        </button>

        <button
          type="button"
          onClick={() => window.api.openExternal("https://github.com/sponsors/trazhub")}
          className="flex w-full items-center justify-center gap-2 rounded-lg bg-gold/8 px-3 py-2 text-xs font-medium text-gold ring-1 ring-gold/25 transition-all hover:bg-gold hover:text-bg-primary hover:shadow-glow-gold"
        >
          <HandCoins size={13} />
          Sponsor VoxelPort
        </button>

        <div className="flex items-center justify-between px-1">
          <div className="flex items-center gap-1.5 text-text-faint">
            <Package size={10} />
            <span className="text-[10px]">v{version}</span>
          </div>
          <span className="rounded-md bg-accent/12 px-2 py-0.5 text-[9px] font-bold tracking-widest text-accent ring-1 ring-accent/20">
            STABLE
          </span>
        </div>
      </div>

    </aside>
  );
}
