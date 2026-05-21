/**
 * Discord DM Verification — no OAuth secrets needed.
 *
 * Flow:
 *  1. App calls startVerification(username, botUrl, botSecret)
 *     → bot finds the member and DMs them a 6-digit code
 *  2. User reads the code from Discord, types it in the app
 *  3. App calls confirmVerification(code, botUrl, botSecret)
 *     → bot validates, returns userId + roles
 *  4. App stores the identity — no tokens, no secrets stored
 */

async function postToBot(path, body, botUrl, botSecret) {
  const url = new URL(path, botUrl.endsWith("/") ? botUrl : botUrl + "/").toString();

  const headers = { "Content-Type": "application/json" };
  if (botSecret) headers["x-bot-secret"] = botSecret;

  let res;
  try {
    res = await fetch(url, {
      method: "POST",
      headers,
      body: JSON.stringify(body),
      signal: AbortSignal.timeout(12_000),
    });
  } catch (err) {
    if (err.name === "TimeoutError" || err.name === "AbortError") {
      throw new Error("Bot did not respond in time. Check that the bot is running on your VPS.");
    }
    throw new Error(
      `Cannot reach the VoxelPort bot at ${botUrl}.\n` +
      `Make sure the bot is running and port ${new URL(botUrl).port || 80} is open on your VPS.`
    );
  }

  let data = {};
  try { data = await res.json(); } catch {}

  if (res.status === 401) {
    throw new Error("Bot rejected the request — BOT_SECRET in the app does not match the bot.");
  }
  if (!res.ok) {
    throw new Error(data.error || `Bot returned error ${res.status}`);
  }

  return data;
}

export async function startVerification(username, botUrl, botSecret) {
  return postToBot("verify/start", { username: String(username).trim() }, botUrl, botSecret);
}

export async function confirmVerification(code, botUrl, botSecret) {
  return postToBot("verify/confirm", { code: String(code).trim() }, botUrl, botSecret);
}
