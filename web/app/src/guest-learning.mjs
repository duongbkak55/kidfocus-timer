export const GUEST_PROGRESS_KEY = "tuduy.web.v1";
export const IMPORTED_KEY = "kidfocus-guest-learning-imported";

export function readGuestProgress(storage) {
  if (storage.getItem(IMPORTED_KEY) === "yes") return null;
  try {
    const raw = JSON.parse(storage.getItem(GUEST_PROGRESS_KEY) || "null");
    if (!raw || typeof raw !== "object" || Array.isArray(raw) ||
        !raw.stats || typeof raw.stats !== "object" || Array.isArray(raw.stats) ||
        !Array.isArray(raw.review)) return null;
    if (!Object.keys(raw.stats).length && !raw.review.length) return null;
    return { stats: raw.stats, review: raw.review };
  } catch { return null; }
}

export function canImport(targetProfileId, learningRows) {
  return !learningRows.some(row => row.id === `${targetProfileId}_tuduy` && !row.deleted);
}
