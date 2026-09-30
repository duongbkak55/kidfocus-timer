const {createHash} = require("node:crypto");
const {initializeApp} = require("firebase-admin/app");
const {getAuth} = require("firebase-admin/auth");
const {getFirestore, FieldValue} = require("firebase-admin/firestore");
const {getRemoteConfig} = require("firebase-admin/remote-config");
const {defineSecret, defineBoolean} = require("firebase-functions/params");
const {onCall, onRequest, HttpsError} = require("firebase-functions/v2/https");

const schedule = require("./schedule");

initializeApp();

const db = getFirestore();
const openRouterKey = defineSecret("OPENROUTER_API_KEY");
const revenueCatWebhookAuth = defineSecret("REVENUECAT_WEBHOOK_AUTH");
const enforceAppCheck = defineBoolean("ENFORCE_APP_CHECK", {default: true});
const REGION = "asia-southeast1";
const TIME_ZONE = "Asia/Ho_Chi_Minh";
const SYSTEM_PROMPT = `Bạn là trợ lý học tập thân thiện tên Cú học, dành cho học sinh tiểu học và THCS Việt Nam.
Giải thích ngắn gọn, dễ hiểu, bằng ngôn ngữ phù hợp với trẻ em. Chỉ hỗ trợ học tập và kiến thức phổ thông.
Không yêu cầu hoặc lặp lại dữ liệu cá nhân của trẻ. Với nội dung nguy hiểm hoặc không phù hợp, từ chối nhẹ nhàng.
Trả lời tối đa 5 câu, dùng ví dụ cụ thể khi cần.`;

const DEFAULT_CONFIG = {
  enabled: true,
  guestDailyLimit: 10,
  freeDailyLimit: 10,
  premiumDailyCredits: 60,
  globalDailyCredits: 500,
  earlyAccessOpen: false,
  earlyAccessDays: 60,
  earlyDailyCredits: 15,
  scheduleEnabled: false,
  scheduleGuestEnabled: false,
  scheduleModel: "google/gemini-2.5-flash",
  scheduleParseCost: 1,
  scheduleAdviseCost: 2,
  scheduleLogCost: 1,
  scheduleAdviseModel: "google/gemini-2.5-flash",
  scheduleVisionModel: "google/gemini-2.5-flash-lite",
  scheduleImageCost: 3,
  scheduleImageTiers: [], // Fail closed for beta if Remote Config is unavailable.
  scheduleFreeDailyParses: 0, // 0 preserves W2: no additional PARSE cap.
  maxHistoryMessages: 12,
  maxInputChars: 1500,
  maxOutputTokens: 600,
  models: [
    {
      id: "openrouter/free",
      label: "Miễn phí",
      description: "Tự chọn model miễn phí đang sẵn sàng",
      creditCost: 1,
      dailyLimit: 10,
      premiumOnly: false,
    },
    {
      id: "google/gemini-2.5-flash-lite",
      label: "Nhanh",
      description: "Nhanh, phù hợp câu hỏi ngắn",
      creditCost: 1,
      dailyLimit: 60,
      premiumOnly: true,
    },
    {
      id: "openai/gpt-5-mini",
      label: "Thông minh",
      description: "Giải thích bài tập cần suy luận",
      creditCost: 3,
      dailyLimit: 20,
      premiumOnly: true,
    },
    {
      id: "google/gemini-3.6-flash",
      label: "Chuyên sâu",
      description: "Dành cho câu hỏi khó",
      creditCost: 10,
      dailyLimit: 6,
      premiumOnly: true,
    },
  ],
};

let cachedConfig = null;
let cachedAt = 0;

exports.getAiConfig = onCall({region: REGION, maxInstances: 2}, async (request) => {
  const config = await loadConfig();
  const identity = await resolveIdentity(request);
  const usage = await readUsage(identity, config);
  return publicConfig(config, usage);
});

