/** Ingested SMS is untrusted evidence. It never sets an order to PAID. */
export function validateSmsEvent(body) {
  if (!body || Array.isArray(body) || typeof body !== 'object') return null;
  const { event_id, device_id, provider, sms_sender, transaction_id,
    amount_minor, currency, observed_at } = body;
  if (typeof event_id !== 'string' || !/^[a-f0-9]{64}$/.test(event_id)) return null;
  if (typeof device_id !== 'string' || !/^[0-9a-f-]{36}$/i.test(device_id)) return null;
  if (provider !== 'bkash_sms' || currency !== 'BDT') return null;
  if (typeof sms_sender !== 'string' || !/^[A-Za-z0-9+ -]{2,30}$/.test(sms_sender)) return null;
  if (typeof transaction_id !== 'string' || !/^[A-Za-z0-9]{6,30}$/.test(transaction_id)) return null;
  if (!Number.isSafeInteger(amount_minor) || amount_minor < 1 || amount_minor > 100_000_000_00) return null;
  if (typeof observed_at !== 'string' || !Number.isFinite(Date.parse(observed_at))) return null;
  return {
    event_id, device_id, provider, sms_sender,
    transaction_id: transaction_id.toUpperCase(),
    amount_minor, currency, observed_at
  };
}
