'use strict';
const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');

const root = path.join(__dirname, '../www');

test('Back inside a puzzle returns to the Tu Duy category chooser', () => {
  const elements = {
    tdQuiz: { hidden: false },
    tdDone: { hidden: true },
    tdChooser: { hidden: true },
    tdPick: { innerHTML: '', appendChild() {} },
  };
  const window = { TuDuy: { TYPES: [], PHASE2_TYPES: [], TYPE_INFO: {} } };
  const document = {
    getElementById: (id) => elements[id],
    createElement: () => ({ className: '', innerHTML: '', onclick: null }),
  };
  const localStorage = { getItem: () => null, setItem() {} };
  vm.runInNewContext(fs.readFileSync(path.join(root, 'tuduy/tuduy-ui.js'), 'utf8'),
    { window, document, localStorage, console, clearTimeout });
  assert.equal(window.TuDuyUI.backToChooser(), true);
  assert.equal(elements.tdChooser.hidden, false);
  assert.equal(elements.tdQuiz.hidden, true);
  assert.equal(window.TuDuyUI.backToChooser(), false);
  elements.tdDone.hidden = false;
  assert.equal(window.TuDuyUI.backToChooser(), true);
  assert.equal(elements.tdDone.hidden, true);
});

test('host Back stays in Tu Duy until the chooser is visible', () => {
  const html = fs.readFileSync(path.join(root, 'index.html'), 'utf8');
  const body = /\n  handleBack\(\)\{([\s\S]*?)\n  \}/.exec(html)?.[1];
  assert(body);
  const handleBack = new Function('currentGameId', 'window', 'TuDuyUI', 'go', body);
  const visited = [];
  const go = (id) => visited.push(id);
  const ui = { backToChooser: () => true };
  assert.equal(handleBack('tuduy', { TuDuyUI: ui }, ui, go), 'tuduy');
  assert.deepEqual(visited, []);
  ui.backToChooser = () => false;
  assert.equal(handleBack('tuduy', { TuDuyUI: ui }, ui, go), 'home');
  assert.deepEqual(visited, ['home']);
});
