export const ROOM_CODE_REGEX = /^[A-Z0-9]{6}$/;
export const DEFAULT_RELAY_URL = "wss://voxelportrelay.qzz.io";

export const ALLOWED_DOMAINS = new Set([
  "api.papermc.io",
  "api.purpurmc.org",
  "launchermeta.mojang.com",
  "s3.amazonaws.com",
  "meta.fabricmc.net",
  "maven.fabricmc.net",
  "files.minecraftforge.net",
  "maven.minecraftforge.net",
  "maven.neoforged.net",
  "cdn.modrinth.com",
  "hangar.papermc.io"
]);

export function stripLineFeed(value) {
  return String(value || "").replace(/[\r\n]/g, "");
}
