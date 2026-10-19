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
  vm.runInNewContext(source, {
    window, document, location: { hostname, origin: `https://${hostname}` }, Set, URL, URLSearchParams,
  });
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
  assert.equal(script.dataset.excludeSearch, undefined, 'query must reach beforeSend for UTM filtering');
  assert.equal(script.dataset.excludeHash, 'true');
  production.window.kidfocusTrack('tuduy_start');
  production.window.kidfocusTrack('profile_123');
  production.window.umami = { track: (name) => production.sent.push(name) };
  script.load();
  production.window.kidfocusTrack('install_click');
  assert.deepEqual(production.sent, ['tuduy_start', 'install_click']);
});

test('tracker keeps only campaign UTM parameters and referrer origin', () => {
  const { window } = load('kidfocus.web.app');
  const safe = window.kidfocusUmamiBeforeSend('event', {
    url: '/tu-duy/?profile=child-123&band=4-5&utm_source=facebook&utm_medium=social&utm_campaign=beta&email=private@example.com#child-name',
    referrer: 'https://www.example.com:8443/person?email=private@example.com#fragment',
    name: 'guest_start',
  });
  assert.equal(safe.url, '/tu-duy/?utm_source=facebook&utm_medium=social&utm_campaign=beta');
  assert.equal(safe.referrer, 'https://www.example.com:8443');
  assert.equal(safe.name, 'guest_start');
  const privateOnly = window.kidfocusUmamiBeforeSend('pageview', {
    url: 'https://kidfocus.web.app/app/?profile=child-123&email=private@example.com#secret',
    referrer: 'file:///private/family.html',
  });
  assert.equal(privateOnly.url, '/app/');
  assert.equal(privateOnly.referrer, undefined);
});
