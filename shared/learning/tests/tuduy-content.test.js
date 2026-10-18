'use strict';

/**
 * Content gate for "Luyện tư duy" (5–6 tuổi). Runs the shipped engine
 * (`www/tuduy/tuduy-core.js`) thousands of times per type/level with fixed seeds and
 * fails the build if any item breaks a rule a teacher or parent would catch.
 * Answers are re-derived here by independent brute force, not by trusting the generator.
 */

const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');

const WWW = path.resolve(__dirname, '..', 'www');
const T = require(path.join(WWW, 'tuduy', 'tuduy-core.js'));
const N = Number(process.env.TUDUY_N || 10000);

function numbersIn(item) {
  const out = [];
  const walk = (x) => {
    if (typeof x === 'number') out.push(x);
    else if (typeof x === 'string' && /^\d+$/.test(x)) out.push(Number(x));
    else if (Array.isArray(x)) x.forEach(walk);
    else if (x && typeof x === 'object') Object.entries(x).forEach(([k, v]) => {
      if (['kind', 'aspect', 'ops', 'segments', 'w', 'h', 'ant'].includes(k)) return; // geometry/meta, not quantities
      walk(v);
    });
  };
  walk(item.visual);
  item.blanks.forEach((b) => out.push(b.answer));
  return out;
}

function forEachItem(type, level, fn) {
  const rng = T.mkRng(0xC0FFEE ^ (type.length * 7919) ^ level);
  for (let i = 0; i < N; i++) fn(T.generate(type, level, rng), i);
}

const evalTokens = (toks) => {
  let acc = toks[0];
  for (let i = 1; i < toks.length; i += 2) {
    acc = toks[i] === '+' ? acc + toks[i + 1] : acc - toks[i + 1];
    if (acc < 0 || acc > 10) return null;
  }
  return acc;
};

const independentSolvers = {
  pyramid(item) {
    const c = item.visual.cells;
    const sols = [];
    for (let a = 0; a <= 10; a++) for (let b = 0; b <= 10; b++) for (let d = 0; d <= 10; d++) {
      const full = [a, b, d, a + b, b + d, a + 2 * b + d];
      if (full.some((x) => x > 10)) continue;
      if (full.every((x, i) => c[i] == null || c[i] === x)) sols.push(full);
    }
    return sols.map((f) => item.blanks.map((bl) => f[bl.cell]));
  },
  balance(item) {
    const L = item.visual.left.map((t) => (t === '−' ? '-' : t));
    const R = item.visual.right.map((t) => (t === '−' ? '-' : t));
    const sols = [];
    for (let x = 0; x <= 10; x++) {
      const l = L.map((t) => (t === '□' ? x : t)), r = R.map((t) => (t === '□' ? x : t));
      const lv = evalTokens(l), rv = evalTokens(r);
      if (lv != null && lv === rv) sols.push([x]);
    }
    return sols;
  },
  chain(item) {
    const { values, ops } = item.visual;
    const sols = [];
    for (let start = 0; start <= 10; start++) {
      const seq = [start];
      for (const d of ops) seq.push(seq[seq.length - 1] + d);
      if (seq.some((v) => v < 0 || v > 10)) continue;
      if (seq.every((v, i) => values[i] == null || values[i] === v)) sols.push(item.blanks.map((b) => seq[b.cell]));
    }
    return sols;
  },
  scale(item) {
    const { facts, ask } = item.visual;
    const syms = [...new Set(facts.flatMap((f) => f.left).concat(ask.left))];
    const vals = new Set();
    const rec = (i, env) => {
      if (i === syms.length) {
        if (facts.every((f) => f.left.reduce((s, k) => s + env[k], 0) === Number(f.right[0]))) {
          vals.add(ask.left.reduce((s, k) => s + env[k], 0));
        }
        return;
      }
      for (let v = 0; v <= 10; v++) { env[syms[i]] = v; rec(i + 1, env); }
    };
    rec(0, {});
    return [...vals].map((v) => [v]);
  },
  shapes(item) {
    const v = item.visual;
    if (v.kind === 'fan') return [[(v.segments * (v.segments + 1)) / 2]];
    if (v.ant) return [[(v.ant.x + 1) * (2 - v.ant.x) * (v.ant.y + 1) * (2 - v.ant.y)]]; // rects containing a cell in 2×2
    if (v.aspect === 1) return [[5]]; // squares in 2×2
    return [[(v.w * (v.w + 1)) / 2 * ((v.h * (v.h + 1)) / 2)]];
  },
};

