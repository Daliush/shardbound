import { Component, computed, inject, input } from '@angular/core';
import { CardComponent } from '../cards/card';
import { Inspect } from '../cards/inspect';
import { Icon } from '../core/icon';
import { CardBook } from '../game/card-book';

const RULE_FILES: Record<string,[string,string]> = {
  '0':['00-vocabulary.md','Vocabulary'],'1':['01-the-game.md','The game'],'2':['02-deck-building.md','Deck building'],'3':['03-zones.md','Zones'],'4':['04-shards.md','Shards'],'5':['05-game-sequence.md','Game sequence'],'6':['06-cards.md','Cards'],'7':['07-combat.md','Combat'],'8':['08-effects.md','Effects'],'9':['09-triggers.md','Triggers'],'10':['10-targets.md','Targets'],'11.1':['11-1-echo.md','Echo'],'11.2':['11-2-fracture.md','Fracture'],'11.3':['11-3-anchor.md','Anchor'],'11.4':['11-4-overcharge.md','Overcharge'],'11.5':['11-5-link.md','Link'],'12':['12-open-points.md','Open points'],
};
@Component({
  selector:'app-chunk-chip', imports:[CardComponent,Icon],
  template:`@if (book.card(id()); as card) { <button class="chip quiet" (click)="inspect.open({id:card.id})"><app-card [cardId]="card.id" mode="thumb" /><span>{{ card.name }}</span></button> }
  @else if (rule(); as rule) { <a class="chip" [href]="rule.url" target="_blank" rel="noreferrer"><span class="number">§ {{ id() }}</span><small>{{ rule.name }}</small></a> }
  @else { <span class="chip term"><app-icon name="lab" />{{ id().startsWith('vocabulary.') ? id().slice(11) : id() }}</span> }`,
  styles:`:host { display: block; min-width: 0; }.chip { min-height: 40px; display: flex; align-items: center; gap: .45rem; padding: .25rem .4rem; font-size: .72rem; color: var(--text); text-decoration: none; width: 100%; justify-content: flex-start; }.chip span { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }.chip small { color: var(--muted); font-size: .63rem; margin-left: auto; }.chip app-card { width: 23px; height: 29px; flex-shrink: 0; }.term { overflow-wrap: anywhere; }.chip:hover { color: var(--prism-light); }`,
})
export class ChunkChip {
  readonly id = input.required<string>();
  protected readonly book = inject(CardBook);
  protected readonly inspect = inject(Inspect);
  protected readonly rule = computed(() => {
    const id = this.id();
    if (!/^\d+(\.\d+)*$/.test(id)) return null;
    const section = id.startsWith('11.') ? id.split('.').slice(0,2).join('.') : id.split('.')[0];
    const file = RULE_FILES[section];
    return file ? {name:file[1],url:'https://github.com/Daliush/shardbound/blob/main/docs/rules/' + file[0]} : null;
  });
}

