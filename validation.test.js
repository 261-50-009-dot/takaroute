import test from 'node:test';
import assert from 'node:assert/strict';
import { validateSmsEvent } from '../validation.js';

const good = {
  event_id: 'a'.repeat(64),
  device_id: 'de67f6ec-e3ae-4942-95d3-090fc2f66f44',
  provider: 'bkash_sms',
  sms_sender: 'bKash',
  transaction_id: 'ABC1234567',
  amount_minor: 180000,
  currency: 'BDT',
  observed_at: '2026-11-18T04:00:00Z'
};
test('accepts valid event for manual review', () => {
  assert.equal(validateSmsEvent(good).amount_minor, 180000);
});
test('rejects invalid event amount', () => {
  assert.equal(validateSmsEvent({ ...good, amount_minor: -10 }), null);
});
test('rejects invalid or unsafe event payload', () => {
  assert.equal(validateSmsEvent({ ...good, transaction_id: 'OTP 123456' }), null);
  assert.equal(validateSmsEvent({ ...good, amount_minor: '180000' }), null);
  assert.equal(validateSmsEvent(null), null);
});
