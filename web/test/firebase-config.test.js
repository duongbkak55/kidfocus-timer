const test = require("node:test");
const assert = require("node:assert/strict");
const { readFileSync } = require("node:fs");
const path = require("node:path");

test("production Firebase config uses the hosted auth domain", () => {
  const source = JSON.parse(
    readFileSync(path.join(__dirname, "../app/firebase-config.json"), "utf8"),
  );
  const deployed = JSON.parse(
    readFileSync(path.join(__dirname, "../dist/app/firebase-config.json"), "utf8"),
  );
  assert.equal(source.projectId, "kid-focus-app");
  assert.equal(source.authDomain, "kidfocus.web.app");
  assert.match(source.appId, /^1:1064385320816:web:/);
  assert.notEqual(source.apiKey, "PUBLIC_WEB_API_KEY");
  assert.deepEqual(deployed, source);
});
