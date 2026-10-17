'use strict';
const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const http = require('node:http');
const { chromium } = require('playwright-core');

const dist = path.resolve(__dirname, '../dist');
const executablePath = process.env.CHROMIUM_PATH || '/Applications/Brave Browser.app/Contents/MacOS/Brave Browser';

test('a missed explanation never animates a later question, chooser or session', async (t) => {
  if (!fs.existsSync(executablePath)) return t.skip('Set CHROMIUM_PATH to a local Chromium executable');
  const server = http.createServer((req, res) => {
    const route = new URL(req.url, 'http://127.0.0.1').pathname;
    const file = path.resolve(dist, '.' + (route.endsWith('/') ? route + 'index.html' : route));
    if (!file.startsWith(dist + path.sep)) { res.writeHead(403).end(); return; }
    fs.readFile(file, (error, bytes) => {
      if (error) { res.writeHead(404).end(); return; }
      res.setHeader('Content-Type', { '.html': 'text/html', '.js': 'text/javascript', '.css': 'text/css' }[path.extname(file)] || 'application/octet-stream');
      res.end(bytes);
    });
  });
  await new Promise((resolve) => server.listen(0, '127.0.0.1', resolve));
  t.after(() => server.close());
  const browser = await chromium.launch({ executablePath, headless: true, args: ['--no-sandbox', '--disable-gpu'] });
  t.after(() => browser.close());
  const page = await browser.newPage({ viewport: { width: 390, height: 844 } });
  const errors = [];
  page.on('pageerror', (error) => errors.push(error.message));
  await page.clock.install();
  await page.goto(`http://127.0.0.1:${server.address().port}/tu-duy/`);

  const types = await page.evaluate(() => TuDuy.TYPES.concat(TuDuy.PHASE2_TYPES, TuDuy.PHASE2B_TYPES));
  let cases = 0;
  for (const band of ['5-6', 'l1']) for (const level of [2, 3]) {
    for (const type of types.filter((name) => band === 'l1' || !['triSum', 'addTable'].includes(name))) {
      const before = await page.evaluate(({ band, level, type }) => {
        ageBand = band;
        const generate = TuDuy.generate;
        const items = [];
        TuDuy.generate = (name, _level, rng, opts) => {
          const item = generate(name, level, rng, opts);
          items.push(item);
          return item;
        };
        try { TuDuyUI.start(type); } finally { TuDuy.generate = generate; }
        const item = items[0], visual = document.getElementById('tdVisual');
        const click = (selector) => {
          const element = document.querySelector(selector);
          if (!element) throw Error(`${type} L${level} ${band}: missing ${selector}`);
          element.click();
        };
        if (item.input === 'numpad' || !item.input || item.input === 'signs') {
          for (const blank of item.blanks) {
            click(`[data-blank="${blank.id}"]`);
            const key = [...document.querySelectorAll('#tdPad .td-key')]
              .find((b) => b.textContent !== '⌫' && b.textContent !== String(blank.answer).replace('-', '−'));
            if (!key) throw Error(`${type} L${level} ${band}: missing wrong key`);
            key.click();
          }
        } else if (item.input === 'order') {
          for (const value of item.answer.slice().reverse()) click(`[data-order="${value}"]`);
        } else if (item.input === 'multi') {
          const wrong = [...visual.querySelectorAll('[data-multi]')]
            .find((b) => !item.answer.includes(Number(b.dataset.multi)));
          if (!wrong) throw Error(`${type} L${level} ${band}: no wrong multi option`);
          wrong.click();
        } else if (item.input === 'pairs') {
          item.answer.forEach((right, left) => {
            click(`[data-pair-side="0"][data-pair-index="${left}"]`);
            click(`[data-pair-side="1"][data-pair-index="${(right + 1) % item.answer.length}"]`);
          });
        } else {
          const visualChoices = [...visual.querySelectorAll('[data-choice]')];
          if (visualChoices.length) {
            const wrong = visualChoices.find((b) => Number(b.dataset.choice) !== item.answer);
            if (!wrong) throw Error(`${type} L${level} ${band}: no wrong visual option`);
            wrong.dispatchEvent(new MouseEvent('click', { bubbles: true }));
          } else {
            const choices = item.input === 'sign' ? ['>', '<', '='] : item.choices;
            const wrongIndex = choices.findIndex((choice, index) =>
              item.input === 'sign' || item.type === 'shapePattern' ? choice !== item.answer : index !== item.answer);
            click(`#tdPad [data-choice="${wrongIndex}"]`);
          }
        }
        TuDuyUI.check();
        TuDuyUI.check();
        if (document.getElementById('tdNext').hidden) throw Error(`${type} L${level} ${band}: second miss did not finish`);
        TuDuyUI.next();
        const blanks = [...visual.querySelectorAll('.td-blank')].map((b) => b.textContent);
        if (blanks.some((value) => value !== '?')) throw Error(`${type} L${level} ${band}: next question has prefilled blanks`);
        return { visual: visual.innerHTML, blanks, count: document.getElementById('tdCount').textContent };
      }, { band, level, type });
      await page.clock.runFor(3000);
      const after = await page.evaluate(() => ({
        visual: document.getElementById('tdVisual').innerHTML,
        blanks: [...document.querySelectorAll('#tdVisual .td-blank')].map((b) => b.textContent),
        count: document.getElementById('tdCount').textContent,
      }));
      assert.deepEqual(after, before, `${type} L${level} ${band} changed after stale animation`);
      assert.deepEqual(errors, [], `${type} L${level} ${band} raised a page error`);
      cases++;
    }
  }

  for (const transition of ['open', 'start']) {
    const before = await page.evaluate((transition) => {
      ageBand = 'l1';
      TuDuyUI.start('pyramid');
      const item = document.querySelector('#tdPad .td-key');
      for (const blank of document.querySelectorAll('#tdVisual .td-blank')) {
        blank.click(); item.click();
      }
      TuDuyUI.check(); TuDuyUI.check();
      if (document.getElementById('tdNext').hidden) throw Error('pyramid second miss did not finish');
      if (transition === 'open') TuDuyUI.open(); else TuDuyUI.start('chain');
      return document.getElementById('tuduy').innerHTML;
    }, transition);
    await page.clock.runFor(3000);
    assert.equal(await page.locator('#tuduy').innerHTML(), before, `pyramid stale animation after ${transition}`);
    assert.deepEqual(errors, [], `pyramid stale animation raised an error after ${transition}`);
  }
  assert.equal(cases, 100, 'all 26 types at L2/L3 and each permitted band are covered');
});
