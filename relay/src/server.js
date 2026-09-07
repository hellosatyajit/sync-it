import { createServer } from "node:http";
import { createHandler } from "./app.js";
import { MessageStore } from "./store.js";

const port = Number(process.env.PORT ?? 8787);
const store = new MessageStore(process.env.SYNCIT_DATA_FILE ?? null);
await store.load();

const server = createServer(createHandler(store));
server.requestTimeout = 15_000;
server.headersTimeout = 10_000;
server.listen(port, "0.0.0.0", () => console.log(`Sync It relay listening on :${port}`));

