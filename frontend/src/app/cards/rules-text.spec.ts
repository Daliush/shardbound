import { ruleTokens } from './rules-text';

describe('card rules formatting', () => {
  it('keeps the engine wording intact and distinguishes costs, sacrifice, damage and effects', () => {
    const text = 'Sacrifice 2. Kindle (3 Shards): Deal 7 damage. Echo 50. Give +2/+0. Draw 1 card.';
    const tokens = ruleTokens(text);
    expect(tokens.map(token => token.text).join('')).toBe(text);
    expect(tokens.filter(token => token.icon).map(token => token.kind)).toEqual(['sacrifice', 'cost', 'damage', 'effect', 'buff', 'effect']);
  });
  it('does not consume text around unknown effects or markup', () => {
    const text = 'Until end of turn: <unknown> & 5/8; restore 3 HP.';
    expect(ruleTokens(text).map(token => token.text).join('')).toBe(text);
  });
});
