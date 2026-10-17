import { readFileSync } from 'node:fs';
import { test } from 'node:test';
import assert from 'node:assert/strict';

test('every server event wire name has a choreography beat', () => {
  const engine = readFileSync('../engine/core/src/main/java/fr/daliush/shardbound/core/event/GameEvent.java', 'utf8');
  const client = readFileSync('src/app/arena/stage/choreographer.ts', 'utf8').split('export interface Beat')[0];
  const expected = [...engine.matchAll(/record (\w+)\([\s\S]*?\)\s*implements GameEvent/g)].map(match => match[1].replace(/([a-z0-9])([A-Z])/g, '$1_$2').toLowerCase()).sort();
  const actual = [...client.matchAll(/^  (\w+): '/gm)].map(match => match[1]).sort();
  assert.deepEqual(actual, expected, 'Update EVENT_BEATS when the engine introduces an event.');
});
