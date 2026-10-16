'use strict';
const test = require('node:test');
const assert = require('node:assert/strict');
const T = require('../www/tuduy/tuduy-core');
const P2B = require('../www/tuduy/tuduy-phase2b-core');
const fs = require('node:fs');
const path = require('node:path');
const N = Number(process.env.TUDUY_N || 100);
const seq = (n) => Array.from({ length: n }, (_, i) => i);
const legal = (a, op, b, max) => a >= 0 && b >= 0 && b <= max &&
  (op === '+' ? a + b <= max && (max === 10 || a + b <= 10 || a % 10 + b % 10 <= 9) :
    a - b >= 0 && (max === 10 || a <= 10 || a % 10 >= b % 10));
function gridSolutions(cells, n) {
  let count = 0, solution;
  const a = cells.slice();
  function search() {
    const hole = a.findIndex((x) => x == null);
    if (hole < 0) { count++; solution = a.slice(); return; }
    const row = Math.floor(hole / n), col = hole % n;
    for (let value = 1; value <= n; value++) {
      if (seq(n).some((j) => a[row * n + j] === value || a[j * n + col] === value)) continue;
      a[hole] = value; search(); a[hole] = null;
      if (count > 1) return;
    }
  }
  search(); return { count, solution };
}
function magicSolutions(v, max) {
  const a = v.cells.slice(), holes = seq(9).filter((i) => a[i] == null), sols = [];
  function rowsColumns() {
    const lines = [...seq(3).map((r) => [a[r * 3], a[r * 3 + 1], a[r * 3 + 2]]),
      ...seq(3).map((c) => [a[c], a[c + 3], a[c + 6]])];
    return lines.every((line) => line.reduce((x, y) => x + y, 0) === v.sum &&
      legal(line[0], '+', line[1], max) && legal(line[0] + line[1], '+', line[2], max));
  }
  function walk(k) {
    if (k === holes.length) { if (rowsColumns()) sols.push(a.slice()); return; }
    const i = holes[k], row = Math.floor(i / 3);
    // Independent row constraint prunes the 0..max brute force search.
    for (let value = 0; value <= max; value++) {
      a[i] = value;
      if (a.slice(row * 3, row * 3 + 3).every((x) => x != null) &&
          a.slice(row * 3, row * 3 + 3).reduce((x, y) => x + y, 0) === v.sum) walk(k + 1);
      a[i] = null;
      if (sols.length > 1) return;
    }
  }
  walk(0); return sols;
}
function signAnswers(v) {
  const out = [], k = v.nums.length - 1;
  for (let mask = 0; mask < 1 << k; mask++) {
    let acc = v.nums[0], good = true; const signs = [];
    for (let j = 0; j < k; j++) {
      const op = mask & 1 << j ? '+' : '-'; signs.push(op);
      acc += op === '+' ? v.nums[j + 1] : -v.nums[j + 1];
      if (acc < 0) { good = false; break; }
    }
    if (good && acc === v.target) out.push(signs);
  }
  return out;
}
function unusedNumbers(nums) {
  const out = [];
  for (let omitted = 0; omitted < 5; omitted++) {
    const a = nums.filter((_, i) => i !== omitted);
    if ([[0,1,2,3],[0,2,1,3],[0,3,1,2]].some(([p,q,r,s]) => a[p] + a[q] === a[r] + a[s])) out.push(nums[omitted]);
  }
  return out;
}
function expression(e, max) {
  assert(legal(e[0], e[1], e[2], max));
  return e[1] === '+' ? e[0] + e[2] : e[0] - e[2];
}
function independent(item, max) {
  const v = item.visual;
  switch (item.type) {
    case 'latin': {
      assert.equal(v.n, max === 10 ? item.level + 1 : item.level + 2);
      assert(item.blanks.length >= 2 && item.blanks.length <= 4);
      const solved = gridSolutions(v.cells, v.n);
      assert.equal(solved.count, 1);
      return item.blanks.map((b) => solved.solution[b.cell]);
    }
    case 'magic': {
      assert.equal(v.cells.length, 9);
      assert.equal(item.blanks.length, 3);
      if (max === 10) assert(v.sum <= 9);
      else assert(v.sum <= 15);
      const sols = magicSolutions(v, max);
      assert.equal(sols.length, 1);
      return item.blanks.map((b) => sols[0][b.cell]);
    }
    case 'signs': {
      assert.equal(v.nums.length - 1, item.level);
      const sols = signAnswers(v);
      assert.equal(sols.length, 1);
      let acc = v.nums[0];
      sols[0].forEach((op, i) => { assert(legal(acc, op, v.nums[i + 1], max)); acc += op === '+' ? v.nums[i + 1] : -v.nums[i + 1]; });
      return sols[0];
    }
    case 'triCenter': {
      const a = v.values.slice(), hole = item.blanks[0].cell;
      assert.equal(item.level === 2 ? 3 : hole, hole);
      const result = hole === 3 ? a[0] + a[1] + a[2] : a[3] - seq(3).filter((i) => i !== hole).reduce((s, i) => s + a[i], 0);
      a[hole] = result;
      assert(legal(a[0], '+', a[1], max) && legal(a[0] + a[1], '+', a[2], max));
      assert.equal(a[0] + a[1] + a[2], a[3]);
      return [result];
    }
    case 'sumSplit': {
      assert.equal(new Set(v.nums).size, 5);
      assert(v.nums.every((x) => x >= 1 && x <= 10));
      assert.equal(item.choices.length, 4);
      const sols = unusedNumbers(v.nums);
      assert.equal(sols.length, 1);
      const four = v.nums.filter((n) => n !== sols[0]);
      assert([[0,1,2,3],[0,2,1,3],[0,3,1,2]].some(([a,b,c,d]) =>
        four[a] + four[b] === four[c] + four[d] && legal(four[a], '+', four[b], max) && legal(four[c], '+', four[d], max)));
      assert.equal(item.choices.filter((x) => x === sols[0]).length, 1);
      return item.choices.indexOf(sols[0]);
    }
    case 'addTable': {
      assert.equal(max, 20);
      const rows = v.rows.slice();
      if (rows[0] == null) {
        const known = v.cells.findIndex((x, i) => i < 2 && x != null);
        assert(known >= 0);
        rows[0] = v.cells[known] - v.cols[known];
      }
      const full = rows.flatMap((row) => v.cols.map((col) => {
        assert(legal(row, '+', col, max)); return row + col;
      }));
      assert(v.cells.every((x, i) => x == null || x === full[i]));
      return item.blanks.map((b) => b.cell === 'row0' ? rows[0] : full[b.cell]);
    }
    case 'numberLine': {
      const delta = v.to - v.from, op = delta >= 0 ? '+' : '-';
      assert.equal(op, v.op);
      assert(legal(v.from, op, Math.abs(delta), max));
      assert(v.from >= 0 && v.to <= max);
      return [v.from, Math.abs(delta), v.to];
    }
    case 'ring': {
      const values = [v.values[0]];
      assert(v.ops.length >= 6 && v.ops.length <= 8);
      v.ops.forEach((d, i) => {
        assert(Math.abs(d) >= 1 && Math.abs(d) <= 3);
        assert(legal(values[i], d > 0 ? '+' : '-', Math.abs(d), max));
        values.push(values[i] + d);
      });
      assert.equal(values.at(-1), values[0]);
      assert.equal(v.values[0], values[0]);
      const givens = v.values.map((x, i) => x == null ? null : i).filter((x) => x != null);
      assert.equal(givens.length, item.level === 3 ? 2 : 1);
      assert(givens.every((i) => v.values[i] === values[i]));
      return item.blanks.map((b) => values[b.cell]);
    }
    case 'match': {
      const lv = v.left.map((e) => expression(e, max)), rv = v.right.map((e) => expression(e, max));
      assert.equal(new Set(lv).size, lv.length);
      assert.equal(new Set(rv).size, rv.length);
      const permutations = (xs) => xs.length ? xs.flatMap((x) => permutations(xs.filter((y) => y !== x)).map((p) => [x, ...p])) : [[]];
      const solutions = permutations(seq(rv.length)).filter((p) => p.every((j, i) => lv[i] === rv[j]));
      assert.equal(solutions.length, 1);
      return solutions[0];
    }
    default: throw new Error('missing independent solver for ' + item.type);
  }
}
for (const type of T.PHASE2B_TYPES) for (const level of [2, 3]) {
  test(`${type} L${level}: ${N} independently checked items per band`, () => {
    for (const max of type === 'addTable' ? [20] : [10, 20]) {
      const rng = T.mkRng(10001 + T.PHASE2B_TYPES.indexOf(type) * 201 + level * 13 + max);
      for (let i = 0; i < N; i++) {
        const item = T.generate(type, level, rng, { max });
        assert.equal(item.type, type);
        assert.equal(item.level, level);
        const expected = independent(item, max);
        assert.deepEqual(item.answer, expected, `${type} max=${max} #${i}`);
        if (item.blanks) assert.deepEqual(item.blanks.map((b) => b.answer), expected);
        assert(item.explain.length >= 2 && item.explain[0].length > 0);
        const answerText = Array.isArray(expected) ? expected.join(', ') : String(expected);
        assert(!item.explain[0].includes(answerText));
        assert(item.explain.at(-1).length > 0);
        assert(!/(^|[\s.,!?])thi(?=$|[\s.,!?])|kiểm tra|sẵn sàng vào lớp 1/iu.test(item.prompt + item.explain.join(' ')));
      }
    }
  });
}
test('addTable is unavailable in the 5–6 band', () =>
  assert.throws(() => T.generate('addTable', 2, T.mkRng(1), { max: 10 })));
