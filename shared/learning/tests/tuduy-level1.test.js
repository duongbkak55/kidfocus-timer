'use strict';
const test = require('node:test');
const assert = require('node:assert/strict');
const T = require('../www/tuduy/tuduy-core');

const N = 2000;
const expectedTypes = ['shapes', 'compare', 'order', 'neighbor', 'sequence', 'shapePattern',
  'oddOne', 'position', 'insideOut', 'match', 'chain', 'balance', 'latin'];
const allNumbers = (value) => {
  if (typeof value === 'number') return [value];
  if (Array.isArray(value)) return value.flatMap(allNumbers);
  if (!value || typeof value !== 'object') return [];
  return Object.values(value).flatMap(allNumbers);
};
function solve(item) {
  const v = item.visual;
  switch (item.type) {
    case 'shapes': return v.count;
    case 'compare': return v.left === v.right ? 'Bằng nhau' : v.left > v.right ? 'Nhiều hơn' : 'Ít hơn';
    case 'order': {
      const options = item.choices.filter((n) => v.values[0] < Number(n) && Number(n) < v.values[2]);
      assert.equal(options.length, 1); return options[0];
    }
    case 'neighbor': return v.number + (v.direction === 'after' ? 1 : -1);
    case 'sequence':
      if (v.kind === 'l1Sequence') return v.values[2] + 1;
      return patternAnswer(v.shown);
    case 'shapePattern': return patternAnswer(v.shown);
    case 'oddOne': {
      const counts = new Map(v.pictures.map((picture) => [picture, v.pictures.filter((x) => x === picture).length]));
      assert.deepEqual([...counts.values()].sort(), [1, 3]);
      return v.pictures.findIndex((picture) => counts.get(picture) === 1);
    }
    case 'position': {
      const direction = item.prompt.match(/(?:trái|phải|giữa|trên|dưới)/)[0];
      return direction === 'phải' || direction === 'dưới' ? 2 : direction === 'giữa' ? 1 : 0;
    }
    case 'insideOut': return v.inside;
    case 'match': return v.pairs.find((pair) => pair.number == null).count;
    case 'chain': return v.values[0] + v.steps[0] + v.steps[1];
    case 'balance': return v.a + v.b;
    case 'latin': {
      const row = Math.floor(v.hidden / 3), col = v.hidden % 3;
      const inRow = v.cells.slice(row * 3, row * 3 + 3).filter(Boolean);
      const inCol = [v.cells[col], v.cells[col + 3], v.cells[col + 6]].filter(Boolean);
      const options = item.choices.filter((symbol) => !inRow.includes(symbol) && !inCol.includes(symbol));
      assert.equal(options.length, 1); return options[0];
    }
    default: throw new Error(item.type);
  }
}
function patternAnswer(shown) {
  const candidates = [[0, 1], [0, 0, 1], [0, 1, 1]];
  const symbols = [shown[0], shown.find((symbol) => symbol !== shown[0])];
  const answers = candidates.filter((pattern) => shown.every((symbol, i) => symbol === symbols[pattern[i % pattern.length]]))
    .map((pattern) => symbols[pattern[shown.length % pattern.length]]);
  assert.equal(new Set(answers).size, 1, `pattern ambiguous: ${shown}`);
  return answers[0];
}

test('level 1 uses only the thirteen intended types', () => {
  assert.deepEqual(T.LEVEL1_TYPES, expectedTypes);
  for (const type of T.TYPES.concat(T.PHASE2_TYPES, T.PHASE2B_TYPES).filter((x) => !expectedTypes.includes(x)))
    assert.throws(() => T.generate(type, 1, T.mkRng(1), { max: 10 }), /not supported/);
  assert.throws(() => T.generate('shapes', 1, T.mkRng(1), { max: 20 }), /max must be 10/);
});

