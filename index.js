import 'dotenv/config';
import express from 'express';
import rateLimit from 'express-rate-limit';
import Database from 'better-sqlite3';
import { timingSafeEqual } from 'node:crypto';
import { mkdirSync } from 'node:fs';
import { dirname, resolve } from 'node:path';
import { validateSmsEvent } from './validation.js';

const ingestToken = process.env.INGEST_TOKEN ?? '';
const adminToken = process.env.ADMIN_TOKEN ?? '';
if (ingestToken.length < 24 || adminToken.length < 24 || ingestToken === adminToken) {
  throw new Error('Set two different, strong INGEST_TOKEN and ADMIN_TOKEN values in .env');
}

const dbPath = resolve(process.env.SQLITE_PATH ?? './data/events.db');
mkdirSync(dirname(dbPath), { recursive: true });
const db = new Database(dbPath);
db.pragma('journal_mode = WAL');
db.exec(`
  CREATE TABLE IF NOT EXISTS sms_events (
    event_id TEXT PRIMARY KEY,
    device_id TEXT NOT NULL,
    provider TEXT NOT NULL,
    sms_sender TEXT NOT NULL,
    transaction_id TEXT NOT NULL,
    amount_minor INTEGER NOT NULL,
    currency TEXT NOT NULL,
    observed_at TEXT NOT NULL,
    created_at TEXT NOT NULL DEFAULT (datetime('now')),
    review_status TEXT NOT NULL DEFAULT 'awaiting_manual_review',
    UNIQUE(device_id, transaction_id, amount_minor)
  );
  CREATE INDEX IF NOT EXISTS idx_sms_review_status ON sms_events(review_status);
`);

function bearerMatches(req, expected) {
  const auth = req.headers.authorization;
  if (!auth?.startsWith('Bearer ')) return false;
  const actual = Buffer.from(auth.slice(7));
  const wanted = Buffer.from(expected);
  return actual.length === wanted.length && timingSafeEqual(actual, wanted);
}

const app = express();
app.disable('x-powered-by');
app.use(express.json({ limit: '4kb', strict: true }));
app.use(rateLimit({ windowMs: 60_000, limit: 100, standardHeaders: 'draft-7', legacyHeaders: false }));

app.get('/health', (_req, res) => res.json({ ok: true }));

app.post('/v1/sms-events', (req, res) => {
  if (!bearerMatches(req, ingestToken)) return res.sendStatus(401);
  const event = validateSmsEvent(req.body);
  if (!event) return res.status(400).json({ error: 'Invalid payment-alert event' });
  const inserted = db.prepare(`
    INSERT OR IGNORE INTO sms_events
    (event_id, device_id, provider, sms_sender, transaction_id,
     amount_minor, currency, observed_at)
    VALUES (@event_id, @device_id, @provider, @sms_sender, @transaction_id,
            @amount_minor, @currency, @observed_at)
  `).run(event);
  return res.status(202).json({
    received: true,
    duplicate: inserted.changes === 0,
    review_status: 'awaiting_manual_review'
  });
});

// Demo read-only review endpoint. Replace with proper admin accounts, RBAC,
// audit logs and MFA before processing any real customer data.
app.get('/admin/sms-events', (req, res) => {
  if (!bearerMatches(req, adminToken)) return res.sendStatus(401);
  const events = db.prepare(`
    SELECT event_id, device_id, provider, sms_sender, transaction_id,
           amount_minor, currency, observed_at, created_at, review_status
    FROM sms_events ORDER BY created_at DESC LIMIT 100
  `).all();
  return res.json({ data: events });
});

// Never expose an endpoint here that marks website orders PAID based only on an SMS.
const port = Number.parseInt(process.env.PORT || '3000', 10);
app.listen(port, '0.0.0.0', () => {
  console.log(`SMS ingest demo listening on port ${port}. Use a TLS reverse proxy.`);
});
