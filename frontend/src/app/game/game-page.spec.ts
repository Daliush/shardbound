import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { FakeWebSocket } from '../testing/fake-web-socket';
import { aView } from '../testing/views';
import { GamePage } from './game-page';
import { WEB_SOCKET } from './game-socket.service';
import { EventView } from '../api/protocol';

describe('GamePage', () => {
  beforeEach(() => {
    localStorage.clear(); FakeWebSocket.reset();
    TestBed.configureTestingModule({ imports: [GamePage], providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([]), {provide:WEB_SOCKET,useValue:FakeWebSocket}] });
  });
  async function open(seated = true, view = aView(12), history: EventView[] = [{type:'turn_started',rules:['5.2'],text:'Turn 5: your turn.'}]) {
    if (seated) localStorage.setItem('shardbound.game.g-1.token','secret');
    const fixture = TestBed.createComponent(GamePage); fixture.componentRef.setInput('gameId','g-1');
    await fixture.whenStable();
    if (seated) { FakeWebSocket.latest().open(); FakeWebSocket.latest().receive({type:'state',view,history}); }
    await fixture.whenStable();
    const http = TestBed.inject(HttpTestingController);
    http.match('/api/cards').forEach(request => request.flush([]));
    http.match('/cards/art/manifest.json').forEach(request => request.flush({}));
    await fixture.whenStable();
    return fixture;
  }
  it('does not connect without a seat', async () => {
    const fixture = await open(false);
    expect(fixture.nativeElement.textContent).toContain('This browser holds no seat in this game.');
    expect(FakeWebSocket.instances).toHaveLength(0);
  });
  it('uses the token, server prompt and Chronicle, then sends the chosen action index', async () => {
    const fixture = await open(); const page = fixture.nativeElement as HTMLElement;
    expect(FakeWebSocket.latest().url).toContain('/ws/games/g-1?token=secret');
    expect(page.textContent).toContain('Your turn: play a card, attack, or end your turn.');
    expect(page.textContent).toContain('Turn 5: your turn.');
    (page.querySelector('[data-key="card:3"]') as HTMLButtonElement).click(); await fixture.whenStable();
    (page.querySelector('[data-key="unit:61"]') as HTMLButtonElement).click();
    expect(FakeWebSocket.latest().sent).toEqual([{type:'act',requestId:'c-1',decisionId:'d-13',action:0}]);
    (page.querySelector('[data-key="unit:61"]') as HTMLButtonElement).click();
    expect(FakeWebSocket.latest().sent).toHaveLength(1);
  });
  it('allows a discard to be chosen directly from the hand', async () => {
    const base = aView(20);
    const fixture = await open(true,aView(20,{you:{...base.you,hand:[...base.you.hand,{id:4,card:'neutral.shardling',cost:1,fractureStep:null}]},decision:{id:'d-21',kind:'choose_cards',prompt:'Choose one card.',actions:[{index:0,type:'choose_cards',label:'Discard Spark Dart #3',cards:[3]},{index:1,type:'choose_cards',label:'Discard Shardling #4',cards:[4]}]}}));
    (fixture.nativeElement.querySelector('[data-key="card:4"]') as HTMLButtonElement).click();
    expect(FakeWebSocket.latest().sent).toEqual([{type:'act',requestId:'c-1',decisionId:'d-21',action:1}]);
  });
  it('locks actions on a version gap until a full state arrives', async () => {
    const fixture = await open(); const socket = FakeWebSocket.latest();
    socket.receive({type:'update',view:aView(15),events:[]}); await fixture.whenStable();
    expect((fixture.nativeElement.querySelector('.end-turn') as HTMLButtonElement | null)).toBeNull();
    socket.receive({type:'state',view:aView(15),history:[]}); await fixture.whenStable();
    expect((fixture.nativeElement.querySelector('.end-turn') as HTMLButtonElement).disabled).toBe(false);
  });
  it('intercepts by selecting an eligible ally, then confirming or tapping the threatened unit', async () => {
    const base = aView(20);
    const view = {...base, you: {...base.you, units: [...base.you.units, {...base.you.units[0], id: 2}]}, decision: {id: 'd-21', kind: 'intercept' as const, prompt: 'Sprout #61 attacks your Cinderling #1 with Flick (3 damage). Intercept with another unit?', actions: [
      {index: 7, type: 'intercept' as const, interceptor: 2, label: 'Intercept with ally'},
      {index: 8, type: 'decline_intercept' as const, label: 'Let the attack through'},
    ]}};
    const fixture = await open(true, view, [{type: 'attack_declared', rules: [], text: 'Declared attack', attacker: {id: 61, card: 'root.sprout'}, target: {kind: 'unit', id: 1}, attackIndex: 0}]);
    const page = fixture.nativeElement as HTMLElement;
    expect(page.querySelector('app-intent-bar')?.textContent).toContain('Intercept with another unit?');
    expect(page.querySelector('app-intent-bar')?.textContent).toContain('Tap a glowing ally to intercept');
    expect(page.querySelector('[data-key="unit:1"] app-unit-token')?.textContent).toContain('Under attack');
    expect(page.querySelector('[data-key="unit:1"] .projection')?.textContent).toContain('Lethal');
    (page.querySelector('[data-key="unit:2"]') as HTMLButtonElement).click();
    await fixture.whenStable();
    expect(page.querySelector('[data-key="unit:1"] [data-mark]')?.getAttribute('data-mark')).toBe('targetable');
    expect([...page.querySelectorAll('app-intent-bar button')].map(button => button.textContent?.trim())).toContain('Intercept with ally');
    (page.querySelector('[data-key="unit:1"]') as HTMLButtonElement).click();
    expect(FakeWebSocket.latest().sent).toEqual([{type:'act',requestId:'c-1',decisionId:'d-21',action:7}]);
  });
  it('writes the outcome of the selected attack on each target before it is sent', async () => {
    const fixture = await open(); const page = fixture.nativeElement as HTMLElement;
    (page.querySelector('[data-key="unit:1"]') as HTMLButtonElement).click(); await fixture.whenStable();
    expect(page.querySelector('app-intent-bar')?.textContent).toContain('choose a target');
    expect(page.querySelector('[data-key="unit:61"] .projection')?.textContent).toContain('Lethal');
    expect(page.querySelector('[data-key="card:3"]')?.classList).toContain('dim');
    expect(FakeWebSocket.latest().sent).toHaveLength(0);
    (page.querySelector('[data-key="unit:61"]') as HTMLButtonElement).click();
    expect(FakeWebSocket.latest().sent).toEqual([{type:'act',requestId:'c-1',decisionId:'d-13',action:4}]);
  });
  it('submits an interception only after a pointer drag reaches the defender', async () => {
    const base = aView(20);
    const view = {...base, you: {...base.you, units: [...base.you.units, {...base.you.units[0], id: 2}]}, decision: {id: 'd-21', kind: 'intercept' as const, prompt: 'Intercept?', actions: [{index: 7, type: 'intercept' as const, interceptor: 2, label: 'Intercept with ally'}]}};
    const fixture = await open(true, view, [{type: 'attack_declared', rules: [], text: 'Declared attack', attacker: {id: 61, card: 'root.sprout'}, target: {kind: 'unit', id: 1}}]);
    const page = fixture.nativeElement as HTMLElement, protector = page.querySelector('[data-key="unit:2"]')!, defender = page.querySelector('[data-key="unit:1"]')!;
    const original = document.elementFromPoint;
    document.elementFromPoint = () => defender;
    try {
      protector.dispatchEvent(new MouseEvent('pointerdown', {bubbles: true, button: 0, clientX: 100, clientY: 300}));
      window.dispatchEvent(new MouseEvent('pointermove', {clientX: 150, clientY: 200}));
      await fixture.whenStable();
      expect(page.querySelector('.drag-arrow')).not.toBeNull();
      expect(FakeWebSocket.latest().sent).toHaveLength(0);
      window.dispatchEvent(new MouseEvent('pointerup', {clientX: 150, clientY: 200}));
      expect(FakeWebSocket.latest().sent).toEqual([{type:'act',requestId:'c-1',decisionId:'d-21',action:7}]);
    } finally { document.elementFromPoint = original; }
  });
});

