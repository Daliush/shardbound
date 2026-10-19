import { Component, computed, inject, signal } from '@angular/core';
import { CardComponent } from '../../cards/card';
import { Inspect } from '../../cards/inspect';
import { Settings } from '../../core/settings';
import { CardBook } from '../../game/card-book';
import { EventView, GameView, UnitView } from '../../api/protocol';
import { aView } from '../../testing/views';
import { ArenaBoard } from '../board';
import { Anchors } from './anchors';
import { EVENT_BEATS } from './choreographer';
import { Director } from './director';
import { ArenaStage } from './stage-player';
import { StageOverlay } from './stage-overlay';
import { Feedback } from './feedback';

const HAND = ['ember.spark-dart', 'neutral.binding-thread', 'tide.receding-wave', 'tide.deepcurrent-behemoth', 'tide.coral-font', 'tide.mire-eel',
  'tide.brine-adept', 'tide.moonpull', 'neutral.shard-construct', 'neutral.shard-construct'];

@Component({
  selector: 'app-sandbox',
  imports: [CardComponent, ArenaBoard, StageOverlay],
  providers: [Anchors, ArenaStage, Feedback],
  template: `<section class="page"><div class="heading"><div><h1>The stage</h1><p class="muted">Synthetic fixtures · development only</p></div><button (click)="reset()">Reset board</button></div>
    <div class="controls"><label>Card <select (change)="cardId.set(value($event)); reset()">@for (card of book.cards(); track card.id) { <option [value]="card.id" [selected]="card.id === cardId()">{{ card.name }}</option> }</select></label>
    <label>Hand <select (change)="handSize.set(+value($event)); reset()">@for (size of handSizes; track size) { <option [value]="size" [selected]="size === handSize()">{{ size }}</option> }</select></label>
    <label>Speed <select [value]="settings.preferences().speed" (change)="setSpeed($event)"><option value="1">1×</option><option value="2">2×</option><option value="0">Instant</option></select></label>
    <label><input type="checkbox" [checked]="settings.reducedMotion()" (change)="settings.update({motion: settings.reducedMotion() ? 'off' : 'on'})" />Reduced motion</label><button (click)="director.skip()">Skip</button><button (click)="inspect.open({id:cardId()})">Inspect card</button></div>
    <div class="fake-table">@if (director.shownView(); as view) { <app-arena-board [view]="view" [locked]="director.busy()" /> }</div>
    <p role="status" class="event-caption">{{ lastEvent() }}</p><div class="event-buttons">@for (type of eventTypes; track type) { <button [disabled]="director.busy()" (click)="play(type)">{{ type }}</button> }</div>
    <details><summary>Card faces and all unit states</summary><button (click)="back.set(!back())">Flip card</button><div class="gallery"><app-card [cardId]="cardId()" mode="inspect" [back]="back()" /><div class="states">@for (unit of examples(); track unit.id) { <div><app-card [cardId]="unit.card" [unit]="unit" mode="board" /><p>{{ names[unit.id - 1] }}</p></div> }</div></div></details>
  </section><app-stage-overlay />`,
  styles: `.page { max-width: 1440px; }.heading { display: flex; justify-content: space-between; align-items: center; }.heading h1 { font-size: 3rem; margin-bottom: .3rem; }.controls { display: flex; flex-wrap: wrap; gap: 1rem; align-items: center; }label { display: flex; align-items: center; gap: .5rem; font-size: .8rem; }.fake-table { height: min(760px, 90dvh); overflow: hidden; border-block: 1px solid var(--line); margin-top: 1.5rem; }.event-caption { margin: 1rem 0; font-size: .8rem; }.event-buttons { display: flex; flex-wrap: wrap; gap: .35rem; margin: 1.5rem 0; }.event-buttons button { font: .7rem var(--font-number); }.gallery { display: flex; flex-wrap: wrap; gap: 2rem; margin-top: 1rem; }.states { display: grid; grid-template-columns: repeat(3,1fr); gap: 1rem; }.states p { font-size: .7rem; }.controls select { max-width: 210px; }summary { cursor: pointer; padding-block: 1rem; }`,
})
export class Sandbox {
  protected readonly book = inject(CardBook);
  protected readonly inspect = inject(Inspect);
  protected readonly settings = inject(Settings);
  protected readonly director = new Director(inject(ArenaStage));
  protected readonly eventTypes = Object.keys(EVENT_BEATS);
  protected readonly cardId = signal('ember.ash-warden');
  protected readonly handSizes = Array.from({ length: 11 }, (_, size) => size);
  protected readonly handSize = signal(4);
  protected readonly back = signal(false);
  protected readonly lastEvent = signal('Choose an event to rehearse its beat.');
  protected readonly names = ['Frozen', 'Doomed', 'Anchored', 'Linked', 'Modified', 'Token', 'Arrived', 'Ready', 'Attacked'];
  protected readonly examples = computed(() => {
    const base = aView(0).you.units[0];
    const changes: Partial<UnitView>[] = [{ frozen: true }, { doomed: true, defense: 0 }, { anchorProtected: true }, { linkedTo: 3 }, { modifiers: [{ attackDamage: 2, defense: 0, duration: 'end_of_turn' }] }, { token: true, card: 'root.sprout' }, { arrivedThisTurn: true }, {}, { hasAttackedThisTurn: true }];
    return changes.map((change, index) => ({ ...base, card: this.cardId(), id: index + 1, ...change }));
  });
  constructor() { this.reset(); }
  protected reset(): void { this.director.reset(this.fixture(), []); }
  private fixture(): GameView {
    const base = aView(0);
    return { ...base, decision: null, you: { ...base.you, units: this.examples().slice(0,6), hand: [this.cardId(), ...HAND].slice(0, this.handSize()).map((card,index) => ({id:100+index,card,cost:2,fractureStep:null})) }, opponent: { ...base.opponent!, units: this.examples().slice(3,9).map(unit => ({...unit,id:unit.id+60,controller:'opponent' as const})) } };
  }
  protected play(type: string): void {
    const old = this.fixture(); this.director.reset(old, []);
    const card = {id:1,card:this.cardId()};
    const event: EventView = {type,rules:['6.1'],text:type.replaceAll('_',' ') + ' · preview',unit:card,card,source:card,attacker:card,interceptor:card,player:'you',controller:'you',firstPlayer:'you',target:{kind:'unit',id:64},originalTarget:{kind:'unit',id:64},second:{id:4,card:this.cardId()},amount:5,percent:50};
    if (['player_damaged','player_healed','hp_lost','shards_refilled','shards_gained','shards_locked','game_ended','turn_started'].includes(type)) { delete event['unit']; delete event['card']; delete event['source']; delete event['attacker']; delete event['interceptor']; }
    const next: GameView = { ...old, version: 1, you: { ...old.you, hp:type === 'player_damaged' ? 45 : 50, units: ['unit_destroyed','unit_sacrificed','token_vanished'].includes(type) ? old.you.units.slice(1) : old.you.units } };
    if (type === 'game_ended') next.result = { outcome:'win',reason:'hp' };
    this.lastEvent.set(event.text); this.director.enqueue(next,[event]);
  }
  protected value(event: Event): string { return (event.target as HTMLSelectElement).value; }
  protected setSpeed(event: Event): void { this.settings.update({speed:Number(this.value(event)) as 0|1|2}); }
}