exports.aiChat = onCall(
    {
      region: REGION,
      timeoutSeconds: 60,
      memory: "256MiB",
      minInstances: 0,
      maxInstances: 2,
      concurrency: 20,
      secrets: [openRouterKey],
      // Change to true after registering Play Integrity and the local debug token.
      enforceAppCheck: false,
    },
    async (request) => {
      const config = await loadConfig();
      if (!config.enabled) throw new HttpsError("unavailable", "AI_DISABLED");

      const identity = await resolveIdentity(request);
      const modelId = safeString(request.data && request.data.modelId, 120);
      const model = config.models.find((item) => item.id === modelId) || config.models[0];
      if (!model) throw new HttpsError("failed-precondition", "NO_AI_MODEL");
      if (model.premiumOnly && !identity.premium) {
        throw new HttpsError("permission-denied", "PREMIUM_REQUIRED");
      }

      const messages = sanitizeMessages(request.data && request.data.messages, config);
      const lastUserMessage = [...messages].reverse().find((item) => item.role === "user");
      if (!lastUserMessage) throw new HttpsError("invalid-argument", "EMPTY_QUESTION");
      const requestId = safeString(request.data && request.data.requestId, 80);
      if (!requestId) throw new HttpsError("invalid-argument", "REQUEST_ID_REQUIRED");

      await reserveQuota(identity, config, model, requestId);

      try {
        const response = await fetch("https://openrouter.ai/api/v1/chat/completions", {
          method: "POST",
          headers: {
            "Authorization": `Bearer ${openRouterKey.value()}`,
            "Content-Type": "application/json",
            "HTTP-Referer": "https://kidfocus.app",
            "X-Title": "KidFocus Timer",
          },
          body: JSON.stringify({
            model: model.id,
            messages: [{role: "system", content: SYSTEM_PROMPT}, ...messages],
            max_tokens: config.maxOutputTokens,
            temperature: 0.4,
            provider: {data_collection: "deny"},
          }),
        });
        const payload = await response.json().catch(() => ({}));
        if (!response.ok) {
          throw new Error(`OPENROUTER_${response.status}:${safeString(payload.error && payload.error.message, 160)}`);
        }
        const text = safeString(payload.choices && payload.choices[0] && payload.choices[0].message && payload.choices[0].message.content, 6000);
        if (!text) throw new Error("EMPTY_AI_RESPONSE");
        const usage = await completeReservation(identity, config, requestId);
        return {text, usage};
      } catch (error) {
        await refundReservation(identity, model, requestId).catch(() => undefined);
        console.error("AI request failed", error instanceof Error ? error.message : error);
        if (String(error && error.message).includes("OPENROUTER_429")) {
          throw new HttpsError("resource-exhausted", "PROVIDER_BUSY");
        }
        throw new HttpsError("unavailable", "AI_REQUEST_FAILED");
      }
    },
);

exports.aiSchedule = onCall(
    {region: REGION, timeoutSeconds: 90, memory: "256MiB", minInstances: 0,
      maxInstances: 2, concurrency: 20, secrets: [openRouterKey], enforceAppCheck},
    schedule.createScheduleHandler({loadConfig, resolveIdentity, quotaDay, reserveQuota, completeReservation, refundReservation,
      fetch: (...args) => fetch(...args), key: () => openRouterKey.value(),
      error: (code, message) => new HttpsError(code, message), log: (code) => console.error(code)}),
);

exports.claimEarlyAccess = onCall(
    {region: REGION, maxInstances: 2, enforceAppCheck},
    async (request) => schedule.claimEarlyAccess({db, uid: request.auth && request.auth.uid,
      config: await loadConfig(), now: Date.now(), error: (code, message) => new HttpsError(code, message)}),
);

exports.revenueCatWebhook = onRequest(
    {region: REGION, maxInstances: 1, secrets: [revenueCatWebhookAuth]},
    async (request, response) => {
      const expected = revenueCatWebhookAuth.value();
      if (!expected || request.get("authorization") !== expected) {
        response.status(401).send("unauthorized");
        return;
      }
      const event = request.body && request.body.event;
      const uid = safeString(event && event.app_user_id, 160);
      if (!uid || uid.startsWith("$RCAnonymousID")) {
        response.status(202).send("ignored");
        return;
      }
      const entitlementIds = Array.isArray(event.entitlement_ids) ? event.entitlement_ids : [];
      const expirationAtMillis = Number(event.expiration_at_ms || 0);
      const inactiveEvent = event.type === "EXPIRATION";
      const active = entitlementIds.includes("premium") && !inactiveEvent &&
        (!expirationAtMillis || expirationAtMillis > Date.now());
      await db.collection("entitlements").doc(uid).set({
        premium: active,
        productId: safeString(event.product_id, 160),
        expirationAtMillis,
        eventType: safeString(event.type, 80),
        updatedAt: FieldValue.serverTimestamp(),
      }, {merge: true});
      response.status(200).send("ok");
    },
);

exports.deleteAccount = onCall(
    {region: REGION, maxInstances: 2},
    async (request) => {
      const uid = request.auth && request.auth.uid;
      if (!uid) throw new HttpsError("unauthenticated", "SIGN_IN_REQUIRED");

      const userRef = db.collection("users").doc(uid);
      await db.recursiveDelete(userRef);
      await db.recursiveDelete(
          db.collection("internalAiQuota").doc(`uid_${uid}`),
      );
      await db.collection("entitlements").doc(uid).delete();
      await getAuth().deleteUser(uid);
      return {deleted: true};
    },
);

