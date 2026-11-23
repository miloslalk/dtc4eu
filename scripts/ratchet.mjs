#!/usr/bin/env node
// ratchet.mjs — compares today's measured values against the "Measured, not yet
// enforced" table in CONSTRAINTS.md (the single source of the recorded numbers).
// Usage: node scripts/ratchet.mjs <backend|frontend|website>
// Reads artifacts produced earlier in the same check run (jacoco.xml, dist/).
// Exit 0: no metric regressed beyond tolerance; 1: a regression; 2: could not measure.
import { execFileSync } from 'node:child_process';
import { existsSync, readFileSync } from 'node:fs';

const TOLERANCE = 0.5; // percentage points for coverage, percent growth for bundle bytes
const scope = process.argv[2];
const bail = (msg) => { console.error('ratchet: ' + msg); process.exit(2); };
if (!['backend', 'frontend', 'website'].includes(scope)) bail('usage: ratchet.mjs <backend|frontend|website>');

const top = execFileSync('git', ['rev-parse', '--show-toplevel'], { encoding: 'utf8' }).trim();
process.chdir(top);

// | Metric | Today | Direction | Source |
const recorded = new Map();
for (const line of readFileSync('CONSTRAINTS.md', 'utf8').split('\n')) {
  const cells = line.split('|').map((c) => c.trim());
  if (cells.length < 5) continue;
  const value = Number(cells[2].replace(/[%,\s]|bytes/g, ''));
  if (!Number.isNaN(value) && /must not (fall|grow)/.test(cells[3])) {
    recorded.set(cells[1], { value, direction: cells[3].includes('fall') ? 'min' : 'max' });
  }
}

const coverage = (type) => {
  const xml = 'backend/target/site/jacoco/jacoco.xml';
  if (!existsSync(xml)) bail(`${xml} missing — run the backend suite first`);
  // report-level counters are the last ones in the file
  const counters = [...readFileSync(xml, 'utf8').matchAll(/<counter type="(\w+)" missed="(\d+)" covered="(\d+)"\/>/g)];
  const c = counters.reverse().find((m) => m[1] === type);
  if (!c) bail(`no ${type} counter in ${xml}`);
  return Math.round((10000 * Number(c[3])) / (Number(c[2]) + Number(c[3]))) / 100;
};
const bundles = (dir) => {
  if (!existsSync(`${dir}/dist`)) bail(`${dir}/dist missing — build it first`);
  const out = JSON.parse(execFileSync('npx', ['size-limit', '--json'], { cwd: dir, encoding: 'utf8' }));
  return Object.fromEntries(out.map((e) => [e.name, e.size]));
};

const measured = {};
if (scope === 'backend') {
  measured['Backend line coverage'] = coverage('LINE');
  measured['Backend branch coverage'] = coverage('BRANCH');
} else {
  const sizes = bundles(scope === 'frontend' ? 'frontend' : 'website/frontend');
  for (const [name, size] of Object.entries(sizes)) measured[`Bundle: ${name}`] = size;
}

let regressions = 0;
for (const [metric, now] of Object.entries(measured)) {
  const rec = recorded.get(metric);
  if (!rec) bail(`"${metric}" has no row in the CONSTRAINTS.md Measured table`);
  const worse = rec.direction === 'min'
    ? now < rec.value - TOLERANCE
    : now > rec.value * (1 + TOLERANCE / 100);
  const better = rec.direction === 'min' ? now > rec.value : now < rec.value;
  const verdict = worse ? 'REGRESSED' : better ? 'improved — record the new value' : 'held';
  if (worse) regressions++;
  console.log(`${metric}: recorded ${rec.value}, now ${now} — ${verdict}`);
}
process.exit(regressions ? 1 : 0);
