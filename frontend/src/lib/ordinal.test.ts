import { test } from 'node:test';
import assert from 'node:assert/strict';
import { facilityCycleLabel, ordinal, shortCycleCode } from './ordinal.ts';

test('ordinal suffixes, including the 11–13 exceptions', () => {
  const cases: [number, string][] = [[1, '1st'], [2, '2nd'], [3, '3rd'], [4, '4th'], [11, '11th'], [12, '12th'],
    [13, '13th'], [21, '21st'], [22, '22nd'], [101, '101st'], [111, '111th'], [112, '112th']];
  for (const [n, expected] of cases) assert.equal(ordinal(n), expected);
});

test('invalid ordinals render nothing (the old "-1undefined" bug)', () => {
  for (const n of [0, -1, 1.5, null, undefined, Number.NaN]) assert.equal(ordinal(n), null);
});

test('cycle labels', () => {
  assert.equal(shortCycleCode('2025-2026'), '2025-26');
  assert.equal(shortCycleCode('PILOT'), 'PILOT');
  assert.equal(facilityCycleLabel('2025-2026', 1), '1st 2025-26');
  assert.equal(facilityCycleLabel('2025-2026', -1), '2025-26');
  assert.equal(facilityCycleLabel('2025-2026', null), '2025-26');
});
