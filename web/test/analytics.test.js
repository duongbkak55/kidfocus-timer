const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');

const source = fs.readFileSync(path.resolve(__dirname, '../analytics.js'), 'utf8');

function load(hostname) {
  const appended = [];
  const sent = [];
  const window = { umami: null };
  const document = {
    head: { append: (script) => appended.push(script) },
    createElement: () => ({ dataset: {}, addEventListener(name, callback) { this[name] = callback; } }),
  };
  vm.runInNewContext(source, { window, document, location: { hostname }, Set });
  return { appended, sent, window };
}

test('tracker loads on KidFocus production hosts only and sends only allowlisted event names', () => {
  const local = load('127.0.0.1');
  assert.equal(local.appended.length, 0);
  const production = load('kidfocus.web.app');
  assert.equal(production.appended.length, 1);
  const script = production.appended[0];
  assert.match(script.src, /^https:\/\//);
  assert.equal(script.referrerPolicy, 'no-referrer');
  assert.equal(script.dataset.beforeSend, 'kidfocusUmamiBeforeSend');
  assert.equal(script.dataset.doNotTrack, 'true');
  assert.equal(script.dataset.excludeSearch, 'true');
  assert.equal(script.dataset.excludeHash, 'true');
  production.window.kidfocusTrack('tuduy_start');
  production.window.kidfocusTrack('profile_123');
  production.window.umami = { track: (name) => production.sent.push(name) };
  script.load();
  production.window.kidfocusTrack('install_click');
  assert.deepEqual(production.sent, ['tuduy_start', 'install_click']);
});

test('tracker removes URL parameters and referrer before sending', () => {
  const { window } = load('kidfocus.web.app');
  const safe = window.kidfocusUmamiBeforeSend('event', {
    url: '/app/?email=private@example.com#child-name',
    referrer: 'https://example.com/person',
    name: 'guest_start',
  });
  assert.equal(safe.url, '/app/');
  assert.equal(safe.referrer, undefined);
  assert.equal(safe.name, 'guest_start');
});
