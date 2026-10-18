import { RULEBOOK_URL, ruleLink } from './rulebook';

describe('ruleLink', () => {
  it('points a rule at its section of the rulebook', () => {
    expect(ruleLink('7.5')).toEqual({ id: '7.5', href: RULEBOOK_URL + '07-combat.md', section: 'Combat' });
    expect(ruleLink('5.1.2').href).toBe(RULEBOOK_URL + '05-game-sequence.md');
    expect(ruleLink('10.3').section).toBe('Targets');
  });
  it('tells the keyword files apart', () => {
    expect(ruleLink('11.3.2')).toMatchObject({ href: RULEBOOK_URL + '11-3-anchor.md', section: 'Anchor' });
    expect(ruleLink('11.5.2').section).toBe('Link');
  });
  it('falls back on the contents for an unknown section', () => {
    expect(ruleLink('42.1')).toMatchObject({ href: RULEBOOK_URL + 'README.md', section: 'Rulebook' });
  });
});
