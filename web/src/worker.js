import { DurableObject } from "cloudflare:workers";

const MAX_MESSAGE = 65536;
const CODE_TTL = 120000;

async function roomName(secret) {
  const digest = await crypto.subtle.digest("SHA-256", new TextEncoder().encode(secret));
  return [...new Uint8Array(digest)].map((b) => b.toString(16).padStart(2, "0")).join("");
}

function post(ws, message) {
  try { ws.send(message); } catch {}
}

function json(body, status = 200) {
  return new Response(JSON.stringify(body), { status, headers: { "content-type": "application/json", "cache-control": "no-store" } });
}

export default {
  async fetch(request, env) {
    const url = new URL(request.url);
    const role = url.pathname === "/ws/tv" ? "tv" : url.pathname === "/ws/phone" ? "phone" : "";
    if (role) {
      if (request.headers.get("upgrade")?.toLowerCase() !== "websocket") return new Response("Expected WebSocket", { status: 426 });
      const secret = url.searchParams.get("room") || "";
      if (!/^[0-9a-f]{64}$/.test(secret)) return new Response("Bad room", { status: 400 });
      const stub = env.ROOMS.get(env.ROOMS.idFromName(await roomName(secret)));
      return stub.fetch(new Request(`https://room/${role}?secret=${secret}`, request));
    }
    const code = url.pathname.match(/^\/api\/code\/([0-9]{6})$/);
    if (code && request.method === "POST") {
      const room = await env.CODES.get(env.CODES.idFromName(code[1])).take();
      return room ? json({ room }) : json({ error: "Kod wygasł albo jest błędny" }, 404);
    }
    return new Response("Not found", { status: 404 });
  },
};

export class Room extends DurableObject {
  async fetch(request) {
    const url = new URL(request.url);
    const role = url.pathname.slice(1);
    const [client, server] = Object.values(new WebSocketPair());
    if (role === "tv") {
      for (const old of this.ctx.getWebSockets("tv")) old.close(4000, "Replaced");
      await this.ctx.storage.put("secret", url.searchParams.get("secret"));
    } else if (this.ctx.getWebSockets("phone").length >= 8) {
      return new Response("Busy", { status: 503 });
    }
    this.ctx.acceptWebSocket(server, [role]);
    this.presence();
    return new Response(null, { status: 101, webSocket: client });
  }

  presence(gone) {
    const live = (tag) => this.ctx.getWebSockets(tag).filter((ws) => ws !== gone);
    const tv = live("tv").length > 0;
    const phones = live("phone").length;
    for (const ws of live("phone")) post(ws, JSON.stringify({ type: "presence", tv }));
    for (const ws of live("tv")) post(ws, JSON.stringify({ type: "presence", phones }));
  }

  async webSocketMessage(ws, message) {
    if (typeof message !== "string" || message.length > MAX_MESSAGE) return;
    const role = this.ctx.getTags(ws)[0];
    if (role === "tv") {
      if (message === '{"type":"code"}') {
        const secret = await this.ctx.storage.get("secret");
        for (let i = 0; i < 5; i++) {
          const code = String(crypto.getRandomValues(new Uint32Array(1))[0] % 1000000).padStart(6, "0");
          if (await this.env.CODES.get(this.env.CODES.idFromName(code)).claim(secret)) { ws.send(JSON.stringify({ type: "code", code, ttl: CODE_TTL })); break; }
        }
        return;
      }
      for (const phone of this.ctx.getWebSockets("phone")) post(phone, message);
    } else {
      const tv = this.ctx.getWebSockets("tv");
      if (!tv.length) { ws.send(JSON.stringify({ type: "error", message: "Telewizor jest wyłączony albo bez internetu" })); return; }
      for (const target of tv) post(target, message);
    }
  }

  webSocketClose(ws, code) {
    try { ws.close(code === 1005 ? 1000 : code); } catch {}
    this.presence(ws);
  }

  webSocketError(ws) { this.presence(ws); }
}

export class Code extends DurableObject {
  async claim(secret) {
    const current = await this.ctx.storage.get("entry");
    if (current && current.expires > Date.now()) return false;
    await this.ctx.storage.put("entry", { secret, expires: Date.now() + CODE_TTL });
    return true;
  }

  async take() {
    const current = await this.ctx.storage.get("entry");
    await this.ctx.storage.deleteAll();
    return current && current.expires > Date.now() ? current.secret : null;
  }
}