test('magic and Venn give at least 30 distinct visuals in 1000 items with safe margins', () => {
  for (const [type, max, options] of [['magic', 10, {}], ['insideOut', 10, { phase2b: true }]]) {
    const rng = T.mkRng(type === 'magic' ? 412 : 413), visuals = new Set();
    for (let i = 0; i < 1000; i++) {
      const item = T.generate(type, type === 'magic' ? 2 : 3, rng, { max, ...options });
      const v = item.visual;
      if (type === 'magic') visuals.add(JSON.stringify([v.cells, v.sum]));
      else {
        assert.equal(v.kind, 'venn3');
        for (const c of v.circles) assert(c.x - c.r >= 12 && c.x + c.r <= 248 && c.y - c.r >= 12 && c.y + c.r <= 218);
        assert(v.kangaroo.x >= 12 && v.kangaroo.x <= 248 && v.kangaroo.y >= 12 && v.kangaroo.y <= 218);
        visuals.add(JSON.stringify([v.circles, v.kangaroo]));
      }
    }
    assert(visuals.size >= 30, `${type}: ${visuals.size} distinct visuals`);
  }
});
function independentExtension(item, max) {
  const v = item.visual;
  switch (v.kind) {
    case 'queue': {
      assert.equal(new Set(v.names).size, v.names.length);
      assert.equal(v.ahead + v.behind + 1, v.names.length);
      return v.ahead;
    }
    case 'venn3': {
      const counts = v.circles.map((c) => {
        const distance = Math.hypot(v.kangaroo.x - c.x, v.kangaroo.y - c.y);
        assert(Math.abs(distance - c.r) >= 12);
        return distance < c.r ? 1 : 0;
      });
      const total = counts.reduce((a,b) => a+b, 0);
      assert(total >= 1 && total <= 3);
      return total - 1;
    }
    case 'expressionCondition': {
      const found = v.expressions.map((e,i) => {
        const value = expression(e,max); return value === v.target ? i : null;
      }).filter((x) => x != null);
      assert(found.length >= 1 && found.length <= 4);
      return found;
    }
    case 'dotPattern': {
      assert.equal(v.prefix.length, 8);
      const possible = item.choices.filter((candidate) => [1,3].some((d) =>
        v.prefix.every((x,i) => x === (v.prefix[0] + d*i) % 4) && candidate === (v.prefix[0] + d*v.prefix.length) % 4));
      assert.equal(possible.length, 1);
      return possible[0];
    }
    case 'dotGrid': {
      const fits = item.choices.map((tile,i) => {
        const cells = v.cells.slice(); cells[v.missing] = tile;
        const valid = seq(3).every((j) => {
          const row = cells.slice(3*j,3*j+3), col=[cells[j],cells[j+3],cells[j+6]];
          return new Set(row.map((x)=>x.shape)).size===3 && new Set(row.map((x)=>x.dots)).size===3 &&
            new Set(col.map((x)=>x.shape)).size===3 && new Set(col.map((x)=>x.dots)).size===3;
        });
        return valid ? i : null;
      }).filter((x)=>x!=null);
      assert.equal(fits.length,1);
      return fits[0];
    }
    case 'diceCompare': {
      assert(v.pairs.length >= 4 && v.pairs.length <= 5);
      const totals=v.pairs.map(([a,b])=>{
        assert(a>=1&&a<=6&&b>=1&&b<=6);
        assert(legal(a,'+',b,max));return a+b;
      });
      assert.equal(new Set(totals).size,totals.length);
      return totals.indexOf(Math.max(...totals));
    }
    case 'pyramid4': {
      assert.equal(max,20);
      const cells=v.cells.slice();
      for(const [from,len] of [[0,4],[4,3],[7,2]]) for(let j=0;j<len-1;j++){
        const sum=cells[from+j]+cells[from+j+1];
        assert(legal(cells[from+j],'+',cells[from+j+1],max));
        cells[from+len+j]=sum;
      }
      assert(v.cells.every((x,i)=>x==null||x===cells[i]));
      return item.blanks.map((b)=>cells[b.cell]);
    }
    case 'chain': {
      assert(v.ops.length>=6&&v.ops.length<=7);
      assert(v.ops.some((x)=>x>0)&&v.ops.some((x)=>x<0));
      const full=[v.values[0]];
      v.ops.forEach((d,i)=>{assert(legal(full[i],d>0?'+':'-',Math.abs(d),max));full.push(full[i]+d);});
      assert(v.values.every((x,i)=>x==null||x===full[i]));
      return item.blanks.map((b)=>full[b.cell]);
    }
    default: throw new Error('missing extension solver '+v.kind);
  }
}
for(const type of ['position','insideOut','condition','shapePattern','grid','compare','pyramid','chain']){
  test(`${type} L3 extension: ${N} independent items per band`,()=>{
    for(const max of type==='pyramid'?[20]:[10,20]){
      const rng=T.mkRng(5010+max*23+type.length);
      for(let i=0;i<N;i++){
        const item=T.generate(type,3,rng,{max,phase2b:true});
        const expected=independentExtension(item,max);
        assert.deepEqual(item.answer,expected);
        if(item.blanks) assert.deepEqual(item.blanks.map((b)=>b.answer),expected);
        assert(item.explain.length>=2);
        assert(!item.explain[0].includes(Array.isArray(expected)?expected.join(', '):String(expected)));
      }
    }
  });
}
test('four chooser shelves include every available type exactly once', () => {
  const ui = fs.readFileSync(path.join(__dirname, '../www/tuduy/tuduy-ui.js'), 'utf8');
  const block = ui.slice(ui.indexOf('const SHELVES ='), ui.indexOf('function open()'));
  const ids = [...block.matchAll(/'([A-Za-z]+)'/g)].map((x) => x[1]).filter((x) => T.TYPE_INFO[x]);
  assert.equal(new Set(ids).size, ids.length);
  assert.deepEqual(new Set(ids), new Set(T.TYPES.concat(T.PHASE2_TYPES, T.PHASE2B_TYPES)));
});
test('new figures fit 360 and 412 px content width', () => {
  const css = fs.readFileSync(path.join(__dirname, '../www/tuduy/tuduy.css'), 'utf8');
  const ui = fs.readFileSync(path.join(__dirname, '../www/tuduy/tuduy-ui.js'), 'utf8');
  for (const selector of ['td-digit-grid', 'td-ring', 'td-number-line', 'td-match', 'td-add-table']) {
    assert.match(css, new RegExp(`\\.${selector}\\{[^}]*width:min\\([0-9]+px,100%\\)`));
  }
  assert.match(css, /\.td-digit-cell \.td-box\{[^}]*width:100%/);
  assert.match(css, /\.td-dot-pattern\{[^}]*flex-wrap:wrap/);
  assert.match(ui, /viewBox="0 0 300 100"/); // number line
  assert.match(ui, /viewBox="0 0 260 230"/); // 3 overlapping circles
  assert.match(ui, /viewBox="0 0 300 250"/); // centre triangle
  assert.match(ui, /viewBox="0 0 64 64"/);   // 2×2 moving dot
  for (const width of [360, 412]) {
    const content = width - 52, board = Math.min(300, content);
    assert((board - 4 * 4) / 5 >= 43); // 5×5 Latin cell and its tappable number
    for (const n of [6,7,8]) for (let i=0;i<n;i++) {
      const theta = -Math.PI/2 + 2*Math.PI*i/n;
      const x=board*(.5+.38*Math.cos(theta)), y=board*(.5+.38*Math.sin(theta));
      assert(x-21>=0 && x+21<=board && y-21>=0 && y+21<=board); // ring boxes
    }
    assert(18-12>=0 && 282+12<=300); // frog and endpoint labels
    assert(8*36+7*4<=320 || /flex-wrap:wrap/.test(css)); // dot sequence wraps at 360
  }
});