async function loadConfig() {
  if (cachedConfig && Date.now() - cachedAt < 5 * 60 * 1000) return cachedConfig;
  const next = {...DEFAULT_CONFIG};
  try {
    const template = await getRemoteConfig().getTemplate();
    const value = (name) => template.parameters[name] &&
      template.parameters[name].defaultValue && template.parameters[name].defaultValue.value;
    next.enabled = parseBoolean(value("ai_enabled"), next.enabled);
    next.guestDailyLimit = parseIntSafe(value("ai_guest_daily_limit"), next.guestDailyLimit, 0, 100);
    next.freeDailyLimit = parseIntSafe(value("ai_free_daily_limit"), next.freeDailyLimit, 0, 100);
    next.premiumDailyCredits = parseIntSafe(value("ai_premium_daily_credits"), next.premiumDailyCredits, 0, 1000);
    next.globalDailyCredits = parseIntSafe(value("ai_global_daily_credits"), next.globalDailyCredits, 0, 100000);
    next.maxHistoryMessages = parseIntSafe(value("ai_max_history_messages"), next.maxHistoryMessages, 2, 30);
    next.maxInputChars = parseIntSafe(value("ai_max_input_chars"), next.maxInputChars, 100, 4000);
    next.maxOutputTokens = parseIntSafe(value("ai_max_output_tokens"), next.maxOutputTokens, 100, 2000);
    next.earlyAccessOpen = parseBoolean(value("early_access_open"), next.earlyAccessOpen);
    next.earlyAccessDays = parseIntSafe(value("ai_early_access_days"), next.earlyAccessDays, 1, 365);
    next.earlyDailyCredits = parseIntSafe(value("ai_early_daily_credits"), next.earlyDailyCredits, 0, 1000);
    next.scheduleEnabled = parseBoolean(value("ai_schedule_enabled"), next.scheduleEnabled);
    next.scheduleGuestEnabled = parseBoolean(value("ai_schedule_guest_enabled"), next.scheduleGuestEnabled);
    next.scheduleModel = safeString(value("ai_schedule_model"), 120) || next.scheduleModel;
    next.scheduleParseCost = parseIntSafe(value("ai_schedule_parse_cost"), next.scheduleParseCost, 1, 100);
    next.scheduleLogCost = parseIntSafe(value("ai_schedule_log_cost"), next.scheduleLogCost, 1, 100);
    next.scheduleAdviseCost = parseIntSafe(value("ai_schedule_advise_cost"), next.scheduleAdviseCost, 1, 100);
    next.scheduleAdviseModel = safeString(value("ai_schedule_advise_model"), 120) || next.scheduleModel;
    next.scheduleVisionModel = safeString(value("ai_schedule_vision_model"), 120) || next.scheduleVisionModel;
    next.scheduleImageCost = parseIntSafe(value("ai_schedule_image_cost"), next.scheduleImageCost, 1, 100);
    const imageTiers = value("ai_schedule_image_tiers");
    next.scheduleImageTiers = parseScheduleImageTiers(imageTiers, next.scheduleImageTiers);
    next.scheduleFreeDailyParses = parseIntSafe(value("ai_schedule_free_daily_parses"), next.scheduleFreeDailyParses, 0, 100);
    const remoteModels = JSON.parse(value("ai_models_json") || "null");
    if (Array.isArray(remoteModels) && remoteModels.length) {
      next.models = remoteModels.map(normalizeModel).filter(Boolean);
    }
  } catch (error) {
    console.warn("Using built-in AI config", error instanceof Error ? error.message : error);
  }
  cachedConfig = next;
  cachedAt = Date.now();
  return next;
}

async function resolveIdentity(request) {
  const uid = request.auth && request.auth.uid;
  if (uid) {
    const entitlement = await db.collection("entitlements").doc(uid).get();
    return identityFromEntitlement(uid, entitlement.data() || {}, Date.now());
  }
  const guestId = safeString(request.data && request.data.guestId, 80);
  if (!/^[a-zA-Z0-9-]{16,80}$/.test(guestId)) {
    throw new HttpsError("unauthenticated", "GUEST_ID_REQUIRED");
  }
  const hash = createHash("sha256").update(guestId).digest("hex");
  return {subject: `guest_${hash}`, premium: false, signedIn: false};
}

