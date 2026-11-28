import { test } from 'node:test';
import assert from 'node:assert/strict';
import { isNotApplicable, setRowField } from './profileDerivation.ts';

const fields = [
  { key: 'role' },
  { key: 'disciplinary_area', derivedFrom: { field: 'role', map: {
    'PL: Psychologist': 'PSI: Psychological-Psychotherapeutic',
    'AS: Social Worker': 'SAC: Socio-Anthropological-Cultural',
  }, none: ['V: Volunteers'] } },
  { key: 'age' },
];

test('a role listed under none clears the area and makes it not applicable', () => {
  const row = { role: 'PL: Psychologist', disciplinary_area: 'PSI: Psychological-Psychotherapeutic' };
  const next = setRowField(fields, row, 'role', 'V: Volunteers');
  assert.equal('disciplinary_area' in next, false);
  assert.equal(isNotApplicable(fields[1], next), true);
  assert.equal(isNotApplicable(fields[1], row), false);
});

test('choosing a role assigns its disciplinary area', () => {
  assert.deepEqual(setRowField(fields, { age: 40 }, 'role', 'PL: Psychologist'),
    { age: 40, role: 'PL: Psychologist', disciplinary_area: 'PSI: Psychological-Psychotherapeutic' });
});

test('changing the role replaces the area', () => {
  const row = { role: 'PL: Psychologist', disciplinary_area: 'PSI: Psychological-Psychotherapeutic' };
  assert.equal(setRowField(fields, row, 'role', 'AS: Social Worker').disciplinary_area,
    'SAC: Socio-Anthropological-Cultural');
});

test('a role without a single area leaves it to be chosen by hand', () => {
  const row = { role: 'PL: Psychologist', disciplinary_area: 'PSI: Psychological-Psychotherapeutic' };
  const next = setRowField(fields, row, 'role', 'DC: Delegate Coordinator (operator delegated by the Staff)');
  assert.equal('disciplinary_area' in next, false);
  assert.equal('disciplinary_area' in setRowField(fields, row, 'role', undefined), false);
});

test('the area stays editable and other fields do not touch it', () => {
  const row = { role: 'PL: Psychologist', disciplinary_area: 'PSI: Psychological-Psychotherapeutic' };
  assert.equal(setRowField(fields, row, 'disciplinary_area', 'A: Other').disciplinary_area, 'A: Other');
  assert.equal(setRowField(fields, row, 'age', 50).disciplinary_area, 'PSI: Psychological-Psychotherapeutic');
});