for (const type of expectedTypes) test(`${type} level 1: ${N} independent seeded checks`, () => {
  for (let seed = 1; seed <= N; seed++) {
    const item = T.generate(type, 1, T.mkRng(seed), { max: 10 });
    assert.deepEqual(item, T.generate(type, 1, T.mkRng(seed), { max: 10 }), `seed ${seed} differs`);
    assert.equal(item.type, type);
    assert.equal(item.level, 1);
    assert(item.blanks.length <= 1);
    assert(item.prompt.trim().split(/\s+/).length <= 12, `${type} #${seed}: long prompt`);
    assert(item.explain.length >= 2);
    assert.doesNotMatch([item.prompt, ...item.explain].join(' '), /(^|[^\p{L}])(thi|kiểm tra|số liền trước|lớp 1)(?=$|[^\p{L}])/iu);
    for (const n of allNumbers(item.visual).concat(allNumbers(item.blanks)))
      assert(Number.isInteger(n) && n >= 0 && n <= 10, `${type} #${seed}: ${n} outside 0–10`);
    if (type === 'balance') assert(item.visual.a <= 5 && item.visual.b <= 5 && item.visual.a + item.visual.b <= 5);
    if (type === 'chain') assert(item.visual.steps.every((step) => step === 1 || step === 2) &&
      item.visual.values[0] + item.visual.steps[0] + item.visual.steps[1] <= 5);
    if (type === 'shapes') assert(item.visual.count >= 2 && item.visual.count <= 5);
    if (type === 'insideOut') assert(item.visual.inside <= 5);
    if (type === 'latin') assert.equal(item.visual.cells.filter((x) => x == null).length, 1);
    const correct = solve(item);
    if (item.input === 'choice') {
      assert(item.choices.length <= 4 && new Set(item.choices).size === item.choices.length);
      assert.equal(item.choices.length ? item.choices[item.answer] : item.answer, correct, `${type} #${seed}: wrong choice`);
    } else {
      assert.equal(item.input, 'numpad');
      assert.equal(item.blanks.length, 1);
      assert.equal(item.blanks[0].answer, correct, `${type} #${seed}: wrong number`);
    }
  }
});

test('picture patterns continue with either symbol across 2,000 seeds', () => {
  const counts = { first: 0, second: 0 };
  for (let seed = 1; seed <= N; seed++) {
    const item = T.generate('shapePattern', 1, T.mkRng(seed), { max: 10 });
    assert(item.visual.shown.length >= 4 && item.visual.shown.length <= 6);
    const answer = item.choices[item.answer];
    counts[answer === item.visual.shown[0] ? 'first' : 'second']++;
  }
  assert(counts.first >= N * .35 && counts.second >= N * .35, JSON.stringify(counts));
});

test('position uses natural directions in prompt and explanation', () => {
  const directions = new Set();
  for (let seed = 1; seed <= N; seed++) {
    const item = T.generate('position', 1, T.mkRng(seed), { max: 10 });
    const direction = item.prompt.replace('Con nào ở ', '').replace('?', '');
    assert(['bên trái', 'bên phải', 'giữa', 'trên cùng', 'dưới cùng'].includes(direction));
    assert(item.explain[1].endsWith(`ở ${direction}.`));
    assert.equal(item.visual.vertical, direction === 'trên cùng' || direction === 'dưới cùng');
    directions.add(direction);
  }
  assert.equal(directions.size, 5);
});

test('sequence favors counting and odd-one includes a different kind', () => {
  let counting = 0, picturePattern = 0, kind = 0;
  for (let seed = 1; seed <= N; seed++) {
    const sequence = T.generate('sequence', 1, T.mkRng(seed), { max: 10 });
    if (sequence.visual.kind === 'l1Sequence') counting++;
    else picturePattern++;
    const odd = T.generate('oddOne', 1, T.mkRng(seed), { max: 10 });
    if (odd.visual.pictures.some((picture) => ['🍎', '🐱', '🚗'].includes(picture))) kind++;
  }
  assert(counting >= N * .65 && counting <= N * .75, `${counting} counting questions`);
  assert(picturePattern > 0 && kind > 0, `${picturePattern} picture patterns, ${kind} kinds`);
});
