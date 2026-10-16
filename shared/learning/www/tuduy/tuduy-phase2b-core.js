/* Luyện tư duy phase 2B. Pure puzzle generation; no DOM or device APIs. */
(function (root) {
  'use strict';
  const arithmetic = typeof module !== 'undefined' && module.exports ? require('./tuduy-arithmetic.js') : root.TuDuyArithmetic;
  const ri = (r, a, b) => a + Math.floor(r() * (b - a + 1));
  const pick = (r, a) => a[ri(r, 0, a.length - 1)];
  const range = (n) => Array.from({ length: n }, (_, i) => i);
  function shuffle(r, a) { const b = a.slice(); for (let i = b.length - 1; i > 0; i--) { const j = ri(r, 0, i); [b[i], b[j]] = [b[j], b[i]]; } return b; }
  const step = arithmetic.step;
  const shownAnswer = (type, level, prompt, visual, input, answer, explain, extra) =>
    Object.assign({ type, level, prompt, visual, input, answer, explain, anim: { kind: type } }, extra || {});
  const blank = (cell, answer, prefix) => ({ id: prefix + cell, cell, answer });

  function latinCount(cells, n, limit) {
    const work = cells.slice(); let count = 0, found = null;
    function walk() {
      const open = work.findIndex((x) => x == null);
      if (open < 0) { count++; found = work.slice(); return; }
      const row = Math.floor(open / n), col = open % n;
      for (let v = 1; v <= n; v++) {
        if (range(n).some((i) => work[row * n + i] === v || work[i * n + col] === v)) continue;
        work[open] = v; walk(); work[open] = null;
        if (count >= limit) return;
      }
    }
    walk(); return { count, found };
  }
  function latin(r, level, max) {
    const n = max === 10 ? (level === 2 ? 3 : 4) : (level === 2 ? 4 : 5);
    const rowOrder = shuffle(r, range(n)), colOrder = shuffle(r, range(n)), symbols = shuffle(r, range(n).map((i) => i + 1));
    const full = range(n * n).map((i) => symbols[(rowOrder[Math.floor(i / n)] + colOrder[i % n]) % n]);
    for (let trial = 0; trial < 80; trial++) {
      const holes = shuffle(r, range(n * n)).slice(0, ri(r, 2, 4));
      const cells = full.map((v, i) => holes.includes(i) ? null : v);
      const solved = latinCount(cells, n, 2);
      if (solved.count !== 1) continue;
      const blanks = holes.sort((a, b) => a - b).map((i) => blank(i, solved.found[i], 'l'));
      return shownAnswer('latin', level, 'Mỗi hàng và mỗi cột có đủ các số, không lặp. Điền ô trống.',
        { kind: 'latin', n, cells }, 'numpad', blanks.map((b) => b.answer),
        ['Xem hàng và cột của từng ô: số nào còn thiếu?', `Các ô trống lần lượt là ${blanks.map((b) => b.answer).join(', ')}.`], { blanks });
    }
    throw new Error('latin unique generation failed');
  }

  function magic(r, level, max) {
    const candidates = max === 10 ?
      [[0, 1, 2], [1, 2, 3], [0, 2, 4], [1, 2, 4], [2, 3, 4], [1, 3, 5]] :
      [[0, 1, 2], [1, 2, 3], [0, 2, 4], [1, 2, 4], [2, 3, 4], [1, 3, 5], [10, 2, 3]];
    // The book's 1–4 / sum-10 column variant conflicts with both the 5–6 S≤9
    // band and the Lớp 1 no-carry rule, so it remains paper-only.
    const triple = shuffle(r, pick(r, candidates));
    const sum = triple.reduce((a, b) => a + b, 0);
    const full = [triple[0], triple[1], triple[2], triple[2], triple[0], triple[1], triple[1], triple[2], triple[0]];
    // One hidden cell in each row and column makes every hidden value deducible from its row.
    const cols = shuffle(r, [0, 1, 2]);
    const holes = cols.map((col, row) => row * 3 + col);
    const cells = full.map((v, i) => holes.includes(i) ? null : v);
    const solved = cells.slice();
    for (let row = 0; row < 3; row++) {
      const index = row * 3 + cols[row];
      solved[index] = sum - solved.slice(row * 3, row * 3 + 3).reduce((a, b) => a + (b || 0), 0);
    }
    if (!range(3).every((col) => solved[col] + solved[col + 3] + solved[col + 6] === sum)) throw new Error('magic solver mismatch');
    const blanks = holes.map((i) => blank(i, solved[i], 'm'));
    return shownAnswer('magic', level, `Mỗi hàng và cột cộng được ${sum}. Điền các ô trống.`,
      { kind: 'magic', n: 3, cells, sum }, 'numpad', blanks.map((b) => b.answer),
      ['Cộng các ô đã biết trong một hàng, rồi tìm số còn thiếu.', `Điền lần lượt ${blanks.map((b) => b.answer).join(', ')}; mỗi hàng và cột được ${sum}.`], { blanks });
  }

  function signSolutions(nums, target) {
    const out = [], k = nums.length - 1;
    for (let mask = 0; mask < (1 << k); mask++) {
      let value = nums[0], nonnegative = true; const ops = [];
      for (let i = 0; i < k; i++) {
        const op = mask & (1 << i) ? '+' : '-'; ops.push(op);
        value += op === '+' ? nums[i + 1] : -nums[i + 1];
        if (value < 0) { nonnegative = false; break; }
      }
      if (nonnegative && value === target) out.push(ops);
    }
    return out;
  }
  function signs(r, level, max) {
    const k = level === 2 ? 2 : 3;
    for (let trial = 0; trial < 500; trial++) {
      const nums = [ri(r, 3, Math.min(max, 16)), ...range(k).map(() => ri(r, 1, 5))];
      let target = nums[0], valid = true;
      const ops = range(k).map(() => pick(r, ['+', '-']));
      for (let i = 0; i < k; i++) {
        if (!step(target, ops[i], nums[i + 1], max)) { valid = false; break; }
        target += ops[i] === '+' ? nums[i + 1] : -nums[i + 1];
      }
      if (!valid) continue;
      const solutions = signSolutions(nums, target);
      if (solutions.length !== 1) continue;
      const answer = solutions[0];
      const blanks = answer.map((op, i) => blank(i, op, 's'));
      return shownAnswer('signs', level, 'Chọn dấu cộng hoặc trừ để phép tính đúng.',
        { kind: 'signs', nums, target }, 'signs', answer,
        ['Thử đi từ trái sang phải, giữ kết quả sau mỗi bước.', `${nums.map((n, i) => i ? `${answer[i - 1] === '-' ? '−' : '+'} ${n}` : String(n)).join(' ')} = ${target}.`], { blanks });
    }
    throw new Error('signs unique generation failed');
  }

  function triCenter(r, level, max) {
    for (let trial = 0; trial < 100; trial++) {
      const corners = range(3).map(() => ri(r, 1, max === 10 ? 4 : 8));
      if (!step(corners[0], '+', corners[1], max) || !step(corners[0] + corners[1], '+', corners[2], max)) continue;
      const center = corners.reduce((a, b) => a + b, 0), hidden = level === 2 ? 3 : ri(r, 0, 2);
      const values = corners.concat(center), shown = values.map((v, i) => i === hidden ? null : v);
      const solved = hidden === 3 ? shown[0] + shown[1] + shown[2] :
        shown[3] - shown.slice(0, 3).reduce((sum, value) => sum + (value || 0), 0);
      const blanks = [blank(hidden, solved, 't')];
      return shownAnswer('triCenter', level, 'Số ở giữa bằng tổng ba góc. Điền ô trống.',
        { kind: 'triCenter', values: shown }, 'numpad', [solved],
        ['Cộng ba góc để tìm số giữa; nếu thiếu một góc thì đếm ngược từ số giữa.',
          `${corners.join(' + ')} = ${center}; ô trống là ${values[hidden]}.`], { blanks });
    }
    throw new Error('triCenter generation failed');
  }

  function equalPairings(nums) {
    const out = [];
    for (let unused = 0; unused < 5; unused++) {
      const four = nums.filter((_, i) => i !== unused);
      if (four[0] + four[1] === four[2] + four[3] ||
          four[0] + four[2] === four[1] + four[3] ||
          four[0] + four[3] === four[1] + four[2]) out.push(unused);
    }
    return out;
  }
  function sumSplit(r, level, max) {
    for (let trial = 0; trial < 300; trial++) {
      const nums = shuffle(r, range(10).map((i) => i + 1)).slice(0, 5);
      const solved = equalPairings(nums);
      if (solved.length !== 1) continue;
      const unused = solved[0];
      const four = nums.filter((_, i) => i !== unused);
      const pairings = [[0, 1, 2, 3], [0, 2, 1, 3], [0, 3, 1, 2]];
      const pairing = pairings.find(([a, b, c, d]) =>
        four[a] + four[b] === four[c] + four[d] && step(four[a], '+', four[b], max) && step(four[c], '+', four[d], max));
      if (!pairing) continue;
      const choices = shuffle(r, [nums[unused], ...shuffle(r, nums.filter((_, i) => i !== unused)).slice(0, 3)]);
      return shownAnswer('sumSplit', level, 'Chọn bốn số để hai bên cộng bằng nhau. Số nào không dùng?',
        { kind: 'sumSplit', nums }, 'choice', choices.indexOf(nums[unused]),
        ['Thử ghép thành hai cặp có tổng bằng nhau.',
          `${four[pairing[0]]} + ${four[pairing[1]]} = ${four[pairing[2]]} + ${four[pairing[3]]}; số không dùng là ${nums[unused]}.`], { choices });
    }
    throw new Error('sumSplit unique generation failed');
  }

  function addTable(r, level, max) {
    if (max !== 20) throw new Error('addTable is for Lớp 1+');
    const rows = shuffle(r, [11, 12, 13]).slice(0, 2), cols = shuffle(r, [1, 2, 3]).slice(0, 2);
    if (rows.some((a) => cols.some((b) => !step(a, '+', b, max)))) return addTable(r, level, max);
    const cells = rows.flatMap((a) => cols.map((b) => a + b));
    const holes = level === 2 ? shuffle(r, range(4)).slice(0, ri(r, 2, 3)) : [pick(r, [0, 1]), pick(r, [2, 3])];
    const shownCells = cells.map((v, i) => holes.includes(i) ? null : v);
    const shownRows = rows.slice(); const blanks = holes.map((i) => blank(i, cells[i], 'a'));
    if (level === 3) {
      // The first row is still anchored by its other visible cell.
      shownRows[0] = null;
      blanks.unshift({ id: 'row0', cell: 'row0', answer: rows[0] });
      if (shownCells[0] == null && shownCells[1] == null) return addTable(r, level, max);
    }
    const solvedRows = shownRows.slice();
    if (solvedRows[0] == null) {
      const anchor = shownCells[0] != null ? 0 : 1;
      solvedRows[0] = shownCells[anchor] - cols[anchor];
    }
    const solvedCells = solvedRows.flatMap((row) => cols.map((col) => row + col));
    blanks.forEach((b) => { b.answer = b.cell === 'row0' ? solvedRows[0] : solvedCells[b.cell]; });
    return shownAnswer('addTable', level, 'Số trong ô bằng số đầu hàng cộng số đầu cột.',
      { kind: 'addTable', rows: shownRows, cols, cells: shownCells }, 'numpad', blanks.map((b) => b.answer),
      ['Nhìn số đầu hàng và đầu cột; cộng hai số đó.',
        `Các ô trống lần lượt là ${blanks.map((b) => b.answer).join(', ')}.`], { blanks });
  }

  function numberLine(r, level, max) {
    for (let trial = 0; trial < 100; trial++) {
      const from = ri(r, 1, max - 1), distance = ri(r, 1, Math.min(5, max));
      const op = pick(r, ['+', '-']);
      if (!step(from, op, distance, max)) continue;
      const to = from + (op === '+' ? distance : -distance);
      const visual = { kind: 'numberLine', max, from, to, op };
      const solved = [visual.from, Math.abs(visual.to - visual.from), visual.to];
      const blanks = solved.map((v, i) => blank(i, v, 'n'));
      return shownAnswer('numberLine', level, 'Nhìn bước nhảy rồi điền phép tính.',
        visual, 'numpad', solved,
        ['Xem ếch bắt đầu ở đâu, nhảy mấy bước, rồi dừng ở đâu.',
          `${from} ${op === '+' ? '+' : '−'} ${distance} = ${to}.`], { blanks });
    }
    throw new Error('numberLine generation failed');
  }

  function ring(r, level, max) {
    const n = level === 2 ? 6 : pick(r, [7, 8]);
    for (let trial = 0; trial < 300; trial++) {
      const values = [ri(r, 3, max - 3)], ops = [];
      for (let i = 0; i < n - 1; i++) {
        const cur = values[values.length - 1], cand = [];
        for (let d = -3; d <= 3; d++) if (d && step(cur, d > 0 ? '+' : '-', Math.abs(d), max)) cand.push(d);
        const d = pick(r, cand); ops.push(d); values.push(cur + d);
      }
      const close = values[0] - values[n - 1];
      if (!close || Math.abs(close) > 3 || !step(values[n - 1], close > 0 ? '+' : '-', Math.abs(close), max)) continue;
      ops.push(close);
      const solved = [values[0]];
      ops.forEach((d) => solved.push(solved.at(-1) + d));
      if (solved.at(-1) !== solved[0]) throw new Error('ring does not close');
      const secondGiven = level === 3 ? Math.floor(n / 2) : -1;
      const shown = values.map((value, i) => i === 0 || i === secondGiven ? value : null);
      const blanks = range(n).filter((i) => shown[i] == null).map((i) => blank(i, solved[i], 'r'));
      return shownAnswer('ring', level, 'Đi theo mũi tên quanh vòng. Điền số còn thiếu.',
        { kind: 'ring', values: shown, ops }, 'numpad', blanks.map((b) => b.answer),
        ['Từ số đã cho, làm phép tính trên từng mũi tên.',
          `Đi một vòng được ${values.join(' → ')} → ${values[0]}.`], { blanks });
    }
    throw new Error('ring closed generation failed');
  }

  function expression(r, value, max) {
    const candidates = [];
    for (let d = 1; d <= 5; d++) {
      if (step(value - d, '+', d, max)) candidates.push([value - d, '+', d]);
      if (step(value + d, '-', d, max)) candidates.push([value + d, '-', d]);
    }
    return pick(r, candidates);
  }
  function match(r, level, max) {
    const count = level === 2 ? 3 : 4;
    const values = shuffle(r, range(max - 2).map((i) => i + 2)).slice(0, count);
    const left = values.map((v) => expression(r, v, max));
    const rightValues = shuffle(r, values), right = rightValues.map((v) => expression(r, v, max));
    const answer = left.map((e) => right.findIndex((other) => expressionValue(e) === expressionValue(other)));
    return shownAnswer('match', level, 'Nối hai phép tính có cùng kết quả.',
      { kind: 'match', left, right }, 'pairs', answer,
      ['Tính từng phép ở cột trái và cột phải rồi nối hai kết quả giống nhau.',
        `Nối ${answer.map((j, i) => `${i + 1}–${j + 1}`).join(', ')}.`]);
  }

  /* Extensions are opt-in so the phase-2 contract and its 31 older checks stay intact. */
  function queue(r) {
    const names = shuffle(r, ['An', 'Bình', 'Chi', 'Dũng', 'Hà', 'Lan', 'Minh']).slice(0, ri(r, 5, 7));
    const index = ri(r, 1, names.length - 2), ahead = index, behind = names.length - index - 1;
    const answer = range(names.length).filter((i) => i === ahead && names.length - i - 1 === behind);
    if (answer.length !== 1) throw new Error('queue is ambiguous');
    return shownAnswer('position', 3, `Có ${ahead} bạn đứng trước và ${behind} bạn đứng sau. Chọn bạn đó.`,
      { kind: 'queue', names, ahead, behind }, 'choice', answer[0],
      ['Đếm từ đầu hàng: số bạn đứng trước là số bước cần đi.', `${names[index]} đứng thứ ${index + 1} từ đầu hàng.`], { choices: range(names.length) });
  }
  function venn(r) {
    const dx = ri(r, -10, 10), dy = ri(r, -10, -2);
    const circles = [{ x: 100 + dx, y: 110 + dy, r: 65 }, { x: 160 + dx, y: 110 + dy, r: 65 }, { x: 130 + dx, y: 155 + dy, r: 65 }];
    const points = [{ x: 60, y: 80 }, { x: 130, y: 70 }, { x: 130, y: 110 }];
    const count = ri(r, 1, 3), kangaroo = { x: points[count - 1].x + dx, y: points[count - 1].y + dy };
    const solved = circles.filter((c) => Math.hypot(kangaroo.x - c.x, kangaroo.y - c.y) < c.r).length;
    return shownAnswer('insideOut', 3, 'Bạn kangaroo nằm trong mấy hình tròn?',
      { kind: 'venn3', circles, kangaroo }, 'choice', solved - 1,
      ['Nhìn từng đường tròn rồi đếm những hình ôm lấy bạn kangaroo.', `Bạn kangaroo nằm trong ${count} hình tròn.`],
      { choices: [1, 2, 3] });
  }
  function expressionCondition(r, max) {
    const target = ri(r, 3, Math.min(max - 2, 9));
    const values = shuffle(r, range(max - 1).map((i) => i + 1).filter((v) => v !== target)).slice(0, 5);
    values[ri(r, 0, 4)] = target;
    const expressions = shuffle(r, [target, ...values]).map((v) => expression(r, v, max));
    const answer = expressions.map((e, i) => expressionValue(e) === target ? i : null).filter((i) => i != null);
    return shownAnswer('condition', 3, `Chọn các phép tính có kết quả bằng ${target}.`,
      { kind: 'expressionCondition', expressions, target }, 'multi', answer,
      ['Tính từng phép rồi so với số đã cho.', `Các phép đúng là ô số ${answer.map((i) => i + 1).join(', ')}.`], { choices: range(expressions.length) });
  }
  const expressionValue = (e) => e[1] === '+' ? e[0] + e[2] : e[0] - e[2];
  function dotPattern(r) {
    const start = ri(r, 0, 3), turn = pick(r, [1, 3]);
    const prefix = range(8).map((i) => (start + turn * i) % 4);
    const possible = [1, 3].filter((d) => prefix.every((x, i) => x === (prefix[0] + d * i) % 4));
    if (possible.length !== 1) throw new Error('dot pattern ambiguous');
    const answer = (prefix[0] + possible[0] * prefix.length) % 4;
    return shownAnswer('shapePattern', 3, 'Chấm tròn đi quanh bốn ô. Hình nào tiếp theo?',
      { kind: 'dotPattern', prefix }, 'choice', answer,
      ['Theo dõi chấm tròn đi qua từng góc theo cùng một chiều.', `Chấm tiếp theo ở góc ${answer + 1}.`], { choices: range(4) });
  }
  function dotGrid(r) {
    const shapes = shuffle(r, ['●', '▲', '■']), dots = shuffle(r, [1, 2, 3]);
    const full = range(9).map((i) => {
      const row = Math.floor(i / 3), col = i % 3;
      return { shape: shapes[(row + col) % 3], dots: dots[(row + 2 * col) % 3] };
    });
    const missing = ri(r, 0, 8), cells = full.map((x, i) => i === missing ? null : x);
    const all = shapes.flatMap((shape) => dots.map((count) => ({ shape, dots: count })));
    const candidates = all.filter((tile) => range(3).every((j) => {
      const a = cells.slice(); a[missing] = tile;
      const row = a.slice(j * 3, j * 3 + 3), col = [a[j], a[j + 3], a[j + 6]];
      return new Set(row.map((x) => x.shape)).size === 3 && new Set(row.map((x) => x.dots)).size === 3 &&
        new Set(col.map((x) => x.shape)).size === 3 && new Set(col.map((x) => x.dots)).size === 3;
    }));
    if (candidates.length !== 1) throw new Error('dot grid is ambiguous');
    const choices = shuffle(r, [candidates[0], ...shuffle(r, all.filter((x) => x !== candidates[0] &&
      (x.shape !== candidates[0].shape || x.dots !== candidates[0].dots))).slice(0, 3)]);
    return shownAnswer('grid', 3, 'Ô dấu hỏi cần hình nào và mấy chấm?',
      { kind: 'dotGrid', n: 3, cells, missing }, 'choice', choices.findIndex((x) => x.shape === candidates[0].shape && x.dots === candidates[0].dots),
      ['Mỗi hàng và cột cần đủ ba hình và ba số chấm.', `Ô cần điền là ${candidates[0].shape} với ${candidates[0].dots} chấm.`], { choices });
  }
  function diceCompare(r, max) {
    // Two dice cannot show a no-carry total above 9.
    const count = ri(r, 4, 5), totals = shuffle(r, range(Math.min(max, 9) - 1).map((i) => i + 2)).slice(0, count);
    const pairs = totals.map((sum) => {
      const a = ri(r, Math.max(1, sum - 6), Math.min(6, sum - 1)); return [a, sum - a];
    });
    const pairSums = pairs.map(([a, b]) => a + b), answer = pairSums.indexOf(Math.max(...pairSums));
    return shownAnswer('compare', 3, 'Cặp xúc xắc của bạn nào có tổng lớn nhất?',
      { kind: 'diceCompare', pairs }, 'choice', answer,
      ['Cộng hai mặt xúc xắc của từng bạn, rồi so các tổng.', `Cặp thứ ${answer + 1} lớn nhất, được ${totals[answer]}.`], { choices: range(count).map((i) => `Bạn ${i + 1}`) });
  }
  function pyramid4(r, max) {
    for (let trial = 0; trial < 200; trial++) {
      const bottom = range(4).map(() => ri(r, 0, 3));
      if (bottom.every((x) => x === 0)) continue;
      const full = bottom.slice(); let row = bottom;
      while (row.length > 1) {
        const upper = range(row.length - 1).map((i) => row[i] + row[i + 1]);
        if (upper.some((v, i) => !step(row[i], '+', row[i + 1], max))) break;
        full.push(...upper); row = upper;
      }
      if (full.length !== 10 || full[9] > max) continue;
      const holes = shuffle(r, [4, 5, 6, 7, 8, 9]).slice(0, ri(r, 2, 4)).sort((a,b) => a-b);
      const cells = full.map((x, i) => holes.includes(i) ? null : x), blanks = holes.map((i) => blank(i, full[i], 'p'));
      const solved = cells.slice();
      for (const [from, len] of [[0, 4], [4, 3], [7, 2]])
        for (let i = 0; i < len - 1; i++) solved[from + len + i] = solved[from + i] + solved[from + i + 1];
      blanks.forEach((b) => { b.answer = solved[b.cell]; });
      return shownAnswer('pyramid', 3, 'Mỗi ô bằng tổng hai ô ngay dưới. Điền tháp bốn tầng.',
        { kind: 'pyramid4', cells }, 'numpad', blanks.map((b) => b.answer),
        ['Bắt đầu từ hàng dưới rồi cộng hai ô liền nhau.', `Các ô trống là ${blanks.map((b) => b.answer).join(', ')}; đỉnh tháp là ${full[9]}.`], { blanks });
    }
    throw new Error('pyramid4 generation failed');
  }
  function longTrain(r, max) {
    for (let trial = 0; trial < 100; trial++) {
      const len = ri(r, 6, 7), values = [ri(r, 2, max - 2)], ops = [];
      for (let i = 0; i < len; i++) {
        const prev = values.at(-1), candidates = [];
        for (let d = -5; d <= 5; d++) if (d && step(prev, d > 0 ? '+' : '-', Math.abs(d), max)) candidates.push(d);
        const d = pick(r, candidates); ops.push(d); values.push(prev + d);
      }
      if (!ops.some((d) => d > 0) || !ops.some((d) => d < 0)) continue;
      const holes = shuffle(r, range(len).map((i) => i + 1)).slice(0, 3).sort((a,b) => a-b);
      const shown = values.map((v, i) => holes.includes(i) ? null : v), blanks = holes.map((i) => blank(i, values[i], 't'));
      const solved = [shown[0]];
      ops.forEach((d) => solved.push(solved.at(-1) + d));
      blanks.forEach((b) => { b.answer = solved[b.cell]; });
      return shownAnswer('chain', 3, 'Đi theo từng toa và điền các số còn thiếu.',
        { kind: 'chain', values: shown, ops }, 'numpad', blanks.map((b) => b.answer),
        ['Làm từng phép trên mũi tên từ trái sang phải.', `Đoàn tàu đúng là ${values.join(' → ')}.`],
        { blanks, anim: { kind: 'hops', values, ops, order: holes } });
    }
    throw new Error('long train generation failed');
  }

  function generateExtension(type, level, rng, max) {
    if (level !== 3) return null;
    const fns = { position: () => queue(rng), insideOut: () => venn(rng), condition: () => expressionCondition(rng, max),
      shapePattern: () => dotPattern(rng), grid: () => dotGrid(rng), compare: () => diceCompare(rng, max),
      pyramid: () => max === 20 ? pyramid4(rng, max) : null, chain: () => longTrain(rng, max) };
    return fns[type] ? fns[type]() : null;
  }

  const GENS = { latin, magic, signs, triCenter, sumSplit, addTable, numberLine, ring, match };
  function generate(type, level, rng, max) {
    if (!GENS[type]) throw new Error('unknown phase2b type ' + type);
    return GENS[type](rng, level, max);
  }
  const api = { generate, generateExtension, _internal: { step } };
  if (typeof module !== 'undefined' && module.exports) module.exports = api;
  else root.TuDuyPhase2B = api;
})(typeof window !== 'undefined' ? window : this);
