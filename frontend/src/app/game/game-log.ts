import { Component, ElementRef, afterRenderEffect, computed, inject, input, viewChild } from '@angular/core';
import { EventView } from '../api/protocol';
import { Icon } from '../core/icon';
import { Settings } from '../core/settings';
import { EVENT_BEATS, readCard } from '../arena/stage/choreographer';
import { Inspect } from '../cards/inspect';
import { ruleLink } from './rulebook';

interface TurnGroup { heading: string; mine: boolean | null; events: EventView[]; }

const ICONS: Record<string, string> = { draw: 'deck', attack: 'sword', damage: 'sword', heal: 'heart', shatter: 'fracture', sacrifice: 'sacrifice', freeze: 'frozen', thaw: 'frozen', anchor: 'anchor', doom: 'doom', link: 'link', unlink: 'link', share: 'link', intercept: 'shield', echo: 'echo', play: 'shard', cast: 'shard', victory: 'neutral', turn: 'hourglass', end: 'hourglass', recall: 'graveyard', discard: 'graveyard', return: 'deck' };
const TONES: Record<string, string> = { attack: 'attack', damage: 'attack', shatter: 'attack', sacrifice: 'attack', doom: 'attack', heal: 'heal', intercept: 'heal', freeze: 'frost', thaw: 'frost', play: 'play', cast: 'play', arrive: 'play', summon: 'play', victory: 'play' };

/** The game's events as the engine wrote them, grouped by turn, each rule ID linked to the rulebook. */
@Component({
  selector: 'app-game-log',
  imports: [Icon],
  template: `<ol #list role="log" aria-live="polite" aria-label="Chronicle" aria-relevant="additions">
    @for (group of groups(); track $index; let last = $last) {
      <li class="turn" [class.mine]="group.mine === true" [class.theirs]="group.mine === false">
        <h3>{{ group.heading }}</h3>
        <ol>@for (event of group.events; track $index; let newest = $last) {
          <li class="entry" [class.newest]="last && newest" [attr.data-tone]="tone(event.type)">
            <app-icon [name]="icon(event.type)" />
            <p>{{ event.text }}
              @if (card(event); as card) { <button class="quiet inspect-event" [attr.aria-label]="'Inspect ' + card.card" (click)="inspect.open({ id: card.card })"><app-icon name="eye" /></button> }
            </p>
            @if (settings.preferences().ruleIds && event.rules.length) {
              <span class="rules">@for (rule of event.rules; track rule) {
                @let link = ruleLink(rule);
                <a [href]="link.href" target="_blank" rel="noopener" [title]="'Rule ' + rule + ' · ' + link.section + ' — open the rulebook'">{{ rule }}</a>
              }</span>
            }
          </li>
        }</ol>
      </li>
    } @empty { <li class="empty">The Chronicle records every event of the game, with the rules it applies.</li> }
  </ol>`,
  styles: `
    :host { display: flex; min-height: 0; }
    ol { list-style: none; margin: 0; padding: 0; }
    :host > ol { flex: 1; overflow-y: auto; scrollbar-width: thin; padding-bottom: 12px; }
    .turn h3 { position: sticky; top: 0; z-index: 1; margin: 0; padding: 10px 14px 6px; font: 600 11px/1.2 var(--font-ui); letter-spacing: .06em; text-transform: uppercase; color: var(--muted); background: var(--chronicle-surface, var(--obsidian-900)); }
    .turn.mine h3 { color: var(--faction-light); }
    .entry { display: grid; grid-template-columns: 16px minmax(0, 1fr); column-gap: 10px; padding: 7px 14px; font-size: 12.5px; line-height: 1.45; color: color-mix(in srgb, var(--text) 82%, transparent); }
    .entry.newest { color: var(--text); background: color-mix(in srgb, var(--text) 5%, transparent); }
    .entry > app-icon { width: 14px; height: 14px; margin-top: 2px; color: var(--muted); }
    .entry[data-tone='attack'] > app-icon { color: var(--game-attack); }
    .entry[data-tone='heal'] > app-icon { color: var(--game-ready); }
    .entry[data-tone='frost'] > app-icon { color: var(--frost); }
    .entry[data-tone='play'] > app-icon { color: var(--faction); }
    p { margin: 0; max-width: none; }
    .rules { grid-column: 2; display: flex; flex-wrap: wrap; gap: 4px; margin-top: 4px; }
    .rules a { font: 500 10.5px/1 var(--font-number); padding: 3px 6px; border-radius: 4px; color: var(--muted); background: color-mix(in srgb, var(--text) 6%, transparent); text-decoration: none; transition: color 150ms ease, background 150ms ease; }
    .rules a:hover, .rules a:focus-visible { color: var(--text); background: color-mix(in srgb, var(--faction) 22%, transparent); }
    .inspect-event { padding: 0 4px; min-height: 20px; height: 20px; vertical-align: -4px; color: var(--muted); }
    .inspect-event app-icon { width: 14px; height: 14px; }
    .empty { padding: 16px 14px; font-size: 12.5px; color: var(--muted); }
  `,
})
export class GameLog {
  readonly events = input<EventView[]>([]);
  protected readonly settings = inject(Settings);
  protected readonly inspect = inject(Inspect);
  protected readonly ruleLink = ruleLink;
  private readonly list = viewChild.required<ElementRef<HTMLElement>>('list');
  protected readonly groups = computed(() => {
    const groups: TurnGroup[] = [];
    for (const event of this.events()) {
      if (event.type === 'turn_started' || !groups.length) {
        const player = event['player'];
        groups.push({ heading: event.type === 'turn_started' ? event.text.replace(/\.$/, '') : 'Setup', mine: player === 'you' ? true : player === 'opponent' ? false : null, events: [] });
      }
      groups.at(-1)!.events.push(event);
    }
    return groups;
  });
  protected card(event: EventView) { return readCard(event['card'] ?? event['unit'] ?? event['source'] ?? event['attacker']); }
  protected icon(type: string): string { return ICONS[EVENT_BEATS[type]] ?? 'shard'; }
  protected tone(type: string): string { return TONES[EVENT_BEATS[type]] ?? 'plain'; }
  constructor() { afterRenderEffect(() => { this.events(); const list = this.list().nativeElement; list.scrollTop = list.scrollHeight; }); }
}
