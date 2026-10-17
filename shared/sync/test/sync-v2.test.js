'use strict';
const test = require('node:test');
const assert = require('node:assert/strict');
const path = require('node:path');
const S = require('../sync-v2.js');
const fx = (n) => require(path.join(__dirname, '..', 'fixtures', n));

test('migrateV1 applies the same coercions and drops the same rows as the v1 Kotlin parser', () => {
  const { docs, settings } = S.migrateV1(fx('v1-snapshot.json'), { nowMillis: 5, deviceId: 'web-x' });
  const exp = fx('v1-expected.json');
  const by = (c) => Object.fromEntries(docs.filter((d) => d.collection === c).map((d) => [d.id, d.data]));
  assert.deepEqual(Object.keys(by('profiles')).sort(), exp.profiles);
  for (const [id, band] of Object.entries(exp.profileAgeBands)) assert.equal(by('profiles')[id].ageBand, band);
  for (const c of ['tasks', 'routines', 'completions', 'sessions']) {
    assert.deepEqual(Object.keys(by(c)).sort(), Object.keys(exp[c]).sort(), c);
    for (const [id, fields] of Object.entries(exp[c])) for (const [k, v] of Object.entries(fields)) assert.deepEqual(by(c)[id][k], v, `${c}/${id}.${k}`);
  }
  assert.equal(docs.length, exp.docCount);
  assert.equal(settings.dailyGoalMinutes, exp.settings.dailyGoalMinutes);
  assert.equal(settings.learningAgeBand, exp.settings.learningAgeBand);
  assert.ok(settings.scheduleAnchors && 'default' in settings.schedulePlans);
  for (const d of docs) {
    assert.equal(d.data.schemaVersion, 2); assert.equal(d.data.deleted, false); assert.equal(d.data.updatedAtMillis, 1792000000000);
    assert.deepEqual(S.fromDoc(d.collection, d.id, d.data).entity, Object.fromEntries(Object.entries(d.data).filter(([k]) => !['updatedAtMillis', 'deviceId', 'deleted', 'schemaVersion'].includes(k))));
  }
});

test('conflict fixtures: every client picks the same winner, independent of argument order', () => {
  for (const c of fx('conflicts.json')) {
    const mk = (x) => x && { updatedAtMillis: x.updatedAtMillis, deviceId: x.deviceId, deleted: !!x.deleted,
      entity: { status: x.status || 'ON_TIME', completedAtMillis: x.completedAtMillis || 0 } };
    const l = mk(c.local), r = mk(c.remote);
    assert.equal(S.pickWinner(c.collection, l, r), c.winner, c.name);
    if (l && r) assert.equal(S.pickWinner(c.collection, r, l), c.winner === 'local' ? 'remote' : 'local', c.name + ' (swapped)');
  }
});

test('fromDoc rejects wrong schema, mismatched doc id and invalid entities', () => {
  const d = S.toDoc('tasks', { id: 7, name: 'Vẽ', hour: 8, minute: 0, daysOfWeek: '2' }, { nowMillis: 1, deviceId: 'w' });
  assert.ok(S.fromDoc('tasks', '7', d.data));
  assert.equal(S.fromDoc('tasks', '8', d.data), null);
  assert.equal(S.fromDoc('tasks', '7', { ...d.data, schemaVersion: 1 }), null);
  assert.equal(S.toDoc('tasks', { id: 7, name: '  ' }, { nowMillis: 1, deviceId: 'w' }), null);
  assert.equal(S.toDoc('profiles', { id: 'a/b', name: 'x' }, { nowMillis: 1, deviceId: 'w' }), null);
});

test('newId is a positive safe integer and does not repeat in 100k draws', () => {
  const seen = new Set();
  for (let i = 0; i < 100000; i++) { const id = S.newId(); assert.ok(Number.isSafeInteger(id) && id > 0); seen.add(id); }
  assert.equal(seen.size, 100000);
});
