/* Public, cookie-free web analytics. This file is not loaded by the Android app. */
(() => {
  const hosts = new Set([
    "kidfocus.web.app",
    "kidfocus.134-185-83-168.sslip.io",
  ]);
  if (!hosts.has(location.hostname)) return;

  const eventNames = new Set([
    "tuduy_start",
    "tuduy_done",
    "band_45",
    "install_click",
    "guest_start",
  ]);
  const pending = [];
  window.kidfocusTrack = (name) => {
    if (!eventNames.has(name)) return;
    if (window.umami?.track) window.umami.track(name);
    else pending.push(name);
  };
  window.kidfocusUmamiBeforeSend = (_type, payload) => {
    if (!payload || typeof payload !== "object") return false;
    const safe = { ...payload };
    if (typeof safe.url === "string") {
      try {
        const url = new URL(safe.url, location.origin);
        const allowed = new URLSearchParams();
        for (const key of ["utm_source", "utm_medium", "utm_campaign"]) {
          const value = url.searchParams.get(key);
          if (value !== null) allowed.set(key, value);
        }
        safe.url = url.pathname + (allowed.size ? `?${allowed}` : "");
      } catch (_) {
        safe.url = safe.url.split(/[?#]/, 1)[0];
      }
    }
    if (typeof safe.referrer === "string") {
      try {
        const referrer = new URL(safe.referrer);
        if (/^https?:$/.test(referrer.protocol)) safe.referrer = referrer.hostname;
        else delete safe.referrer;
      } catch (_) {
        delete safe.referrer;
      }
    } else delete safe.referrer;
    return safe;
  };

  const script = document.createElement("script");
  script.defer = true;
  script.referrerPolicy = "no-referrer";
  script.src = "https://stats.kidfocus.134-185-83-168.sslip.io/script.js";
  script.dataset.websiteId = "f5ea3996-ba9a-4a7e-8b29-846364770b3a";
  script.dataset.domains = [...hosts].join(",");
  script.dataset.excludeHash = "true";
  script.dataset.doNotTrack = "true";
  script.dataset.beforeSend = "kidfocusUmamiBeforeSend";
  script.addEventListener("load", () => {
    if (window.umami?.track) pending.splice(0).forEach((name) => window.umami.track(name));
  });
  script.addEventListener("error", () => { pending.length = 0; });
  document.head.append(script);
})();
