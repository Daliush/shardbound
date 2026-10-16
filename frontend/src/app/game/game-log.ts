import { Component, ElementRef, afterRenderEffect, inject, input, viewChild } from '@angular/core';
import { EventView } from '../api/protocol';
import { Icon } from '../core/icon';
import { Settings } from '../core/settings';
import { EVENT_BEATS, readCard } from '../arena/stage/choreographer';
import { Inspect } from '../cards/inspect';

@Component({
  selector: 'app-game-log',
  imports: [Icon],
  template: `<ol #list role="log" aria-live="polite" aria-label="Chronicle" aria-relevant="additions">@for (event of events(); track $index) {
    <li><app-icon [name]="icon(event.type)" /><div>@if (settings.preferences().ruleIds && event.rules.length) { <span class="rules">[{{ event.rules.join(', ') }}] </span> }{{ event.text }}
      @if (card(event); as card) { <button class="quiet inspect-event" [attr.aria-label]="'Inspect ' + card.card" (click)="inspect.open({ id: card.card })"><app-icon name="eye" /></button> }
    </div></li>
  }</ol>`,
  styles: `:host { display: flex; min-height: 0; } ol { flex: 1; overflow-y: auto; margin: 0; padding: 0; list-style: none; font-size: .72rem; line-height: 1.65; } li { display: flex; align-items: flex-start; gap: .6rem; padding: .65rem .5rem .65rem 0; border-bottom: 1px solid var(--line); } li > app-icon { color: var(--muted); margin-top: .3rem; flex-shrink: 0; } .rules { color: var(--muted); font: .65rem var(--font-number); } .inspect-event { padding: 0 .25rem; min-height: 24px; height: 24px; vertical-align: middle; }`,
})
export class GameLog {
  readonly events = input<EventView[]>([]);
  protected readonly settings = inject(Settings);
  protected readonly inspect = inject(Inspect);
  private readonly list = viewChild.required<ElementRef<HTMLElement>>('list');
  protected card(event: EventView) { return readCard(event['card'] ?? event['unit'] ?? event['source'] ?? event['attacker']); }
  protected icon(type: string): string {
    const kind = EVENT_BEATS[type];
    return ({ draw: 'deck', attack: 'sword', damage: 'sword', heal: 'heart', shatter: 'fracture', freeze: 'frozen', anchor: 'anchor', doom: 'doom', link: 'link', intercept: 'shield', echo: 'echo', victory: 'neutral' } as Record<string, string>)[kind] ?? 'shard';
  }
  constructor() { afterRenderEffect(() => { this.events(); const list = this.list().nativeElement; list.scrollTop = list.scrollHeight; }); }
}

