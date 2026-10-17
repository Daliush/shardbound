import { Component, computed, input } from '@angular/core';
import { Icon } from '../core/icon';

export interface RuleToken { text: string; kind: string; icon?: string; }

/** Decorate the engine's text without rewriting rules or interpreting their legality. */
export function ruleTokens(text: string): RuleToken[] {
  const pattern = /\b\d+ Shards?\b|\bSacrifice(?: \d+)?\b|\b\d+ damage\b|[+−-]\d+\/[+−-]?\d+|\b(?:Echo(?: \d+)?|Overcharge|Fracture|Anchor|Freeze|Frozen|Link|Heal|Restore|Draw|Summon|Destroy|Recall|Discard|Arrival|Death|Attack)\b/gi;
  const result: RuleToken[] = [];
  let end = 0;
  for (const match of text.matchAll(pattern)) {
    if (match.index > end) result.push({ text: text.slice(end, match.index), kind: 'plain' });
    const value = match[0], word = value.toLowerCase();
    let kind = 'effect', icon = 'neutral';
    if (/shards?/.test(word)) { kind = 'cost'; icon = 'shard'; }
    else if (word.startsWith('sacrifice')) { kind = 'sacrifice'; icon = 'sacrifice'; }
    else if (word.includes('damage')) { kind = 'damage'; icon = 'sword'; }
    else if (/^[+−-]/.test(word)) { kind = 'buff'; icon = 'shield'; }
    else if (/^(heal|restore)/.test(word)) { kind = 'heal'; icon = 'heart'; }
    else {
      const icons: Record<string, string> = { echo: 'echo', overcharge: 'lock', fracture: 'fracture', anchor: 'anchor', freeze: 'frozen', frozen: 'frozen', link: 'link', draw: 'deck', summon: 'root', destroy: 'doom', recall: 'graveyard', discard: 'graveyard', arrival: 'arrow', death: 'graveyard', attack: 'sword' };
      icon = icons[word.split(' ')[0]] ?? 'neutral';
    }
    result.push({ text: value, kind, icon });
    end = match.index + value.length;
  }
  if (end < text.length) result.push({ text: text.slice(end), kind: 'plain' });
  return result;
}

@Component({
  selector: 'app-rules-text',
  imports: [Icon],
  template: `@for (token of tokens(); track $index) { @if (token.icon) {<strong [attr.data-rule]="token.kind"><app-icon [name]="token.icon" />{{ token.text }}</strong>} @else {{{ token.text }}} }`,
  styles: `:host { display: inline; } strong { font-weight: 600; } app-icon { width: .9em; height: .9em; margin-right: .16em; vertical-align: -.1em; }
    [data-rule='cost'] { color: var(--game-cost); } [data-rule='sacrifice'] { color: var(--game-sacrifice); } [data-rule='damage'] { color: var(--game-attack); }
    [data-rule='heal'], [data-rule='buff'] { color: var(--heal); } [data-rule='effect'] { color: var(--game-effect); }`,
})
export class RulesText {
  readonly text = input('');
  protected readonly tokens = computed(() => ruleTokens(this.text()));
}