function identityFromEntitlement(uid, entitlement, now) {
  const premium = entitlement.premium === true;
  const earlyAccessUntil = Number(entitlement.earlyAccessUntil || 0);
  return {subject: `uid_${uid}`, premium, signedIn: true, earlyAccessUntil,
    tier: premium ? "premium" : earlyAccessUntil > now ? "early" : "free"};
}

function dailyCredits(identity, config) {
  return identity.premium ? config.premiumDailyCredits : identity.tier === "early" ? config.earlyDailyCredits :
    (identity.signedIn ? config.freeDailyLimit : config.guestDailyLimit);
}

function quotaRef(identity, quotaDb = db) {
  return quotaDb.collection("internalAiQuota").doc(identity.subject).collection("days").doc(identity.quotaDate || quotaDay());
}

function globalQuotaRef(quotaDb = db, day = quotaDay()) {
  return quotaDb.collection("internalAiQuota").doc("_global").collection("days").doc(day);
}

function quotaDay() {
  const parts = new Intl.DateTimeFormat("en", {
    timeZone: TIME_ZONE, year: "numeric", month: "2-digit", day: "2-digit",
  }).formatToParts(new Date());
  const part = (type) => parts.find((item) => item.type === type).value;
  return `${part("year")}-${part("month")}-${part("day")}`;
}

async function readUsage(identity, config) {
  const snapshot = await quotaRef(identity).get();
  return usageFromData(identity, config, snapshot.data() || {});
}

function usageFromData(identity, config, data) {
  const questions = Number(data.questions || 0);
  const credits = Number(data.credits || 0);
  const limit = dailyCredits(identity, config);
  const freeScheduleCap = identity.signedIn && !identity.premium && identity.tier !== "early" && config.scheduleFreeDailyParses > 0;
  return {
    ...(freeScheduleCap ? {remainingScheduleParses: Math.max(0, config.scheduleFreeDailyParses - Number(data.scheduleParses || 0))} : {}),
    premium: identity.premium,
    tier: identity.tier || (identity.premium ? "premium" : identity.signedIn ? "free" : "guest"),
    earlyAccessUntil: identity.earlyAccessUntil || null,
    remainingQuestions: Math.max(0, limit - questions),
    remainingCredits: Math.max(0, limit - credits),
  };
}

async function reserveQuota(identity, config, model, requestId, quotaDb = db) {
  const ref = quotaRef(identity, quotaDb);
  const globalRef = globalQuotaRef(quotaDb, identity.quotaDate || quotaDay());
  return quotaDb.runTransaction(async (transaction) => {
    const [snapshot, globalSnapshot] = await Promise.all([
      transaction.get(ref), transaction.get(globalRef),
    ]);
    const data = snapshot.data() || {};
    const globalData = globalSnapshot.data() || {};
    const requests = data.requests || {};
    if (requests[requestId]) {
      throw new HttpsError("already-exists", "DUPLICATE_REQUEST");
    }
    const questions = Number(data.questions || 0);
    const credits = Number(data.credits || 0);
    const modelCounts = data.modelCounts || {};
    const modelCount = Number(modelCounts[model.id] || 0);
    const maxCredits = dailyCredits(identity, config);
    const globalCredits = Number(globalData.credits || 0);
    const scheduleParses = Number(data.scheduleParses || 0);
    if (model.scheduleParse && identity.signedIn && !identity.premium && identity.tier !== "early" &&
        config.scheduleFreeDailyParses > 0 && scheduleParses >= config.scheduleFreeDailyParses) {
      throw new HttpsError("resource-exhausted", "SCHEDULE_DAILY_LIMIT_REACHED");
    }
    if (questions >= maxCredits || credits + model.creditCost > maxCredits || modelCount >= model.dailyLimit) {
      throw new HttpsError("resource-exhausted", "DAILY_LIMIT_REACHED");
    }
    if (globalCredits + model.creditCost > config.globalDailyCredits) {
      throw new HttpsError("resource-exhausted", "GLOBAL_DAILY_LIMIT_REACHED");
    }
    transaction.set(ref, {
      ...(model.scheduleParse ? {scheduleParses: scheduleParses + 1} : {}),
      questions: questions + 1,
      credits: credits + model.creditCost,
      modelCounts: {...modelCounts, [model.id]: modelCount + 1},
      requests: {...requests, [requestId]: {state: "reserved", modelId: model.id, ...(model.scheduleParse ? {scheduleParse: true} : {})}},
      updatedAt: FieldValue.serverTimestamp(),
    }, {merge: true});
    transaction.set(globalRef, {
      questions: Number(globalData.questions || 0) + 1,
      credits: globalCredits + model.creditCost,
      updatedAt: FieldValue.serverTimestamp(),
    }, {merge: true});
    const reserved = {...data, ...(model.scheduleParse ? {scheduleParses: scheduleParses + 1} : {}), questions: questions + 1, credits: credits + model.creditCost};
    return {usage: usageFromData(identity, config, reserved)};
  });
}

