// Matches Android MigrationLeasePolicy; migration items still use their normal LWW transactions.
export const LEASE_MILLIS = 120_000;

export function decide(meta, now, deviceId) {
  if (meta?.schemaVersion === 2) return { kind: "ready", migratedAtMillis: Number(meta.migratedAtMillis ?? 0) };
  const until = Number(meta?.leaseUntil ?? 0);
  if (meta?.state === "migrating" && meta.by !== deviceId && until > now)
    return { kind: "wait", untilMillis: until };
  return { kind: "claim" };
}

export function claim(now, deviceId) {
  return { state: "migrating", by: deviceId, leaseUntil: now + LEASE_MILLIS };
}
