import test from "node:test";
import assert from "node:assert/strict";
import { createServer } from "node:http";
import { createHandler } from "../src/app.js";
import { MessageStore } from "../src/store.js";

const channel = "abcdefghijklmnopqrstuvwxyz123456";
const deviceA = "device_A_abcdefghijklmnopqrstuvwxyz";
const deviceB = "device_B_abcdefghijklmnopqrstuvwxyz";

async function withServer(run) {
  const server = createServer(createHandler(new MessageStore()));
  await new Promise(resolve => server.listen(0, "127.0.0.1", resolve));
  try { await run(`http://127.0.0.1:${server.address().port}`); }
  finally { await new Promise(resolve => server.close(resolve)); }
}

test("routes opaque messages only to other devices", async () => withServer(async base => {
  const headersA = { "x-syncit-channel": channel, "x-syncit-device": deviceA, "content-type": "application/json" };
  const sent = await fetch(`${base}/v1/messages`, { method: "POST", headers: headersA, body: JSON.stringify({ version: 1, nonce: "abc", ciphertext: "secret" }) });
  assert.equal(sent.status, 201);
  const own = await fetch(`${base}/v1/messages`, { headers: headersA });
  assert.deepEqual((await own.json()).messages, []);
  const other = await fetch(`${base}/v1/messages`, { headers: { "x-syncit-channel": channel, "x-syncit-device": deviceB } });
  const messages = (await other.json()).messages;
  assert.equal(messages.length, 1);
  assert.equal(messages[0].ciphertext, "secret");
}));

test("rejects malformed requests", async () => withServer(async base => {
  const response = await fetch(`${base}/v1/messages`);
  assert.equal(response.status, 401);
}));