async function completeReservation(identity, config, requestId, quotaDb = db) {
  const ref = quotaRef(identity, quotaDb);
  return quotaDb.runTransaction(async (transaction) => {
    const snapshot = await transaction.get(ref);
    const data = snapshot.data() || {};
    const requests = data.requests || {};
    transaction.set(ref, {
      requests: {...requests, [requestId]: {...requests[requestId], state: "complete"}},
    }, {merge: true});
    return usageFromData(identity, config, data);
  });
}

async function refundReservation(identity, model, requestId, quotaDb = db) {
  const ref = quotaRef(identity, quotaDb);
  const globalRef = globalQuotaRef(quotaDb, identity.quotaDate || quotaDay());
  await quotaDb.runTransaction(async (transaction) => {
    const [snapshot, globalSnapshot] = await Promise.all([
      transaction.get(ref), transaction.get(globalRef),
    ]);
    const data = snapshot.data() || {};
    const globalData = globalSnapshot.data() || {};
    const requests = data.requests || {};
    if (!requests[requestId] || requests[requestId].state !== "reserved") return;
    const modelCounts = data.modelCounts || {};
    const wasScheduleParse = requests[requestId].scheduleParse === true;
    delete requests[requestId];
    transaction.set(ref, {
      ...data,
      ...(wasScheduleParse ? {scheduleParses: Math.max(0, Number(data.scheduleParses || 0) - 1)} : {}),
      questions: Math.max(0, Number(data.questions || 0) - 1),
      credits: Math.max(0, Number(data.credits || 0) - model.creditCost),
      modelCounts: {...modelCounts, [model.id]: Math.max(0, Number(modelCounts[model.id] || 0) - 1)},
      requests,
    });
    transaction.set(globalRef, {
      questions: Math.max(0, Number(globalData.questions || 0) - 1),
      credits: Math.max(0, Number(globalData.credits || 0) - model.creditCost),
      updatedAt: FieldValue.serverTimestamp(),
    }, {merge: true});
  });
}

function publicConfig(config, usage) {
  return {enabled: config.enabled, models: config.models, usage,
    ai_schedule_enabled: config.scheduleEnabled && config.enabled, early_access_open: config.earlyAccessOpen,
    ai_schedule_log_cost: config.scheduleLogCost, ai_schedule_parse_cost: config.scheduleParseCost, ai_schedule_advise_cost: config.scheduleAdviseCost,
    ai_schedule_image_tiers: config.scheduleImageTiers, ai_schedule_image_cost: config.scheduleImageCost,
    ai_schedule_free_daily_parses: config.scheduleFreeDailyParses};
}

function sanitizeMessages(raw, config) {
  if (!Array.isArray(raw)) return [];
  return raw.slice(-config.maxHistoryMessages).map((message) => ({
    role: message && message.role === "assistant" ? "assistant" : "user",
    content: safeString(message && message.content, config.maxInputChars),
  })).filter((message) => message.content);
}

function normalizeModel(model) {
  const id = safeString(model && model.id, 120);
  if (!id) return null;
  return {
    id,
    label: safeString(model.label, 60) || id,
    description: safeString(model.description, 160),
    creditCost: parseIntSafe(model.creditCost, 1, 1, 100),
    dailyLimit: parseIntSafe(model.dailyLimit, 10, 1, 1000),
    premiumOnly: model.premiumOnly === true,
  };
}

function safeString(value, maxLength) {
  return typeof value === "string" ? value.trim().slice(0, maxLength) : "";
}

function parseBoolean(value, fallback) {
  if (value === "true" || value === true) return true;
  if (value === "false" || value === false) return false;
  return fallback;
}

function parseScheduleImageTiers(value, fallback) {
  if (typeof value !== "string") return fallback;
  return [...new Set(value.split(",").map((tier) => tier.trim())
      .filter((tier) => ["free", "early", "premium", "guest"].includes(tier)))];
}

function parseIntSafe(value, fallback, min, max) {
  const parsed = Number.parseInt(value, 10);
  return Number.isFinite(parsed) ? Math.min(max, Math.max(min, parsed)) : fallback;
}

module.exports._test = {DEFAULT_CONFIG, publicConfig, normalizeModel, parseBoolean, parseIntSafe, parseScheduleImageTiers, sanitizeMessages, usageFromData, identityFromEntitlement, dailyCredits, reserveQuota, completeReservation, refundReservation};