for (const type of T.TYPES) {
  for (const level of [2, 3]) {
    test(`${type} L${level}: ${N} items stay in 0..10 with exactly one solution`, () => {
      forEachItem(type, level, (item, i) => {
        assert.equal(item.type, type);
        assert.ok(item.blanks.length >= 1, `item ${i} has no blank`);
        for (const n of numbersIn(item)) {
          assert.ok(Number.isInteger(n) && n >= 0 && n <= 10, `${type} L${level} #${i}: number ${n} out of 0..10`);
        }
        const expected = item.blanks.map((b) => b.answer);
        if (independentSolvers[type]) {
          const sols = independentSolvers[type](item);
          assert.equal(sols.length, 1, `${type} L${level} #${i}: ${sols.length} solutions ${JSON.stringify(item.visual)}`);
          assert.deepEqual(sols[0], expected, `${type} L${level} #${i}: solver disagrees`);
        }
      });
    });

    test(`${type} L${level}: explanation ends at the right answer and stays age-appropriate`, () => {
      forEachItem(type, level, (item, i) => {
        const text = [item.prompt, ...item.explain].join('\n');
        assert.ok(item.explain.length >= 2, `#${i} explanation too short`);
        for (const b of item.blanks) {
          assert.ok(new RegExp(`(^|[^0-9])${b.answer}([^0-9]|$)`).test(item.explain.slice(1).join(' ')),
            `${type} L${level} #${i}: explanation never states answer ${b.answer}`);
        }
        assert.doesNotMatch(text, /×|÷|(^|[^\p{L}])(nhân|chia|gấp)([^\p{L}]|$)/iu, 'no multiplication/division/ratio wording (audit M11)');
        assert.doesNotMatch(text, /(^|[^\p{L}])(thi|kiểm tra|đề)([^\p{L}]|$)|lớp 1|sẵn sàng/iu, 'no exam or pre-Grade-1 framing (audit F11/M15)');
      });
    });
  }
}

test('sequence items show enough of the pattern before the first blank', () => {
  forEachItem('sequence', 2, (item) => {
    const first = item.blanks[0].cell;
    assert.ok(first >= 2);
    if (item.rule === 'abab') assert.ok(first >= 3);
  });
  forEachItem('sequence', 3, (item) => assert.ok(item.blanks[0].cell >= 2));
});

test('shape enumeration matches the closed formulas', () => {
  const { fanTriangles, stripRects, gridRects } = T._internal;
  for (let lines = 0; lines <= 4; lines++) {
    const n = lines + 1;
    assert.equal(fanTriangles(lines).shapes.length, (n * (n + 1)) / 2);
  }
  for (let w = 1; w <= 5; w++) assert.equal(stripRects(w).length, (w * (w + 1)) / 2);
  assert.equal(gridRects(2, 2, true).length, 5);
  assert.equal(gridRects(2, 2, false).length, 9);
  assert.equal(gridRects(3, 3, true).length, 14);
});

test('adaptive level: four in a row moves up, two misses move back down', () => {
  let s;
  for (let i = 0; i < 4; i++) s = T.updateStat(s, true);
  assert.equal(s.level, 3);
  s = T.updateStat(s, false);
  assert.equal(s.level, 3);
  s = T.updateStat(s, false);
  assert.equal(s.level, 2);
});

test('shipped UI text has no exam or pre-Grade-1 framing', () => {
  const ui = fs.readFileSync(path.join(WWW, 'tuduy', 'tuduy-ui.js'), 'utf8');
  const strings = ui.match(/'[^'\n]*'|`[^`]*`/g) || [];
  for (const s of strings) assert.doesNotMatch(s, /(^|[^\p{L}])thi([^\p{L}]|$)|lớp 1|sẵn sàng vào/iu, s);
});

test('tuduy is shown from 4–5 tuổi upward and never to 2–3', () => {
  const html = fs.readFileSync(path.join(WWW, 'index.html'), 'utf8');
  const start = html.indexOf('const AGE_BANDS=');
  assert.ok(start > 0);
  const block = html.slice(start, html.indexOf('let ageBand=', start));
  const visibleByBand = {};
  const re = /'?([\w-]+)'?:\{\s*label:'[^']*',(?:\s*\/\*[\s\S]*?\*\/)?\s*visible:\[([^\]]*)\]/g;
  let m;
  while ((m = re.exec(block))) visibleByBand[m[1]] = m[2];
  assert.ok(visibleByBand['5-6'], 'band 5-6 exists');
  for (const [band, list] of Object.entries(visibleByBand)) {
    const shouldShow = ['4-5', '5-6', 'l1', 'l2', 'l3'].includes(band);
    assert.equal(/'tuduy'/.test(list), shouldShow, `band ${band} tuduy visibility`);
  }
});

test('hints (first explanation line) never reveal an answer', () => {
  for (const type of T.TYPES) for (const level of [2, 3]) {
    forEachItem(type, level, (item, i) => {
      if (type === 'sequence') {
        assert.equal(item.explain[0], 'Xem các số thay đổi thế nào từ ô này sang ô bên cạnh.', `sequence hint must be a nudge, not the rule (#${i})`);
      }
    });
  }
});

test('grid figures fit inside the 300×200 drawing area', () => {
  const ui = fs.readFileSync(path.join(WWW, 'tuduy', 'tuduy-ui.js'), 'utf8');
  const src = ui.match(/function gridGeom\(v\) \{[\s\S]*?\n/)[0];
  const gridGeom = new Function('v', src.replace(/^function gridGeom\(v\) \{/, '').replace(/\}\s*$/, ''));
  for (const w of [1, 2, 3, 4, 5]) for (const h of [1, 2]) for (const aspect of [1, 1.6]) {
    const g = gridGeom({ w, h, aspect });
    assert.ok(g.ox >= 0 && g.ox + g.W <= 300 && g.oy >= 0 && g.oy + g.H <= 200, `w${w} h${h} a${aspect}: ${JSON.stringify(g)}`);
    assert.ok(Math.abs(g.cw / g.chh - aspect) < 1e-9, 'cell aspect preserved');
  }
});
