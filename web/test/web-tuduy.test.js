'use strict';
const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const crypto = require('node:crypto');
const http = require('node:http');
const { chromium } = require('playwright-core');

const root = path.resolve(__dirname, '../..');
const dist = path.join(root, 'web/dist');
const pageDir = path.join(dist, 'tu-duy');
const sourceAssets = path.join(root, 'shared/learning/www/tuduy');
const builtAssets = path.join(pageDir, 'tuduy');
const read = (file) => fs.readFileSync(file, 'utf8');
const digest = (file) => crypto.createHash('sha256').update(fs.readFileSync(file)).digest('hex');
function filesUnder(dir, prefix = '') {
  return fs.readdirSync(dir, { withFileTypes: true }).flatMap((entry) => {
    const rel = path.join(prefix, entry.name);
    return entry.isDirectory() ? filesUnder(path.join(dir, entry.name), rel) : [rel];
  }).sort();
}

test('build copies every shipped Tu Duy asset byte-for-byte within the size budget', () => {
  const originals = filesUnder(sourceAssets), copies = filesUnder(builtAssets);
  assert.deepEqual(copies, originals);
  for (const file of originals) assert.equal(digest(path.join(builtAssets, file)), digest(path.join(sourceAssets, file)), file);
  const bytes = filesUnder(pageDir).reduce((sum, file) => sum + fs.statSync(path.join(pageDir, file)).size, 0);
  assert(bytes <= 1_500_000, `${bytes} bytes exceeds 1.5 MB`);
  assert.equal(digest(path.join(dist, 'beta/index.html')), digest(path.join(root, 'web/beta/index.html')));
  assert.equal(digest(path.join(pageDir, 'privacy.html')), digest(path.join(root, 'site/privacy.html')));
  assert.equal(digest(path.join(pageDir, 'terms.html')), digest(path.join(root, 'site/terms.html')));
});

