import { randomUUID } from "node:crypto";
import { URL } from "node:url";

const MAX_BODY = 9 * 1024 * 1024;
const ID_PATTERN = /^[A-Za-z0-9_-]{20,128}$/;

function json(response, status, value) {
  const body = JSON.stringify(value);
  response.writeHead(status, {
    "content-type": "application/json; charset=utf-8",
    "content-length": Buffer.byteLength(body),
    "cache-control": "no-store",
    "x-content-type-options": "nosniff"
  });
  response.end(body);
}

async function readJson(request) {
  const chunks = [];
  let size = 0;
  for await (const chunk of request) {
    size += chunk.length;
    if (size > MAX_BODY) throw Object.assign(new Error("Payload too large"), { status: 413 });
    chunks.push(chunk);
  }
  try { return JSON.parse(Buffer.concat(chunks).toString("utf8")); }
  catch { throw Object.assign(new Error("Invalid JSON"), { status: 400 }); }
}

export function createHandler(store) {
  return async (request, response) => {
    try {
      const url = new URL(request.url, "http://relay.local");
      if (request.method === "GET" && url.pathname === "/health") {
        return json(response, 200, { ok: true });
      }

      const channel = request.headers["x-syncit-channel"];
      const device = request.headers["x-syncit-device"];
      if (!ID_PATTERN.test(channel ?? "") || !ID_PATTERN.test(device ?? "")) {
        return json(response, 401, { error: "Valid channel and device headers are required" });
      }

      if (request.method === "POST" && url.pathname === "/v1/messages") {
        const body = await readJson(request);
        if (body.version !== 1 || typeof body.nonce !== "string" || typeof body.ciphertext !== "string" ||
            body.nonce.length > 64 || body.ciphertext.length > MAX_BODY) {
          return json(response, 400, { error: "Invalid encrypted envelope" });
        }
        const envelope = {
          id: `${Date.now().toString().padStart(13, "0")}-${randomUUID()}`,
          sender: device,
          createdAt: Date.now(),
          version: 1,
          nonce: body.nonce,
          ciphertext: body.ciphertext
        };
        store.append(channel, envelope);
        return json(response, 201, { id: envelope.id });
      }

      if (request.method === "GET" && url.pathname === "/v1/messages") {
        const cursor = url.searchParams.get("after") ?? "";
        return json(response, 200, { messages: store.after(channel, cursor, device) });
      }

      return json(response, 404, { error: "Not found" });
    } catch (error) {
      return json(response, error.status ?? 500, { error: error.status ? error.message : "Internal error" });
    }
  };
}

