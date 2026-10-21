/** The three factions as the site presents them: their style, their promise and the card that shows them best. */
export const FACTIONS = [
  { id: 'ember', name: 'Ember', style: 'Fire & sacrifice', description: 'Burn bright. Strike hard. Turn sacrifice into power.', card: 'ember.ash-warden', cardName: 'Ash Warden' },
  { id: 'tide', name: 'Tide', style: 'Flow & control', description: 'Turn the current. Strengthen your allies. Unravel their plans.', card: 'tide.deepcurrent-behemoth', cardName: 'Deepcurrent Behemoth' },
  { id: 'root', name: 'Root', style: 'Growth & resilience', description: 'Take root. Summon your allies. Grow stronger together.', card: 'root.root-sentinel', cardName: 'Root Sentinel' },
] as const;
