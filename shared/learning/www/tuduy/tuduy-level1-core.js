/* Luyện tư duy — picture-first "Làm quen" puzzles for ages 4–5. Pure and seeded. */
(function (root) {
  'use strict';
  const TYPES = ['shapes', 'compare', 'order', 'neighbor', 'sequence', 'shapePattern', 'oddOne',
    'position', 'insideOut', 'match', 'chain', 'balance', 'latin'];
  const ri = (rng, lo, hi) => lo + Math.floor(rng() * (hi - lo + 1));
  const pick = (rng, values) => values[ri(rng, 0, values.length - 1)];
  function shuffle(rng, values) {
    const out = values.slice();
    for (let i = out.length - 1; i > 0; i--) {
      const j = ri(rng, 0, i); [out[i], out[j]] = [out[j], out[i]];
    }
    return out;
  }
  const icons = ['⭐', '🍎', '🐤', '🌸', '🚗'];
  function numeric(type, prompt, visual, answer, hint, reason) {
    return { type, level: 1, prompt, visual, input: 'numpad',
      blanks: [{ id: 'n', answer }], explain: [hint, reason], anim: { kind: 'level1' } };
  }
  function choice(type, prompt, visual, labels, correct, hint, reason, rng) {
    const choices = shuffle(rng, labels);
    if (new Set(choices).size !== choices.length || choices.length > 4)
      throw new Error('level 1 choices must be distinct and at most four');
    return { type, level: 1, prompt, visual, input: 'choice', choices,
      answer: choices.indexOf(correct), blanks: [], explain: [hint, reason], anim: { kind: 'level1' } };
  }
  function pictureChoice(type, prompt, visual, answer, hint, reason) {
    return { type, level: 1, prompt, visual, input: 'choice', choices: [], answer,
      blanks: [], explain: [hint, reason], anim: { kind: 'level1' } };
  }
  function pattern(rng, type) {
    const symbols = shuffle(rng, ['🔵', '🟠', '🔺', '🟣']).slice(0, 2);
    const rule = pick(rng, type === 'sequence' ? ['AB', 'ABB'] : ['AB', 'AAB', 'ABB']);
    const repeats = rule.split('').map((letter) => symbols[letter === 'A' ? 0 : 1]);
    const shownLength = ri(rng, 4, 6);
    const shown = Array.from({ length: shownLength }, (_, i) => repeats[i % repeats.length]);
    const expected = repeats[shownLength % repeats.length];
    return choice(type, 'Hình nào tiếp theo?', { kind: 'l1Pattern', shown, rule }, symbols,
      expected, 'Nhìn xem các hình lặp lại thế nào.', `Hình tiếp theo là ${expected}.`, rng);
  }
  const generators = {
    shapes(rng) {
      const count = ri(rng, 2, 5), icon = pick(rng, icons);
      return numeric('shapes', 'Có mấy hình?', { kind: 'l1Shapes', count, icon }, count,
        'Chỉ đếm từng hình một.', `Có ${count} hình.`);
    },
    compare(rng) {
      const left = ri(rng, 1, 10), right = ri(rng, 1, 10), icon = pick(rng, icons);
      const answer = left === right ? 'Bằng nhau' : left > right ? 'Nhiều hơn' : 'Ít hơn';
      return choice('compare', 'Bên trái nhiều, ít hay bằng bên phải?', { kind: 'l1Compare', left, right, icon },
        ['Nhiều hơn', 'Ít hơn', 'Bằng nhau'], answer, 'Đếm từng bên rồi so sánh.',
        `${left} và ${right}: ${answer.toLowerCase()}.`, rng);
    },
    order(rng) {
      const start = ri(rng, 0, 8), nums = [start, start + 1, start + 2];
      const extras = shuffle(rng, Array.from({ length: 11 }, (_, i) => i).filter((n) => !nums.includes(n))).slice(0, 2);
      return choice('order', 'Điền số để xếp từ bé đến lớn.',
        { kind: 'l1Order', values: [nums[0], null, nums[2]], dots: nums[1] },
        [nums[1], ...extras].map(String), String(nums[1]), 'Số ở giữa lớn hơn bên trái.',
        `${nums[0]} < ${nums[1]} < ${nums[2]}.`, rng);
    },
    neighbor(rng) {
      const direction = pick(rng, ['after', 'before']);
      const number = direction === 'after' ? ri(rng, 0, 9) : ri(rng, 1, 10);
      const answer = number + (direction === 'after' ? 1 : -1);
      return numeric('neighbor', `Số nào đứng ngay ${direction === 'after' ? 'sau' : 'trước'} ${number}?`,
        { kind: 'l1Neighbor', number, direction }, answer,
        'Nhìn các chấm và đếm thêm hoặc bớt một.', `${direction === 'after' ? 'Sau' : 'Trước'} ${number} là ${answer}.`);
    },
    sequence(rng) {
      // The first xorshift draw clusters for nearby seeds; use the next draw for the 70/30 split.
      rng();
      if (rng() >= .7) return pattern(rng, 'sequence');
      const start = ri(rng, 0, 7), answer = start + 3;
      return numeric('sequence', 'Số nào tiếp theo?',
        { kind: 'l1Sequence', values: [start, start + 1, start + 2, null] }, answer,
        'Mỗi lần đếm thêm một.', `${start + 2} rồi đến ${answer}.`);
    },
    shapePattern(rng) { return pattern(rng, 'shapePattern'); },
    oddOne(rng) {
      const one = ri(rng, 0, 3), mode = pick(rng, ['color', 'shape', 'kind']);
      const kinds = pick(rng, [['🍎', '🐱'], ['🐱', '🚗']]);
      const pictures = Array.from({ length: 4 }, (_, i) => mode === 'color'
        ? i === one ? '🟠' : '🔵' : mode === 'shape' ? i === one ? '🔷' : '🔵'
          : i === one ? kinds[1] : kinds[0]);
      return pictureChoice('oddOne', 'Hình nào khác các hình còn lại?', { kind: 'l1Odd', pictures },
        one, 'Tìm một hình khác màu, dáng hoặc loại.', `Hình ${one + 1} khác các hình kia.`);
    },
    position(rng) {
      const pictures = shuffle(rng, ['🐱', '🐶', '🐰']);
      const direction = pick(rng, ['bên trái', 'bên phải', 'giữa', 'trên cùng', 'dưới cùng']);
      const index = direction === 'bên phải' || direction === 'dưới cùng' ? 2 : direction === 'giữa' ? 1 : 0;
      return pictureChoice('position', `Con nào ở ${direction}?`,
        { kind: 'l1Position', pictures, vertical: direction === 'trên cùng' || direction === 'dưới cùng' },
        index, 'Nhìn vị trí của từng con.', `${pictures[index]} ở ${direction}.`);
    },
    insideOut(rng) {
      const inside = ri(rng, 1, 5), outside = ri(rng, 1, 3), icon = pick(rng, icons);
      return numeric('insideOut', 'Có mấy hình ở trong vòng tròn?',
        { kind: 'l1Inside', inside, outside, icon }, inside,
        'Chỉ đếm hình nằm trong vòng tròn.', `Trong vòng tròn có ${inside} hình.`);
    },
    match(rng) {
      const nums = shuffle(rng, [1, 2, 3, 4, 5]).slice(0, 3);
      const hidden = ri(rng, 0, 2);
      return numeric('match', 'Nhóm chấm cạnh ô trống có mấy chấm?',
        { kind: 'l1Match', pairs: nums.map((count, i) => ({ count, number: i === hidden ? null : count })) },
        nums[hidden], 'Đếm chấm ở hàng chưa có số.', `${nums[hidden]} chấm nối với số ${nums[hidden]}.`);
    },
    chain(rng) {
      const first = ri(rng, 0, 1), step1 = ri(rng, 1, 2), step2 = ri(rng, 1, 2);
      const answer = first + step1 + step2;
      return numeric('chain', 'Toa cuối có số mấy?',
        { kind: 'l1Chain', values: [first, first + step1, null], steps: [step1, step2] },
        answer, 'Mỗi mũi tên cho biết thêm mấy.', `${first} + ${step1} + ${step2} = ${answer}.`);
    },
    balance(rng) {
      const a = ri(rng, 1, 4), b = ri(rng, 1, 5 - a);
      return numeric('balance', 'Có tất cả mấy hình?', { kind: 'l1Balance', a, b, icon: pick(rng, icons) },
        a + b, 'Đếm cả hai nhóm hình.', `${a} + ${b} = ${a + b}.`);
    },
    latin(rng) {
      const symbols = shuffle(rng, ['🔵', '🟠', '🔺']);
      const cells = Array.from({ length: 9 }, (_, i) => symbols[(Math.floor(i / 3) + i % 3) % 3]);
      const hidden = ri(rng, 0, 8), answer = cells[hidden]; cells[hidden] = null;
      return choice('latin', 'Hình nào còn thiếu trong hàng này?',
        { kind: 'l1Latin', cells, n: 3, hidden }, symbols,
        answer, 'Mỗi hàng có ba hình khác nhau.', `Ô trống là ${answer}.`, rng);
    },
  };
  function generate(type, level, rng, opts) {
    if (level !== 1 || !TYPES.includes(type)) throw new Error('level 1 type is not supported: ' + type);
    if (opts && opts.max != null && opts.max !== 10) throw new Error('level 1 max must be 10');
    const item = generators[type](rng || Math.random);
    if (item.blanks.length > 1) throw new Error('level 1 has too many blanks');
    return item;
  }
  const api = { TYPES, generate };
  if (typeof module !== 'undefined' && module.exports) module.exports = api;
  else root.TuDuyLevel1 = api;
})(typeof window !== 'undefined' ? window : this);
