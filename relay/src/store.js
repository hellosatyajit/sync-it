import { readFile, rename, writeFile } from "node:fs/promises";
import { dirname } from "node:path";
import { mkdir } from "node:fs/promises";

const MAX_MESSAGES = 50;
const MAX_AGE_MS = 24 * 60 * 60 * 1000;

export class MessageStore {
  constructor(file = null) {
    this.file = file;
    this.channels = new Map();
    this.writeChain = Promise.resolve();
  }

  async load() {
    if (!this.file) return;
    try {
      const parsed = JSON.parse(await readFile(this.file, "utf8"));
      for (const [channel, messages] of Object.entries(parsed)) {
        this.channels.set(channel, Array.isArray(messages) ? messages : []);
      }
      this.prune();
    } catch (error) {
      if (error.code !== "ENOENT") throw error;
    }
  }

  append(channel, envelope) {
    const messages = this.channels.get(channel) ?? [];
    messages.push(envelope);
    this.channels.set(channel, messages.slice(-MAX_MESSAGES));
    this.prune();
    this.persist();
  }

  after(channel, cursor, excludingDevice) {
    this.prune();
    const messages = this.channels.get(channel) ?? [];
    const cursorIndex = cursor ? messages.findIndex(message => message.id === cursor) : -1;
    const candidates = cursorIndex >= 0 ? messages.slice(cursorIndex + 1) : messages.filter(message => message.id > cursor);
    return candidates.filter(message => message.sender !== excludingDevice);
  }

  prune(now = Date.now()) {
    const cutoff = now - MAX_AGE_MS;
    for (const [channel, messages] of this.channels) {
      const retained = messages.filter(message => message.createdAt >= cutoff).slice(-MAX_MESSAGES);
      if (retained.length) this.channels.set(channel, retained);
      else this.channels.delete(channel);
    }
  }

  persist() {
    if (!this.file) return;
    const snapshot = JSON.stringify(Object.fromEntries(this.channels));
    this.writeChain = this.writeChain.then(async () => {
      await mkdir(dirname(this.file), { recursive: true });
      const temporary = `${this.file}.tmp`;
      await writeFile(temporary, snapshot, { mode: 0o600 });
      await rename(temporary, this.file);
    }).catch(error => console.error("Unable to persist relay data", error));
  }
}
