/* Phase 2 puzzle engine. Pure data in, pure data out; no DOM or network. */
(function (root) {
  'use strict';
  const arithmetic = typeof module !== 'undefined' && module.exports ? require('./tuduy-arithmetic.js') : root.TuDuyArithmetic;
  const ri = (r, a, b) => a + Math.floor(r() * (b - a + 1));
  const pick = (r, a) => a[ri(r, 0, a.length - 1)];
  function shuffle(r, a) { const b = a.slice(); for (let i = b.length - 1; i; i--) { const j = ri(r, 0, i); [b[i], b[j]] = [b[j], b[i]]; } return b; }
  const range = (n) => Array.from({ length: n }, (_, i) => i);
  const step = arithmetic.step;
  function result(type, level, prompt, visual, input, answer, explain, more) {
    return Object.assign({ type, level, prompt, visual, input, answer, explain, anim: { kind: type } }, more || {});
  }
  const solveCompare = (l, r) => {
    const evalExpr = (e) => e.length === 1 ? e[0] : (e[1] === '+' ? e[0] + e[2] : e[0] - e[2]);
    return Math.sign(evalExpr(l) - evalExpr(r)) === 0 ? '=' : (evalExpr(l) > evalExpr(r) ? '>' : '<');
  };
  function expr(r, max, value) {
    if (value == null) value = ri(r, 0, max);
    for (let t = 0; t < 30; t++) {
      const op = pick(r, ['+', '−']);
      const b = ri(r, 1, Math.min(7, max));
      const a = op === '+' ? value - b : value + b;
      if (step(a, op === '+' ? '+' : '-', b, max)) return [a, op === '+' ? '+' : '-', b];
    }
    return [value];
  }
  function compare(r, level, max) {
    const leftValue = level === 2 ? ri(r, 3, max - 3) : ri(r, 1, max - 1);
    const relation = level === 2 ? pick(r, [-3, -2, -1, 0, 0, 1, 2, 3]) : pick(r, [-1, 0, 1]);
    const rightValue = leftValue + relation;
    const left = level === 3 ? expr(r, max, leftValue) : (r() < .5 ? [leftValue] : expr(r, max, leftValue));
    const right = level === 3 ? expr(r, max, rightValue) : [rightValue];
    const answer = solveCompare(left, right);
    const fmt = (e) => e.join(' ').replace('-', '−');
    return result('compare', level, 'Bên nào lớn hơn? Chọn dấu thích hợp.', { kind: 'compare', left, right }, 'sign', answer,
      ['Tính mỗi bên rồi mới chọn dấu.', `${fmt(left)} = ${leftValue}; ${fmt(right)} = ${rightValue}.`, `${leftValue} ${answer} ${rightValue}.`]);
  }
  function order(r, level, max) {
    const n = ri(r, 4, Math.min(level === 3 ? 6 : 5, max + 1));
    const nums = shuffle(r, range(max + 1)).slice(0, n);
    const ascending = r() < .5;
    const answer = nums.slice().sort((a, b) => ascending ? a - b : b - a);
    return result('order', level, `Chạm các số theo thứ tự ${ascending ? 'từ bé đến lớn' : 'từ lớn đến bé'}.`,
      { kind: 'order', nums, ascending }, 'order', answer,
      ['Nhìn xem số nào ở đầu dãy theo chiều cần xếp.', `Ta xếp lần lượt: ${answer.join(' → ')}.`]);
  }
  function neighbor(r, level, max) {
    const mode = level === 3 ? pick(r, ['both', 'twoPrev', 'middle']) : pick(r, ['prev', 'next', 'middle']);
    const n = ri(r, mode === 'twoPrev' ? 2 : 1, mode === 'next' || mode === 'both' || mode === 'middle' ? max - 1 : max);
    const shown = mode === 'twoPrev' ? [null, null, n] : mode === 'both' ? [null, n, null] :
      mode === 'prev' ? [null, n] : mode === 'next' ? [n, null] : [n - 1, null, n + 1];
    // Derive the missing numbers from a visible anchor, rather than retaining the sampled centre.
    const first = shown.findIndex((x) => x != null), full = shown.map((_, i) => shown[first] + i - first);
    const blanks = shown.map((v, i) => v == null ? { id: 'n' + i, cell: i, answer: full[i] } : null).filter(Boolean);
    const final = blanks.map((b) => b.answer);
    return result('neighbor', level, 'Điền số còn thiếu vào đoàn tàu số.', { kind: 'neighbor', values: shown }, 'numpad', final,
      ['Mỗi toa kế tiếp hơn toa trước một số.', `Dãy đúng là ${shown.map((v, i) => v == null ? blanks.find((b) => b.cell === i).answer : v).join(' → ')}.`], { blanks });
  }
  const ICONS = ['🐱','🐶','🐼','🐸','🦊','🐻','🐰','🐯','🐵','🐨'];
  function position(r, level) {
    const mode = level === 3 ? pick(r, ['left', 'right', 'middle', 'two']) : pick(r, ['left', 'right']);
    const icons = shuffle(r, ICONS).slice(0, mode === 'middle' ? pick(r, [5, 7]) : ri(r, 5, 7));
    const k = mode === 'middle' ? Math.ceil(icons.length / 2) : ri(r, 1, mode === 'two' ? icons.length - 1 : icons.length);
    const index = mode === 'left' ? k - 1 : mode === 'right' ? icons.length - k :
      mode === 'middle' ? Math.floor(icons.length / 2) : icons.length - k - 1;
    const prompt = mode === 'middle' ? 'Con nào đứng chính giữa?' : mode === 'two' ?
      `Từ bên phải đếm đến con thứ ${k}, rồi sang trái một bước. Chọn con đó.` :
      `Chọn con thứ ${k} tính từ bên ${mode === 'left' ? 'trái' : 'phải'}.`;
    return result('position', level, prompt, { kind: 'position', icons, mode, k }, 'choice', index,
      ['Đặt ngón tay ở đúng bên bắt đầu rồi đếm từng con.', `Con cần chọn là ${icons[index]} ở ô thứ ${index + 1} từ trái.`],
      { choices: range(icons.length) });
  }
  const matches = (n, rule) => {
    const inRange = rule.lo == null || (n > rule.lo && n < rule.hi);
    const parity = rule.parity == null || n % 2 === rule.parity;
    return inRange && parity;
  };
  function condition(r, level, max) {
    for (let t = 0; t < 100; t++) {
      const nums = shuffle(r, range(max + 1)).slice(0, ri(r, 6, 8));
      const rule = level === 3 ? { lo: ri(r, 0, max - 5), parity: ri(r, 0, 1) } :
        (r() < .5 ? { lo: ri(r, 0, max - 5), parity: null } : { lo: null, parity: ri(r, 0, 1) });
      if (rule.lo != null) rule.hi = ri(r, rule.lo + 4, Math.min(max + 1, rule.lo + 12));
      const answer = nums.filter((n) => matches(n, rule)).sort((a, b) => a - b);
      if (answer.length < 1 || answer.length > 4) continue;
      const rangeText = rule.lo == null ? '' : `lớn hơn ${rule.lo} và bé hơn ${rule.hi}`;
      const parityText = rule.parity == null ? '' : (rule.parity ? 'lẻ' : 'chẵn');
      const prompt = rangeText && parityText ? `Chọn số ${rangeText} và là số ${parityText}.` :
        rangeText ? `Chọn tất cả số ${rangeText}.` : `Chọn tất cả số ${parityText}.`;
      return result('condition', level, prompt, { kind: 'condition', nums, rule }, 'multi', answer,
        ['Đọc từng điều kiện, rồi xem từng số có đúng hết không.', `Các số đúng là: ${answer.join(', ')}.`],
        { choices: nums });
    }
    throw new Error('condition generation failed');
  }
  const SHAPES = ['●','▲','■','◆'];
  const ARROWS = ['↑','↗','→','↘','↓','↙','←','↖'];
  function patternAnswers(prefix, choices) {
    return choices.filter((c) => range(Math.floor(prefix.length / 2)).some((i) => {
      const p = i + 1;
      return prefix.every((x, j) => x === prefix[j % p]) && c === prefix[prefix.length % p];
    }));
  }
  function shapePattern(r, level) {
    const arrow = level === 3 && r() < .5;
    const period = arrow ? pick(r, [4, 8]) : pick(r, [2, 3]);
    const base = arrow ? (period === 4 ? [0, 2, 4, 6].map((i) => ARROWS[i]) : ARROWS.slice()) :
      (period === 2 ? shuffle(r, SHAPES).slice(0, 2) : pick(r, ['AAB','ABB','ABC']).split('').map((x, _, a) => {
        const selected = a.__symbols || (a.__symbols = shuffle(r, SHAPES));
        return selected[x.charCodeAt(0) - 65];
      }));
    const prefix = Array.from({ length: period * 2 }, (_, i) => base[i % period]);
    const choices = shuffle(r, arrow ? ARROWS.slice(0, 8) : SHAPES).slice(0, 4);
    const valid = patternAnswers(prefix, choices);
    if (valid.length !== 1) return shapePattern(r, level);
    return result('shapePattern', level, 'Hình nào tiếp theo trong dãy?', { kind: 'shapePattern', prefix, arrow }, 'choice', valid[0],
      ['Tìm nhóm hình lặp lại từ đầu dãy.', `Nhóm lặp là ${base.join(' ')}, nên hình tiếp theo là ${valid[0]}.`], { choices });
  }
  const COLORS = ['#ef5975','#477be8','#25a47f'];
  const SHAPE_NAMES = ['circle','triangle','square'];
  const colorName = { '#ef5975': 'hồng', '#477be8': 'xanh dương', '#25a47f': 'xanh lá' };
  const shapeName = { circle: 'hình tròn', triangle: 'hình tam giác', square: 'hình vuông' };
  function gridValid(tiles, n, candidate, missing) {
    const full = tiles.slice(); full[missing] = candidate;
    if (n === 2) return range(2).every((row) => full[row * 2].shape === full[row * 2 + 1].shape) &&
      range(2).every((col) => full[col].color === full[col + 2].color);
    return range(3).every((i) => {
      const row = full.slice(i * 3, i * 3 + 3), col = [full[i], full[i + 3], full[i + 6]];
      return new Set(row.map((x) => x.shape)).size === 3 && new Set(row.map((x) => x.color)).size === 3 &&
        new Set(col.map((x) => x.shape)).size === 3 && new Set(col.map((x) => x.color)).size === 3;
    });
  }
  function grid(r, level) {
    const n = level === 3 ? 3 : 2;
    const sr = shuffle(r, SHAPE_NAMES), cr = shuffle(r, COLORS);
    const tiles = range(n * n).map((i) => {
      const row = Math.floor(i / n), col = i % n;
      return { shape: n === 2 ? sr[row] : sr[(row + col) % 3],
        color: n === 2 ? cr[col] : cr[(row + 2 * col) % 3] };
    });
    const missing = ri(r, 0, tiles.length - 1), all = SHAPE_NAMES.flatMap((shape) => COLORS.map((color) => ({ shape, color })));
    const shown = tiles.map((x, i) => i === missing ? null : x);
    const correct = all.filter((x) => gridValid(shown, n, x, missing));
    if (correct.length !== 1) throw new Error('grid is ambiguous');
    const wrong = shuffle(r, all.filter((x) => !gridValid(shown, n, x, missing))).slice(0, 3);
    const choices = shuffle(r, correct.concat(wrong));
    return result('grid', level, 'Ô dấu hỏi cần hình nào?', { kind: 'tileGrid', n, tiles: shown, missing }, 'choice',
      choices.findIndex((x) => x.shape === correct[0].shape && x.color === correct[0].color),
      ['Nhìn quy luật hình và màu theo từng hàng, từng cột.', `Ô cần điền là ${shapeName[correct[0].shape]} màu ${colorName[correct[0].color]}.`], { choices });
  }
  function oddOne(r, level) {
    const attribute = pick(r, ['shape','color']);
    const common = attribute === 'shape' ? pick(r, SHAPE_NAMES) : pick(r, COLORS);
    const odd = pick(r, (attribute === 'shape' ? SHAPE_NAMES : COLORS).filter((x) => x !== common));
    const fixed = attribute === 'shape' ? pick(r, COLORS) : pick(r, SHAPE_NAMES);
    const index = ri(r, 0, 3);
    const items = range(4).map((i) => ({
      shape: attribute === 'shape' ? (i === index ? odd : common) : fixed,
      color: attribute === 'color' ? (i === index ? odd : common) : fixed,
      size: 40,
    }));
    const singles = [...new Set(['shape','color','size'].flatMap((key) => range(4).filter((i) => items.filter((x) => x[key] === items[i][key]).length === 1)))];
    if (singles.length !== 1) throw new Error('oddOne ambiguity');
    const answer = singles[0];
    return result('oddOne', level, 'Hình nào khác ba hình còn lại?', { kind: 'oddOne', items }, 'choice', answer,
      ['So từng hình theo hình dạng và màu sắc.', `Hình thứ ${answer + 1} khác ở ${attribute === 'shape' ? 'hình dạng' : 'màu sắc'}.`],
      { choices: range(4) });
  }
  const CIRCLE = { x: 120, y: 120, r: 70 }, RECT = { x: 110, y: 70, w: 110, h: 110 };
  const POINTS = [{ x: 80, y: 120 }, { x: 205, y: 120 }, { x: 150, y: 120 }, { x: 45, y: 30 }];
  const OBJECTS = ['🐱','🍎','🚗','⭐'];
  function region(p, c, rect) {
    const circle = Math.hypot(p.x - c.x, p.y - c.y) < c.r;
    const square = p.x > rect.x && p.x < rect.x + rect.w && p.y > rect.y && p.y < rect.y + rect.h;
    return (circle ? 1 : 0) + (square ? 2 : 0);
  }
  function insideOut(r, level) {
    const dx = ri(r, -10, 10), dy = ri(r, -10, 10);
    const circle = { ...CIRCLE, x: CIRCLE.x + dx, y: CIRCLE.y + dy };
    const rect = { ...RECT, x: RECT.x + dx, y: RECT.y + dy };
    const objects = shuffle(r, OBJECTS).map((icon, i) => ({ icon, x: POINTS[i].x + dx, y: POINTS[i].y + dy }));
    const target = ri(r, 0, 3), answer = objects.findIndex((o) => region(o, circle, rect) === target);
    const names = ['ngoài cả hai hình', 'trong hình tròn nhưng ngoài hình vuông',
      'trong hình vuông nhưng ngoài hình tròn', 'trong cả hai hình'];
    return result('insideOut', level, `Vật nào ở ${names[target]}?`,
      { kind: 'insideOut', circle, rect, objects, target }, 'choice', answer,
      ['Nhìn xem vật nằm ở phía nào của từng đường viền.', `${objects[answer].icon} ở ${names[target]}.`],
      { choices: range(objects.length) });
  }
  function decode(legend, row) { return row.map((x) => legend[x]); }
  function code(r, level) {
    const icons = shuffle(r, ICONS).slice(0, ri(r, 3, 4));
    const digits = shuffle(r, range(10)).slice(0, icons.length);
    const legend = Object.fromEntries(icons.map((icon, i) => [icon, digits[i]]));
    const row = Array.from({ length: ri(r, 3, 4) }, () => pick(r, icons));
    const decoded = decode(legend, row), word = decoded.join('');
    const explain = ['Đọc chú giải từng hình rồi ghép các chữ số theo thứ tự.', `Các hình cho dãy số ${word}.`];
    if (level === 3) return result('code', level, 'Mỗi hình ứng với một chữ số. Điền mật mã.', { kind: 'code', legend, row }, 'numpad',
      decoded, explain, { blanks: decoded.map((answer, i) => ({ id: 'd' + i, cell: i, answer })) });
    const swap = decoded.slice(); [swap[0], swap[1]] = [swap[1], swap[0]];
    const mutate = (i) => { const a = decoded.slice(); a[i] = (a[i] + 1) % 10; return a.join(''); };
    const choices = shuffle(r, [...new Set([word, swap.join(''), mutate(0), mutate(decoded.length - 1), mutate(1)])]).slice(0, 4);
    if (!choices.includes(word) || choices.length !== 4) return code(r, level);
    return result('code', level, 'Mỗi hình ứng với một chữ số. Chọn mật mã đúng.', { kind: 'code', legend, row }, 'choice',
      choices.indexOf(word), explain, { choices });
  }
  let triCache;
  function triSolutions() {
    if (triCache) return triCache;
    triCache = {};
    function perm(a, p) {
      if (!a.length) {
        const [A, B, C, AB, BC, CA] = p;
        const s = A + AB + B;
        if (s >= 9 && s <= 12 && B + BC + C === s && C + CA + A === s) (triCache[s] ||= []).push(p);
        return;
      }
      a.forEach((v, i) => perm(a.filter((_, j) => j !== i), p.concat(v)));
    }
    perm([1,2,3,4,5,6], []);
    return triCache;
  }
  function triSum(r, level) {
    const bySum = triSolutions();
    for (let t = 0; t < 200; t++) {
      // Totals through 10 are Grade-1 facts; totals above 10 would carry.
      const S = pick(r, [9, 10]);
      const full = pick(r, bySum[S]);
      const givens = shuffle(r, range(6)).slice(0, pick(r, [3,4]));
      const matches = bySum[S].filter((p) => givens.every((i) => p[i] === full[i]));
      if (matches.length !== 1) continue;
      const shown = full.map((x, i) => givens.includes(i) ? x : null);
      const blanks = range(6).filter((i) => shown[i] == null).map((i) => ({ id: 'v' + i, cell: i, answer: matches[0][i] }));
      return result('triSum', level, `Mỗi cạnh cộng được ${S}. Điền số còn thiếu.`,
        { kind: 'triSum', values: shown, sum: S }, 'numpad', blanks.map((b) => b.answer),
        ['Mỗi cạnh gồm hai góc và một ô ở giữa.', `Ba góc là ${matches[0].slice(0,3).join(', ')}; ba ô giữa cạnh là ${matches[0].slice(3).join(', ')}. Mỗi cạnh bằng ${S}.`],
        { blanks });
    }
    throw new Error('triSum unique generation failed');
  }
  const GENS = { compare, order, neighbor, position, condition, shapePattern, grid, oddOne, insideOut, code, triSum };
  function generate(type, level, rng, max) {
    if (!GENS[type]) throw new Error('unknown phase2 type ' + type);
    return GENS[type](rng, level, max);
  }
  const api = { generate, _internal: { step } };
  if (typeof module !== 'undefined' && module.exports) module.exports = api;
  else root.TuDuyPhase2 = api;
})(typeof window !== 'undefined' ? window : this);