test('page is Vietnamese, private and free of third-party requests or exam framing', () => {
  const html = read(path.join(pageDir, 'index.html'));
  assert.match(html, /<html lang="vi">/);
  assert.match(html, /tuduy\.web\.v1/);
  assert.doesNotMatch(html, /<script[^>]+src=["']https?:\/\//i);
  assert.doesNotMatch(html, /serviceWorker|document\.cookie|sendBeacon|fetch\(|XMLHttpRequest|@import/i);
  const external = [...html.matchAll(/(?:href|src|content)=["'](https?:\/\/[^"']+)/g)].map((m) => m[1]);
  assert(external.every((url) => /https:\/\/(?:play\.google\.com|groups\.google\.com|kidfocus\.web\.app|kidfocus\.134-185-83-168\.sslip\.io)\//.test(url)));
  const visible = html.replace(/<script\b[^>]*>[\s\S]*?<\/script>/gi, '')
    .replace(/<style\b[^>]*>[\s\S]*?<\/style>/gi, '').replace(/<[^>]*>/g, ' ')
    .replace('không phải luyện thi', ''); // The explicit reassurance is the one permitted use of “thi”.
  assert.doesNotMatch(visible, /(^|[\s.,!?])(?:thi|kiểm tra|đề|lớp 1 sẵn sàng)(?=$|[\s.,!?])/iu);
  assert.match(visible, /quan sát, tìm quy luật, so sánh và suy luận/i);
  assert.match(html, /href="\/beta\/"/);
  assert.match(html, /href="privacy\.html"/);
  const scripts = [...html.matchAll(/<script>([\s\S]*?)<\/script>/g)].map((m) => m[1]);
  assert(scripts.join('\n').split('\n').length < 150, 'web glue exceeds 150 JS lines');
});

test('real Chromium completes five questions in each band without external requests', async (t) => {
  const executablePath = process.env.CHROMIUM_PATH || '/Applications/Brave Browser.app/Contents/MacOS/Brave Browser';
  if (!fs.existsSync(executablePath)) return t.skip('Set CHROMIUM_PATH to a local Chromium executable');
  const server = http.createServer((req, res) => {
    const pathname = new URL(req.url, 'http://127.0.0.1').pathname;
    const route = pathname.endsWith('/') ? pathname + 'index.html' : pathname;
    const file = path.resolve(dist, '.' + route);
    if (!file.startsWith(dist + path.sep)) { res.writeHead(403).end(); return; }
    fs.readFile(file, (error, bytes) => {
      if (error) { res.writeHead(404).end(); return; }
      const ext = path.extname(file);
      res.setHeader('Content-Type', ({ '.html': 'text/html', '.js': 'text/javascript', '.css': 'text/css', '.svg': 'image/svg+xml', '.png': 'image/png', '.webp': 'image/webp' })[ext] || 'application/octet-stream');
      res.end(bytes);
    });
  });
  await new Promise((resolve) => server.listen(0, '127.0.0.1', resolve));
  t.after(() => server.close());
  const browser = await chromium.launch({ executablePath, headless: true, args: ['--no-sandbox', '--disable-gpu'] });
  t.after(() => browser.close());
  const page = await browser.newPage({ viewport: { width: 390, height: 844 }, deviceScaleFactor: 1 });
  const origin = `http://127.0.0.1:${server.address().port}`;
  const errors = [], requests = [];
  page.on('pageerror', (error) => errors.push(error.message));
  page.on('request', (request) => requests.push(request.url()));
  await page.goto(origin + '/tu-duy/', { waitUntil: 'networkidle' });
  assert.equal(await page.title(), 'Cho bé thử Luyện tư duy — KidFocus');
  assert.equal(await page.locator('#webAppBack').getAttribute('href'), '/app/');
  assert.equal(await page.locator('#webAppBack').isVisible(), true);
  async function checkTargets() {
    const tooSmall = await page.evaluate(() => [...document.querySelectorAll('button,a')].flatMap((element) => {
      const box = element.getBoundingClientRect();
      return box.width && box.height && (box.width < 44 || box.height < 44) ? [`${element.id || element.className}: ${Math.round(box.width)}×${Math.round(box.height)}`] : [];
    }));
    assert.deepEqual(tooSmall, []);
  }
  await checkTargets();

  for (const [band, hasGradeOne] of [['webBand56', false], ['webBandL1', true]]) {
    await page.locator('#' + band).click();
    const chooserText = await page.locator('#tdPick').innerText();
    assert.equal(chooserText.includes('Tam giác số'), hasGradeOne);
    assert.equal(chooserText.includes('Bảng cộng'), hasGradeOne);
    await page.locator('#tdPick .td-pick').filter({ hasText: 'So sánh' }).click();
    await checkTargets();
    for (let i = 0; i < 5; i++) {
      assert.match(await page.locator('#tdCount').innerText(), new RegExp(`Câu ${i + 1}/5`));
      await page.locator('#tdPad .td-choice').first().click();
      await page.locator('#tdCheck').click();
      if (await page.locator('#tdNext').isHidden()) await page.locator('#tdCheck').click();
      assert.equal(await page.locator('#tdExplain').isVisible(), true);
      await page.locator('#tdNext').click();
    }
    assert.equal(await page.locator('#tdDone').isVisible(), true);
    await checkTargets();
    assert.match(await page.locator('#tdDoneText').innerText(), /\/5 câu/);
    assert.equal(await page.locator('#webInstall').getAttribute('href'), '/beta/');
    assert.equal(await page.evaluate(() => localStorage.getItem('tuduy.v1')), null);
    assert.notEqual(await page.evaluate(() => localStorage.getItem('tuduy.web.v1')), null);
  }
  for (const width of [360, 412]) {
    await page.setViewportSize({ width, height: 844 });
    for (const [band, age] of [['webBand56', '5-6'], ['webBandL1', 'l1']]) {
      await page.locator('#' + band).click();
      const types = await page.evaluate(() => TuDuy.TYPES.concat(TuDuy.PHASE2_TYPES, TuDuy.PHASE2B_TYPES));
      for (const type of types.filter((name) => age === 'l1' || !['triSum', 'addTable'].includes(name))) {
        await page.evaluate((name) => TuDuyUI.start(name), type);
        await checkTargets();
        const overflow = await page.evaluate(() => document.documentElement.scrollWidth - innerWidth);
        assert(overflow <= 0, `${type} at ${width}px overflows by ${overflow}px`);
        await page.evaluate(() => TuDuyUI.open());
      }
    }
  }
  assert.deepEqual(errors, []);
  assert(requests.every((url) => url.startsWith(origin + '/')), `external request: ${requests.find((url) => !url.startsWith(origin + '/'))}`);
});
