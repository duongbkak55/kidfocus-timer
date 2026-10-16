/* Luyện tư duy — pure engine (no DOM). Loaded by index.html and by Node tests.
 *
 * Every generator returns an item whose answers come from a solver, never from the
 * generator's own bookkeeping, and is rejected unless:
 *   - every number is within the selected 0..10 or 0..20 range,
 *   - max 20 arithmetic never carries or borrows across ten,
 *   - the blanks have exactly one solution,
 *   - the explanation steps end with the same answers the solver found.
 * Scale/picture items use substitution and addition/subtraction only (audit M11: no
 * ratio or multiplication reasoning).
 */
(function (root) {
  'use strict';

  let MAX = 10;
  const TYPES = ['pyramid', 'balance', 'chain', 'sequence', 'scale', 'shapes'];
  const PHASE2_TYPES = ['compare', 'order', 'neighbor', 'position', 'condition', 'shapePattern', 'grid', 'oddOne', 'insideOut', 'code', 'triSum'];
  const PHASE2B_TYPES = ['latin', 'magic', 'signs', 'triCenter', 'sumSplit', 'addTable', 'numberLine', 'ring', 'match'];
  const TYPE_INFO = {
    pyramid:  { name: 'Tháp số', emoji: '🔺' },
    balance:  { name: 'Ô trống cân bằng', emoji: '⚖️' },
    chain:    { name: 'Đoàn tàu phép tính', emoji: '🚂' },
    sequence: { name: 'Quy luật dãy số', emoji: '🔢' },
    scale:    { name: 'Mỗi hình là một số', emoji: '🍎' },
    shapes:   { name: 'Đếm hình', emoji: '🔷' },
    compare: { name: 'So sánh', emoji: '⚖️' },
    order: { name: 'Sắp xếp', emoji: '🔢' },
    neighbor: { name: 'Số liền kề', emoji: '🚂' },
    position: { name: 'Vị trí', emoji: '🐾' },
    condition: { name: 'Chọn số', emoji: '🎯' },
    shapePattern: { name: 'Quy luật hình', emoji: '🔶' },
    grid: { name: 'Bảng còn thiếu', emoji: '🧩' },
    oddOne: { name: 'Khác loại', emoji: '🔎' },
    insideOut: { name: 'Trong và ngoài', emoji: '⭕' },
    code: { name: 'Mật mã hình', emoji: '🔐' },
    triSum: { name: 'Tam giác số', emoji: '🔺' },
    latin: { name: 'Sudoku nhí', emoji: '🧩' },
    magic: { name: 'Ô vuông kì diệu', emoji: '✨' },
    signs: { name: 'Điền dấu cộng trừ', emoji: '➕' },
    triCenter: { name: 'Tổng ba góc', emoji: '🔺' },
    sumSplit: { name: 'Chọn bốn số', emoji: '🔢' },
    addTable: { name: 'Bảng cộng', emoji: '🧮' },
    numberLine: { name: 'Nhảy trên tia số', emoji: '🐸' },
    ring: { name: 'Vòng số', emoji: '⭕' },
    match: { name: 'Nối cùng kết quả', emoji: '🔗' },
  };

  /* ---------- RNG helpers (injectable for deterministic tests) ---------- */
  function mkRng(seed) {
    let s = (seed >>> 0) || 1;
    return function () { s ^= s << 13; s >>>= 0; s ^= s >>> 17; s ^= s << 5; s >>>= 0; return s / 4294967296; };
  }
  const ri = (r, lo, hi) => lo + Math.floor(r() * (hi - lo + 1));
  const pick = (r, a) => a[Math.floor(r() * a.length)];
  function shuffle(r, a) { const b = a.slice(); for (let i = b.length - 1; i > 0; i--) { const j = Math.floor(r() * (i + 1)); [b[i], b[j]] = [b[j], b[i]]; } return b; }
  const inRange = (v) => Number.isInteger(v) && v >= 0 && v <= MAX;
  const legalStep = (a, op, b) => inRange(a) && inRange(b) && inRange(op === '+' ? a + b : a - b) &&
    (MAX === 10 || (op === '+' ? a % 10 + b % 10 <= 9 : a % 10 >= b % 10));

  /* ======================= 1. Tháp số (pyramid) ======================= */
  // Cells: 0..2 bottom (a,b,c), 3..4 middle (a+b, b+c), 5 top. Rule: cell = sum of two below.
  const PYR_RULES = [[3, 0, 1], [4, 1, 2], [5, 3, 4]];
  function pyramidSolve(cells) {
    // Propagation only: fill a cell whenever its equation has exactly one unknown.
    const v = cells.slice();
    let progress = true;
    while (progress) {
      progress = false;
      for (const [s, a, b] of PYR_RULES) {
        const unk = [s, a, b].filter((i) => v[i] == null);
        if (unk.length !== 1) continue;
        const u = unk[0];
        const val = u === s ? v[a] + v[b] : v[s] - (u === a ? v[b] : v[a]);
        if (!inRange(val)) return null;
        v[u] = val; progress = true;
      }
    }
    return v.every((x) => x != null) ? v : null;
  }
  function pyramidBrute(cells) {
    // Independent check: count full assignments in the selected range consistent with givens.
    let count = 0, sol = null;
    for (let a = 0; a <= MAX; a++) for (let b = 0; b <= MAX; b++) for (let c = 0; c <= MAX; c++) {
      const full = [a, b, c, a + b, b + c, a + 2 * b + c];
      if (!full.every(inRange) || !legalStep(a, '+', b) || !legalStep(b, '+', c) || !legalStep(a + b, '+', b + c)) continue;
      if (full.every((x, i) => cells[i] == null || cells[i] === x)) { count++; sol = full; }
    }
    return count === 1 ? sol : null;
  }
  const PYR_BLANKS = {
    2: [[4, 2], [3, 0], [2, 4], [1, 2]],                 // missing next to a sum
    3: [[3, 4, 2], [0, 4, 2], [3, 1, 2], [0, 1, 4]],      // need a subtraction from the top
  };
  function genPyramid(r, level) {
    for (let t = 0; t < 200; t++) {
      const a = ri(r, 1, 4), b = ri(r, 1, 3), c = ri(r, 1, 4);
      const full = [a, b, c, a + b, b + c, a + 2 * b + c];
      if (!full.every(inRange) || full[5] < 5 || !legalStep(a, '+', b) || !legalStep(b, '+', c) || !legalStep(a + b, '+', b + c)) continue;
      const blanks = pick(r, PYR_BLANKS[level]);
      const shown = full.map((x, i) => (blanks.includes(i) ? null : x));
      const sol = pyramidSolve(shown);
      if (!sol || !pyramidBrute(shown)) continue;
      const names = ['ô dưới trái', 'ô dưới giữa', 'ô dưới phải', 'ô giữa trái', 'ô giữa phải', 'ô đỉnh'];
      const steps = [];
      const v = shown.slice();
      let guard = 0;
      while (v.some((x) => x == null) && guard++ < 6) {
        for (const [s, x, y] of PYR_RULES) {
          const unk = [s, x, y].filter((i) => v[i] == null);
          if (unk.length !== 1) continue;
          const u = unk[0];
          if (u === s) { v[s] = v[x] + v[y]; steps.push(`${names[s]} = ${v[x]} + ${v[y]} = ${v[s]}`); }
          else { const o = u === x ? y : x; v[u] = v[s] - v[o]; steps.push(`${names[u]}: ${v[o]} + ? = ${v[s]}, vậy ? = ${v[s]} − ${v[o]} = ${v[u]}`); }
        }
      }
      return {
        type: 'pyramid', level,
        prompt: 'Mỗi ô bằng tổng hai ô ngay bên dưới. Điền số vào ô trống.',
        visual: { kind: 'pyramid', cells: shown },
        blanks: blanks.map((i) => ({ id: 'c' + i, cell: i, answer: sol[i] })),
        explain: ['Mỗi ô = ô dưới bên trái + ô dưới bên phải.', ...steps],
        anim: { kind: 'pyramid', cells: sol, order: blanks },
      };
    }
    throw new Error('pyramid generation failed');
  }

  /* ======================= 2. Ô trống cân bằng (balance) ======================= */
  // Expression sides are token arrays like [6,'+',2]. One token in the item is the blank.
  function evalSide(tokens) {
    let acc = tokens[0];
    for (let i = 1; i < tokens.length; i += 2) {
      const n = tokens[i + 1];
      if (!legalStep(acc, tokens[i], n)) return null;
      acc = tokens[i] === '+' ? acc + n : acc - n;
      if (!inRange(acc)) return null; // also forbids negative intermediates
    }
    return acc;
  }
  function balanceSolutions(left, right, blankSide, blankIdx) {
    const sols = [];
    for (let x = 0; x <= MAX; x++) {
      const L = left.slice(), R = right.slice();
      (blankSide === 'L' ? L : R)[blankIdx] = x;
      const lv = evalSide(L), rv = evalSide(R);
      if (lv != null && rv != null && lv === rv) sols.push(x);
    }
    return sols;
  }
  function randSide(r, value, terms) {
    // Build `terms` numbers with +/- that evaluate to `value`, all intermediates 0..10.
    for (let t = 0; t < 100; t++) {
      const toks = [ri(r, 0, MAX)];
      for (let k = 1; k < terms; k++) toks.push(pick(r, ['+', '−'].map((o) => (o === '−' ? '-' : o))), ri(r, 0, MAX));
      // replace last operand so it evaluates to `value`
      const head = toks.slice(0, -1);
      const partial = evalSide(head.slice(0, -1));
      if (partial == null) continue;
      const op = head[head.length - 1];
      const last = op === '+' ? value - partial : partial - value;
      if (!inRange(last)) continue;
      const full = head.concat([last]);
      if (evalSide(full) === value && full.filter((x) => typeof x === 'number').some((x) => x !== 0)) return full;
    }
    return null;
  }
  const pretty = (t) => (t === '-' ? '−' : t);
  const show = (tokens) => tokens.map((t) => (t === '-' ? '−' : t)).join(' ');
  function genBalance(r, level) {
    for (let t = 0; t < 400; t++) {
      const value = ri(r, 2, MAX);
      const lTerms = level === 3 ? pick(r, [2, 3]) : 2;
      const rTerms = level === 3 && lTerms === 2 ? pick(r, [1, 2]) : (lTerms === 3 ? 1 : 2);
      const left = randSide(r, value, lTerms), right = randSide(r, value, rTerms);
      if (!left || !right) continue;
      const nums = (s) => s.map((x, i) => i).filter((i) => typeof s[i] === 'number');
      let side, idx;
      if (level === 2) { side = 'R'; idx = nums(right)[nums(right).length - 1]; }
      else { side = pick(r, ['L', 'R']); idx = pick(r, nums(side === 'L' ? left : right)); }
      const answer = (side === 'L' ? left : right)[idx];
      const sols = balanceSolutions(left, right, side, idx);
      if (sols.length !== 1 || sols[0] !== answer) continue;
      if (rTerms === 1 && side === 'R') continue;
      if (left.concat(right).some((x) => x === 0)) continue; // no trivial +0 / −0 // "= □" alone is plain arithmetic; keep it two-sided
      const known = side === 'L' ? right : left, withBlank = side === 'L' ? left : right;
      const kv = evalSide(known);
      const masked = withBlank.map((x, i) => (i === idx ? '□' : x));
      const steps = [];
      if (known.length > 1) steps.push(`Bên không có ô trống: ${show(known)} = ${kv}.`);
      else steps.push(`Bên kia bằng ${kv}.`);
      steps.push(`Vậy ${show(masked)} = ${kv}.`);
      // isolate: blank is a term in withBlank; compute it from the others
      const others = withBlank.map((x, i) => (i === idx ? null : x));
      // value of everything except the blank, with the blank's sign
      const sign = idx === 0 ? '+' : withBlank[idx - 1];
      let restVal = 0, restExpr = [];
      for (let i = 0; i < withBlank.length; i += 2) {
        if (i === idx) continue;
        const s = i === 0 ? '+' : withBlank[i - 1];
        restVal += s === '+' ? withBlank[i] : -withBlank[i];
        restExpr.push((s === '+' ? (restExpr.length ? '+ ' : '') : '− ') + withBlank[i]);
      }
      const bval = sign === '+' ? kv - restVal : restVal - kv;
      const otherCount = withBlank.filter((x, i) => typeof x === 'number' && i !== idx).length;
      if (otherCount > 1) steps.push(`Gộp các số còn lại: ${restExpr.join(' ')} = ${restVal}.`);
      if (sign === '+') {
        steps.push(restVal >= 0
          ? `□ = ${kv} − ${restVal} = ${bval} (thử lại: ${restVal} + ${bval} = ${kv}).`
          : `□ = ${kv} + ${-restVal} = ${bval} (thử lại: ${bval} − ${-restVal} = ${kv}).`);
      } else {
        steps.push(`□ = ${restVal} − ${kv} = ${bval} (thử lại: ${restVal} − ${bval} = ${kv}).`);
      }
      if (bval !== answer) continue; // explanation must reach the solver's answer
      void others;
      return {
        type: 'balance', level,
        prompt: 'Điền số vào ô trống để hai bên bằng nhau.',
        visual: { kind: 'equation', left: (side === 'L' ? masked : left).map(pretty), right: (side === 'R' ? masked : right).map(pretty) },
        blanks: [{ id: 'b', answer }],
        explain: ['Hai bên dấu = phải bằng nhau.', ...steps],
        anim: { kind: 'equation' },
      };
    }
    throw new Error('balance generation failed');
  }

  /* ======================= 3. Đoàn tàu phép tính (chain) ======================= */
  function genChain(r, level) {
    for (let t = 0; t < 400; t++) {
      const len = 6;
      const vals = [ri(r, 0, MAX)];
      const ops = [];
      let ok = true;
      for (let k = 0; k < len; k++) {
        const prev = vals[vals.length - 1];
        const cand = [];
        for (let d = 1; d <= 6; d++) { if (legalStep(prev, '+', d)) cand.push(d); if (legalStep(prev, '-', d)) cand.push(-d); }
        if (!cand.length) { ok = false; break; }
        const d = pick(r, cand); ops.push(d); vals.push(prev + d);
      }
      if (!ok || !vals.every(inRange)) continue;
      let blanks;
      if (level === 2) blanks = shuffle(r, [1, 2, 3, 4, 5, 6]).slice(0, 3).sort((a, b) => a - b);
      else blanks = [0].concat(shuffle(r, [1, 2, 3, 4, 5]).slice(0, 2)).sort((a, b) => a - b);
      // solver: forward from any known, backward from any known
      const shown = vals.map((v, i) => (blanks.includes(i) ? null : v));
      const solved = shown.slice();
      for (let pass = 0; pass < len + 1; pass++) {
        for (let i = 0; i < len; i++) {
          if (solved[i] != null && solved[i + 1] == null) solved[i + 1] = solved[i] + ops[i];
          if (solved[i + 1] != null && solved[i] == null) solved[i] = solved[i + 1] - ops[i];
        }
      }
      if (!solved.every((v, i) => v === vals[i])) continue;
      const fmt = (d) => (d > 0 ? '+' + d : '−' + -d);
      const steps = [];
      if (level === 3 && blanks[0] === 0) {
        const firstKnown = shown.findIndex((v) => v != null);
        let cur = vals[firstKnown];
        for (let i = firstKnown - 1; i >= 0; i--) {
          const d = ops[i];
          steps.push(`Đi ngược toa ${i + 1}: ${cur} ${d > 0 ? '−' : '+'} ${Math.abs(d)} = ${vals[i]} (ngược của ${fmt(d)}).`);
          cur = vals[i];
        }
      }
      for (const i of blanks.filter((b) => b > 0)) steps.push(`Toa ${i + 1}: ${vals[i - 1]} ${fmt(ops[i - 1]).replace(/^\+/, '+ ').replace(/^−/, '− ')} = ${vals[i]}.`);
      return {
        type: 'chain', level,
        prompt: level === 3
          ? 'Toa đầu bị mất số! Đi ngược lại để tìm, rồi điền các toa còn trống.'
          : 'Làm phép tính trên mỗi mũi tên để điền số vào toa trống.',
        visual: { kind: 'chain', values: shown, ops },
        blanks: blanks.map((i) => ({ id: 't' + i, cell: i, answer: vals[i] })),
        explain: ['Số toa sau = số toa trước làm phép tính trên mũi tên.', ...steps],
        anim: { kind: 'hops', values: vals, ops, order: blanks },
      };
    }
    throw new Error('chain generation failed');
  }

  /* ======================= 4. Quy luật dãy số (sequence) ======================= */
  const SEQ_RULES = {
    step: (r) => { const d = pick(r, [1, 2, -1, -2]); const n = 6; const start = d > 0 ? ri(r, 0, MAX - d * (n - 1)) : ri(r, -d * (n - 1), MAX); return { seq: Array.from({ length: n }, (_, i) => start + d * i), why: d > 0 ? `Mỗi số sau hơn số trước ${d}.` : `Mỗi số sau kém số trước ${-d}.` }; },
    zip: (r) => { const a = ri(r, 0, 3), b = ri(r, a + 3, 6); const seq = []; for (let i = 0; i < 4; i++) { seq.push(a + i, b + i); } return { seq, why: 'Hai dãy xen kẽ: vị trí lẻ tăng 1, vị trí chẵn tăng 1.' }; },
    tens: (r) => { const s = ri(r, 0, 3); const seq = []; for (let i = 0; i < 4; i++) { seq.push(s + i, MAX - s - i); } return { seq, why: 'Mỗi cặp số cộng lại bằng 10, số đầu cặp tăng dần.' }; },
    repeat: () => ({ seq: [1, 2, 2, 3, 3, 3, 4, 4, 4, 4], why: 'Số 1 viết 1 lần, số 2 viết 2 lần, số 3 viết 3 lần, số 4 viết 4 lần.' }),
    fib: () => ({ seq: [0, 1, 1, 2, 3, 5, 8], why: 'Từ số thứ ba, mỗi số bằng tổng hai số đứng ngay trước nó.' }),
    abab: (r) => { const [a, b, c] = shuffle(r, [0, 1, 2, 3, 4, 5, 6, 7, 8, 9]).slice(0, 3); const seq = [a, b, c, a, b, c, a, b]; return { seq, why: `Nhóm ${a}, ${b}, ${c} lặp lại.` }; },
  };
  function genSequence(r, level) {
    const pool = level === 2 ? ['step', 'step', 'abab', 'zip'] :
      (MAX === 20 ? ['repeat', 'fib', 'zip', 'step'] : ['tens', 'repeat', 'fib', 'zip', 'step']);
    for (let t = 0; t < 200; t++) {
      const kind = pick(r, pool);
      const { seq, why } = SEQ_RULES[kind](r);
      if (!seq.every(inRange)) continue;
      if (MAX === 20 && kind === 'step' && seq.some((v, i) => i > 0 && !legalStep(seq[i - 1], v >= seq[i - 1] ? '+' : '-', Math.abs(v - seq[i - 1])))) continue;
      const nBlank = level === 2 ? 1 : (seq.length >= 8 ? 2 : 1);
      // never blank the first two, so the rule stays visible
      const blanks = shuffle(r, seq.map((_, i) => i).slice(2)).slice(0, nBlank).sort((a, b) => a - b);
      // at least two full periods / three terms must remain visible before the first blank for 'abab'
      if (kind === 'abab' && blanks[0] < 3) continue;
      const shown = seq.map((v, i) => (blanks.includes(i) ? null : v));
      return {
        type: 'sequence', level,
        prompt: 'Tìm quy luật rồi điền số còn thiếu.',
        visual: { kind: 'sequence', values: shown },
        blanks: blanks.map((i) => ({ id: 's' + i, cell: i, answer: seq[i] })),
        explain: ['Xem các số thay đổi thế nào từ ô này sang ô bên cạnh.', why, ...blanks.map((i) => `Ô thứ ${i + 1} là ${seq[i]}.`)],
        anim: { kind: 'sequence', values: seq, order: blanks },
        rule: kind,
      };
    }
    throw new Error('sequence generation failed');
  }

  /* ======================= 5. Mỗi hình là một số (scale) ======================= */
  const FRUITS = ['🍎', '🍐', '🍌', '🍇', '🍓', '🍊', '🥕', '🧁'];
  function genScale(r, level) {
    for (let t = 0; t < 300; t++) {
      const [A, B, C] = shuffle(r, FRUITS).slice(0, 3);
      const a = ri(r, 1, 6), b = ri(r, 1, 6), c = ri(r, 0, 6);
      let facts, ask, answer, steps;
      if (level === 2) {
        if (!legalStep(a, '+', b)) continue;
        facts = [{ left: [A], right: [String(a)] }, { left: [A, B], right: [String(a + b)] }];
        ask = { left: [B] }; answer = b;
        steps = [`${A} = ${a}.`, `${A} + ${B} = ${a + b}, nên ${a} + ${B} = ${a + b}.`, `${B} = ${a + b} − ${a} = ${b}.`];
      } else {
        if (!legalStep(a, '+', b) || !legalStep(b, '+', c) || !legalStep(a, '+', c)) continue;
        facts = [{ left: [A], right: [String(a)] }, { left: [A, B], right: [String(a + b)] }, { left: [B, C], right: [String(b + c)] }];
        ask = { left: [A, C] }; answer = a + c;
        steps = [`${A} = ${a}.`, `${A} + ${B} = ${a + b}, nên ${B} = ${a + b} − ${a} = ${b}.`, `${B} + ${C} = ${b + c}, nên ${C} = ${b + c} − ${b} = ${c}.`, `${A} + ${C} = ${a} + ${c} = ${a + c}.`];
      }
      if (!inRange(answer)) continue;
      // solver: brute-force each symbol 0..10 against the facts; the asked value must be unique
      const syms = [...new Set(facts.flatMap((f) => f.left).concat(ask.left))];
      const vals = new Set();
      const rec = (i, env) => {
        if (i === syms.length) {
          if (facts.every((f) => f.left.reduce((s, x) => s + env[x], 0) === Number(f.right[0]))) vals.add(ask.left.reduce((s, x) => s + env[x], 0));
          return;
        }
        for (let v = 0; v <= MAX; v++) { env[syms[i]] = v; rec(i + 1, env); }
      };
      rec(0, {});
      if (vals.size !== 1 || !vals.has(answer)) continue;
      return {
        type: 'scale', level,
        prompt: 'Mỗi cân đều thăng bằng. Hình bên trái nặng bằng số bên phải. Điền số vào ô trống.',
        visual: { kind: 'scales', facts, ask },
        blanks: [{ id: 'q', answer }],
        explain: ['Cân thăng bằng nghĩa là hai bên bằng nhau.', ...steps],
        anim: { kind: 'scales' },
      };
    }
    throw new Error('scale generation failed');
  }

  /* ======================= 6. Đếm hình (shapes) ======================= */
  // Each figure is enumerated geometrically; the answer is the enumeration size and is
  // cross-checked against a closed formula in the tests.
  function fanTriangles(lines) { // triangle split from the apex by `lines` interior lines
    const n = lines + 1, out = [];
    for (let i = 0; i < n; i++) for (let j = i + 1; j <= n; j++) out.push({ from: i, to: j });
    return { segments: n, shapes: out };
  }
  function stripRects(cells) {
    const out = [];
    for (let i = 0; i < cells; i++) for (let j = i + 1; j <= cells; j++) out.push({ x0: i, x1: j, y0: 0, y1: 1 });
    return out;
  }
  function gridRects(w, h, squaresOnly) {
    const out = [];
    for (let x0 = 0; x0 < w; x0++) for (let x1 = x0 + 1; x1 <= w; x1++)
      for (let y0 = 0; y0 < h; y0++) for (let y1 = y0 + 1; y1 <= h; y1++) {
        if (squaresOnly && x1 - x0 !== y1 - y0) continue;
        out.push({ x0, x1, y0, y1 });
      }
    return out;
  }
  function genShapes(r, level) {
    const variants = level === 2
      ? ['fan1', 'fan2', 'strip2', 'strip3']
      : ['fan3', 'strip4', 'grid2sq', 'grid2ant'];
    const v = pick(r, variants);
    let visual, shapes, prompt, why;
    if (v.startsWith('fan')) {
      const lines = Number(v.slice(3));
      const f = fanTriangles(lines);
      shapes = f.shapes; visual = { kind: 'fan', segments: f.segments };
      prompt = 'Có tất cả bao nhiêu hình tam giác?';
      why = `Có ${f.segments} tam giác nhỏ. Đếm lần lượt tam giác ghép từ 1 mảnh, 2 mảnh, … cho đến cả hình lớn.`;
    } else if (v.startsWith('strip')) {
      const n = Number(v.slice(5));
      shapes = stripRects(n); visual = { kind: 'grid', w: n, h: 1, aspect: 1.6 };
      prompt = 'Có tất cả bao nhiêu hình chữ nhật?';
      why = `Có ${n} ô nhỏ. Đếm hình 1 ô, hình 2 ô, … cho đến cả dải.`;
    } else if (v === 'grid2sq') {
      shapes = gridRects(2, 2, true); visual = { kind: 'grid', w: 2, h: 2, aspect: 1 };
      prompt = 'Có tất cả bao nhiêu hình vuông?';
      why = 'Có 4 hình vuông nhỏ và 1 hình vuông to bao quanh.';
    } else {
      const all = gridRects(2, 2, false);
      const ant = { x: ri(r, 0, 1), y: ri(r, 0, 1) };
      shapes = all.filter((s) => ant.x >= s.x0 && ant.x < s.x1 && ant.y >= s.y0 && ant.y < s.y1);
      visual = { kind: 'grid', w: 2, h: 2, ant, aspect: 1.6 };
      prompt = 'Chú kiến nằm trong bao nhiêu hình chữ nhật?';
      why = 'Đếm các hình chữ nhật có chứa ô của chú kiến: ô đó, hai hình 2 ô, và cả hình lớn.';
    }
    const answer = shapes.length;
    return {
      type: 'shapes', level, prompt,
      visual,
      blanks: [{ id: 'n', answer }],
      explain: [why, `Đếm lần lượt được ${answer} hình.`],
      anim: { kind: 'highlight', shapes, figure: visual },
      variant: v,
    };
  }

  const GENS = { pyramid: genPyramid, balance: genBalance, chain: genChain, sequence: genSequence, scale: genScale, shapes: genShapes };
  function generate(type, level, rng, opts) {
    const phase2 = PHASE2_TYPES.includes(type), phase2b = PHASE2B_TYPES.includes(type);
    if (!GENS[type] && !phase2 && !phase2b) throw new Error('unknown type ' + type);
    if (level !== 2 && level !== 3) throw new Error('level must be 2 or 3');
    const max = opts && opts.max != null ? opts.max : 10;
    if (max !== 10 && max !== 20) throw new Error('max must be 10 or 20');
    if (type === 'triSum' && max === 10) throw new Error('triSum is for Lớp 1+');
    if (type === 'addTable' && max === 10) throw new Error('addTable is for Lớp 1+');
    const previous = MAX;
    MAX = max;
    try {
      if (opts && opts.phase2b && level === 3) {
        const extension = typeof module !== 'undefined' && module.exports ? require('./tuduy-phase2b-core.js') : root.TuDuyPhase2B;
        const item = extension.generateExtension(type, level, rng || Math.random, max);
        if (item) return item;
      }
      if (phase2) {
        const engine = typeof module !== 'undefined' && module.exports ? require('./tuduy-phase2-core.js') : root.TuDuyPhase2;
        return engine.generate(type, level, rng || Math.random, max);
      }
      if (phase2b) {
        const engine = typeof module !== 'undefined' && module.exports ? require('./tuduy-phase2b-core.js') : root.TuDuyPhase2B;
        return engine.generate(type, level, rng || Math.random, max);
      }
      return GENS[type](rng || Math.random, level);
    } finally { MAX = previous; }
  }

  /* ---------- Adaptive level + progress (pure; caller persists) ---------- */
  function nextLevel(stat) {
    const s = stat || { level: 2, streak: 0, miss: 0 };
    if (s.level === 2 && s.streak >= 4) return 3;
    if (s.level === 3 && s.miss >= 2) return 2;
    return s.level || 2;
  }
  function updateStat(stat, correct) {
    const s = Object.assign({ level: 2, streak: 0, miss: 0, seen: 0, right: 0 }, stat || {});
    s.seen += 1;
    if (correct) { s.right += 1; s.streak += 1; s.miss = 0; } else { s.streak = 0; s.miss += 1; }
    const lv = nextLevel(s);
    if (lv !== s.level) { s.level = lv; s.streak = 0; s.miss = 0; }
    return s;
  }

  const api = { MAX: 10, TYPES, PHASE2_TYPES, PHASE2B_TYPES, TYPE_INFO, mkRng, generate, updateStat, nextLevel,
    _internal: { pyramidSolve, pyramidBrute, balanceSolutions, evalSide, fanTriangles, stripRects, gridRects } };
  if (typeof module !== 'undefined' && module.exports) module.exports = api;
  else root.TuDuy = api;
})(typeof window !== 'undefined' ? window : this);
