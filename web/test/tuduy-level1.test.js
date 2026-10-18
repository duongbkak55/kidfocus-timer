'use strict';
const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const http = require('node:http');
const path = require('node:path');
const { chromium } = require('playwright-core');

test('4–5 page plays five questions per type without external requests or overflow', async (t) => {
  const executablePath = process.env.CHROMIUM_PATH || '/Applications/Brave Browser.app/Contents/MacOS/Brave Browser';
  if (!fs.existsSync(executablePath)) return t.skip('Set CHROMIUM_PATH to Chromium');
  const dist = path.resolve(__dirname, '../dist');
  const server = http.createServer((request, response) => {
    const pathname = new URL(request.url, 'http://127.0.0.1').pathname;
    const file = path.resolve(dist, '.' + (pathname.endsWith('/') ? pathname + 'index.html' : pathname));
    if (!file.startsWith(dist + path.sep)) return response.writeHead(403).end();
    fs.readFile(file, (error, bytes) => {
      if (error) return response.writeHead(404).end();
      response.setHeader('Content-Type', ({ '.html': 'text/html', '.js': 'text/javascript', '.mjs': 'text/javascript',
        '.css': 'text/css', '.svg': 'image/svg+xml', '.webp': 'image/webp', '.json': 'application/json' })[path.extname(file)] || 'application/octet-stream');
      response.end(bytes);
    });
  });
  await new Promise((resolve) => server.listen(0, '127.0.0.1', resolve));
  t.after(() => server.close());
  const browser = await chromium.launch({ executablePath, headless: true, args: ['--no-sandbox', '--disable-gpu'] });
  t.after(() => browser.close());
  const origin = `http://127.0.0.1:${server.address().port}`;
  const page = await browser.newPage({ viewport: { width: 390, height: 844 } });
  const errors = [], requests = [];
  page.on('pageerror', (error) => errors.push(error.message));
  page.on('request', (request) => requests.push(request.url()));
  await page.goto(origin + '/tu-duy/?band=4-5', { waitUntil: 'networkidle' });
  assert.equal(await page.locator('#webBand45').getAttribute('aria-pressed'), 'true');
  assert.equal(await page.locator('#webBand56').getAttribute('aria-pressed'), 'false');
  const types = await page.evaluate(() => window.TuDuy.LEVEL1_TYPES);
  assert.equal(types.length, 13);
  assert.match(await page.locator('#tdPick').innerText(), /Làm quen/);
  assert.equal(await page.locator('#tdPick .td-shelf').count(), 4);

  for (const width of [320, 360, 412]) {
    await page.setViewportSize({ width, height: 844 });
    const bandOverflow = await page.evaluate(() => document.documentElement.scrollWidth - innerWidth);
    assert(bandOverflow <= 0, `bands overflow ${width}px by ${bandOverflow}px`);
    for (const type of types) {
      await page.evaluate((name) => window.TuDuyUI.start(name), type);
      assert.match(await page.locator('#tdCount').innerText(), /Câu 1\/5/);
      assert.equal(await page.evaluate(() => document.documentElement.scrollWidth - innerWidth), 0, `${type} overflows ${width}px`);
      const tooSmall = await page.evaluate(() => [...document.querySelectorAll('#tdPad .td-key,#tdVisual [data-choice]')]
        .filter((button) => button.getClientRects().length)
        .filter((button) => button.getBoundingClientRect().width < 64 || button.getBoundingClientRect().height < 64)
        .map((button) => `${button.textContent}: ${Math.round(button.getBoundingClientRect().width)}×${Math.round(button.getBoundingClientRect().height)}`));
      assert.deepEqual(tooSmall, [], `${type} targets at ${width}px`);
      if (width === 320) {
        for (let i = 0; i < 5; i++) {
          assert.match(await page.locator('#tdCount').innerText(), new RegExp(`Câu ${i + 1}/5`));
          if (await page.locator('#tdPad .td-key:visible').count()) await page.locator('#tdPad .td-key:visible').first().click();
          else await page.locator('#tdVisual [data-choice]').first().click();
          await page.locator('#tdCheck').click();
          if (await page.locator('#tdNext').isHidden()) await page.locator('#tdCheck').click();
          assert.equal(await page.locator('#tdExplain').isVisible(), true);
          await page.locator('#tdNext').click();
        }
        assert.equal(await page.locator('#tdDone').isVisible(), true);
      }
      await page.evaluate(() => window.TuDuyUI.open());
    }
  }
  const progress = await page.evaluate(() => JSON.parse(localStorage.getItem('tuduy.web.v1')));
  for (const type of types) {
    assert.equal(progress.stats[`l1:${type}`].level, 1, `${type} promoted out of level 1`);
    assert.equal(progress.stats[`l1:${type}`].seen, 5);
    assert.equal(progress.stats[type], undefined, `${type} changed the older-band stat`);
  }
  assert(progress.review.length > 0 && progress.review.every((item) => item.level === 1), 'review keeps level 1 items');
  await page.locator('#webBand56').click();
  assert.doesNotMatch(await page.locator('#tdPick').innerText(), /Làm quen|Làm lại câu chưa đúng/);
  await page.locator('#webBand45').click();
  assert.match(await page.locator('#tdPick').innerText(), /Làm quen|Làm lại câu chưa đúng/);
  await page.evaluate(() => window.TuDuyUI.start('mix'));
  assert.deepEqual(await page.evaluate(() => window.TuDuy.LEVEL1_TYPES), types, 'mix must not reorder the exported whitelist');
  assert.deepEqual(errors, []);
  assert(requests.every((url) => url.startsWith(origin + '/')), `external request: ${requests.find((url) => !url.startsWith(origin + '/'))}`);
});
