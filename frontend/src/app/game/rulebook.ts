/** The rulebook on GitHub: one file per section (docs/rules/README.md). Rule IDs are stable, so is this map. */
export const RULEBOOK_URL = 'https://github.com/Daliush/shardbound/blob/main/docs/rules/';

const SECTIONS: Record<string, { file: string; title: string }> = {
  '1': { file: '01-the-game.md', title: 'The game' },
  '2': { file: '02-deck-building.md', title: 'Deck building' },
  '3': { file: '03-zones.md', title: 'Zones' },
  '4': { file: '04-shards.md', title: 'Shards' },
  '5': { file: '05-game-sequence.md', title: 'Game sequence' },
  '6': { file: '06-cards.md', title: 'Cards' },
  '7': { file: '07-combat.md', title: 'Combat' },
  '8': { file: '08-effects.md', title: 'Effects' },
  '9': { file: '09-triggers.md', title: 'Triggers' },
  '10': { file: '10-targets.md', title: 'Targets' },
  '11.1': { file: '11-1-echo.md', title: 'Echo' },
  '11.2': { file: '11-2-fracture.md', title: 'Fracture' },
  '11.3': { file: '11-3-anchor.md', title: 'Anchor' },
  '11.4': { file: '11-4-overcharge.md', title: 'Overcharge' },
  '11.5': { file: '11-5-link.md', title: 'Link' },
};

export interface RuleLink { id: string; href: string; section: string; }

/** Where rule "11.3.2" or "7.5" is written: its section's file, or the rulebook's contents for an unknown ID. */
export function ruleLink(id: string): RuleLink {
  const [major, minor] = id.split('.');
  const section = SECTIONS[major === '11' ? `${major}.${minor}` : major];
  return section
    ? { id, href: RULEBOOK_URL + section.file, section: section.title }
    : { id, href: RULEBOOK_URL + 'README.md', section: 'Rulebook' };
}
