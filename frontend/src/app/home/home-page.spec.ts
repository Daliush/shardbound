import { TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';
import { HomePage } from './home-page';
import { Settings } from '../core/settings';
import { Inspect } from '../cards/inspect';

import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';

function render() {
  const fixture = TestBed.createComponent(HomePage);
  fixture.detectChanges();
  const http = TestBed.inject(HttpTestingController);
  http.expectOne('/api/cards').flush([
    {id: 'ember.ash-warden', name: 'Ash Warden', faction: 'ember', type: 'unit', cost: 3, defense: 6, text: [{kind: 'attack', text: 'Cinder Bite (2 Shards): Deal 4 damage to the target.'}]},
    {id: 'tide.deepcurrent-behemoth', name: 'Deepcurrent Behemoth', faction: 'tide', type: 'unit', cost: 7, defense: 14, text: []},
    {id: 'root.root-sentinel', name: 'Root Sentinel', faction: 'root', type: 'unit', cost: 2, defense: 5, text: [{kind: 'keywords', text: 'Anchor'}]},
  ]);
  fixture.detectChanges();
  http.expectOne('/cards/art/manifest.json').flush({});
  return fixture;
}

describe('Faction cards', () => {
  beforeEach(() => {
    localStorage.clear();
    TestBed.configureTestingModule({ imports: [HomePage], providers: [provideRouter([]), provideHttpClient(), provideHttpClientTesting()] });
  });
  afterEach(() => { TestBed.resetTestingModule(); localStorage.clear(); });

  it('selects exactly one faction and carries it into the arena link', async () => {
    const fixture = render();
    await fixture.whenStable();
    const element = fixture.nativeElement as HTMLElement;
    element.querySelector<HTMLButtonElement>('[data-faction="root"].portal')!.click();
    await fixture.whenStable();
    expect(element.querySelectorAll('[aria-pressed="true"]').length).toBe(1);
    expect(element.querySelectorAll('app-card').length).toBe(3);
    expect(element.querySelector('app-card')!.textContent).toContain('Cinder Bite');
    expect(element.querySelector('.arena-door')!.getAttribute('href')).toBe('/play?faction=root');
    expect(element.querySelector('.faction-detail')!.textContent).toContain('Root Sentinel');
    element.querySelector<HTMLButtonElement>('.inspect-link')!.click();
    expect(TestBed.inject(Inspect).card()?.id).toBe('root.root-sentinel');
  });

  it('supports arrow keys and wraps focus with the selection', async () => {
    const fixture = render();
    await fixture.whenStable();
    const portals = fixture.nativeElement.querySelectorAll('.portal') as NodeListOf<HTMLButtonElement>;
    const focus = vi.spyOn(portals[2], 'focus');
    portals[0].dispatchEvent(new KeyboardEvent('keydown', {key: 'ArrowLeft', bubbles: true}));
    await fixture.whenStable();
    expect(portals[2].getAttribute('aria-pressed')).toBe('true');
    expect(focus).toHaveBeenCalledOnce();
  });

  it('defaults to Ember without selecting a card and remembers the last hover', async () => {
    const fixture = render();
    await fixture.whenStable();
    const element = fixture.nativeElement as HTMLElement;
    expect(element.querySelectorAll('[aria-pressed="true"]').length).toBe(0);
    expect(element.querySelector('.world-image.visible')!.getAttribute('src')).toBe('/portals/ember.webp');
    const root = element.querySelector<HTMLButtonElement>('[data-faction="root"].portal')!;
    root.dispatchEvent(new Event('pointerenter'));
    await fixture.whenStable();
    expect(element.querySelector('.world-image.visible')!.getAttribute('src')).toBe('/portals/root.webp');
    root.dispatchEvent(new Event('pointerleave'));
    await fixture.whenStable();
    expect(element.querySelector('.world-image.visible')!.getAttribute('src')).toBe('/portals/root.webp');
    expect(element.querySelector('.arena-door')!.getAttribute('href')).toBe('/play?faction=root');
    expect(element.querySelectorAll('[aria-pressed="true"]').length).toBe(0);
    const tide = element.querySelector<HTMLButtonElement>('[data-faction="tide"].portal')!;
    tide.dispatchEvent(new Event('focus'));
    tide.dispatchEvent(new Event('blur'));
    await fixture.whenStable();
    expect(element.querySelector('.world-image.visible')!.getAttribute('src')).toBe('/portals/tide.webp');
  });

  it('returns to an explicit selection after previewing another faction', async () => {
    const fixture = render();
    await fixture.whenStable();
    const element = fixture.nativeElement as HTMLElement;
    element.querySelector<HTMLButtonElement>('[data-faction="ember"].portal')!.click();
    const root = element.querySelector<HTMLButtonElement>('[data-faction="root"].portal')!;
    root.dispatchEvent(new Event('pointerenter'));
    await fixture.whenStable();
    expect(element.querySelector('.world-image.visible')!.getAttribute('src')).toBe('/portals/root.webp');
    expect(element.querySelector('.arena-door')!.getAttribute('href')).toBe('/play?faction=ember');
    root.dispatchEvent(new Event('pointerleave'));
    await fixture.whenStable();
    expect(element.querySelector('.world-image.visible')!.getAttribute('src')).toBe('/portals/ember.webp');
  });

  it('shows a recoverable catalog error instead of raw card identifiers', async () => {
    const fixture = TestBed.createComponent(HomePage);
    fixture.detectChanges();
    TestBed.inject(HttpTestingController).expectOne('/api/cards').flush('Unavailable', {status: 503, statusText: 'Service unavailable'});
    await fixture.whenStable();
    const element = fixture.nativeElement as HTMLElement;
    expect(element.querySelector('.catalog-error')!.textContent).toContain('Reload cards');
    expect(element.querySelector<HTMLButtonElement>('.inspect-link')!.disabled).toBe(true);
    expect(element.querySelectorAll('.card-loading').length).toBe(3);
    expect(element.textContent).not.toContain('ember.ash-warden');
  });

  it('navigates immediately with reduced motion and keeps modified clicks native', async () => {
    const fixture = render();
    TestBed.inject(Settings).update({motion: 'on'});
    const navigate = vi.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true);
    await fixture.whenStable();
    const arena = fixture.nativeElement.querySelector('.arena-door') as HTMLAnchorElement;
    const modified = new MouseEvent('click', {ctrlKey: true, bubbles: true, cancelable: true});
    arena.dispatchEvent(modified);
    expect(modified.defaultPrevented).toBe(false);
    expect(navigate).not.toHaveBeenCalled();
    arena.click();
    expect(navigate).toHaveBeenCalledWith(['/play'], {queryParams: {faction: 'ember'}});
  });

  it('finishes the passage before routing, then resets if navigation is cancelled', async () => {
    const fixture = render();
    TestBed.inject(Settings).update({motion: 'off', speed: 1});
    const navigate = vi.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(false);
    await fixture.whenStable();
    let finish!: () => void;
    const cancel = vi.fn();
    const veil = fixture.nativeElement.querySelector('.passage');
    veil.animate = vi.fn(() => ({finished: new Promise<void>(resolve => {finish = resolve;}), cancel}));
    fixture.nativeElement.querySelector('.lab-door').click();
    expect(navigate).not.toHaveBeenCalled();
    finish();
    await fixture.whenStable();
    expect(navigate).toHaveBeenCalledWith(['/lab'], {});
    expect(cancel).toHaveBeenCalledOnce();
    expect(fixture.nativeElement.querySelector('.departing')).toBeNull();
  });
});

describe('Mobile faction carousel', () => {
  beforeEach(() => {
    localStorage.clear();
    vi.stubGlobal('matchMedia', (media: string) => ({
      media, matches: media === '(max-width: 800px)',
      addEventListener() {}, removeEventListener() {},
    }));
    TestBed.configureTestingModule({ imports: [HomePage], providers: [provideRouter([]), provideHttpClient(), provideHttpClientTesting()] });
  });
  afterEach(() => { TestBed.resetTestingModule(); localStorage.clear(); vi.unstubAllGlobals(); });

  function pointer(target: HTMLElement, type: string, x: number, y = 180, primary = true) {
    const event = new PointerEvent(type, { bubbles: true, cancelable: true, pointerId: 1, pointerType: 'touch', isPrimary: primary, button: 0, clientX: x, clientY: y });
    target.dispatchEvent(event);
    return event;
  }
  function swipe(target: HTMLElement, from: number, to: number) {
    pointer(target, 'pointerdown', from);
    pointer(target, 'pointermove', to);
    pointer(target, 'pointerup', to);
  }

  it('centers Ember initially and advances exactly one card for a long swipe, without a follow-up click', () => {
    const fixture = render();
    const element = fixture.nativeElement as HTMLElement;
    const carousel = element.querySelector<HTMLElement>('.portals')!;
    expect(element.querySelector('.carousel-main')!.getAttribute('data-faction')).toBe('ember');
    expect(carousel.getAttribute('aria-roledescription')).toBe('carousel');
    swipe(carousel, 380, 0);
    fixture.detectChanges();
    expect(element.querySelector('.carousel-main')!.getAttribute('data-faction')).toBe('tide');
    expect(element.querySelector('.arena-door')!.getAttribute('href')).toBe('/play?faction=tide');
    const root = element.querySelector<HTMLButtonElement>('.portal[data-faction="root"]')!;
    const click = new MouseEvent('click', {bubbles: true, cancelable: true, detail: 1});
    root.dispatchEvent(click);
    expect(click.defaultPrevented).toBe(true);
    swipe(carousel, 380, 0);
    fixture.detectChanges();
    expect(element.querySelector('.carousel-main')!.getAttribute('data-faction')).toBe('tide');
  });

  it('wraps both ways one step at a time and supports side-card taps with instant motion', () => {
    const fixture = render();
    TestBed.inject(Settings).update({speed: 0});
    fixture.detectChanges();
    const element = fixture.nativeElement as HTMLElement;
    const carousel = element.querySelector<HTMLElement>('.portals')!;
    swipe(carousel, 20, 280);
    fixture.detectChanges();
    expect(element.querySelector('.carousel-main')!.getAttribute('data-faction')).toBe('root');
    swipe(carousel, 280, 20);
    fixture.detectChanges();
    expect(element.querySelector('.carousel-main')!.getAttribute('data-faction')).toBe('ember');
    element.querySelector<HTMLButtonElement>('.portal[data-faction="tide"]')!.click();
    fixture.detectChanges();
    expect(element.querySelector('.carousel-main')!.getAttribute('data-faction')).toBe('tide');
    element.querySelector<HTMLButtonElement>('.carousel-arrow.next')!.click();
    fixture.detectChanges();
    expect(element.querySelector('.carousel-main')!.getAttribute('data-faction')).toBe('root');
  });

  it.each([{from: 260, to: 70, faction: 'tide'}, {from: 70, to: 260, faction: 'root'}])('keeps a touch swipe to $faction alive when capture transfers from the card to the carousel', ({from, to, faction}) => {
    const fixture = render();
    const element = fixture.nativeElement as HTMLElement;
    const carousel = element.querySelector<HTMLElement>('.portals')!;
    const card = element.querySelector<HTMLElement>('.carousel-main .card-surface')!;
    let captured = false;
    carousel.setPointerCapture = vi.fn(() => { captured = true; });
    carousel.hasPointerCapture = vi.fn(() => captured);
    carousel.releasePointerCapture = vi.fn(() => { captured = false; });

    // Touch implicitly captures the child. Transferring capture fires a bubbling loss
    // on that child before the carousel receives the following pointer events.
    const direction = Math.sign(to - from);
    pointer(card, 'pointerdown', from);
    pointer(card, 'gotpointercapture', from);
    pointer(card, 'pointermove', from + 70 * direction);
    expect(carousel.setPointerCapture).toHaveBeenCalledWith(1);
    pointer(card, 'lostpointercapture', from + 70 * direction);
    pointer(carousel, 'gotpointercapture', from + 70 * direction);
    pointer(carousel, 'pointermove', from + 140 * direction);
    pointer(carousel, 'pointerup', to);
    fixture.detectChanges();

    expect(element.querySelector('.carousel-main')!.getAttribute('data-faction')).toBe(faction);
    expect(element.querySelector('.arena-door')!.getAttribute('href')).toBe('/play?faction=' + faction);
    expect(carousel.releasePointerCapture).toHaveBeenCalledOnce();
  });

  it('cancels a genuine loss of carousel capture and accepts the next touch gesture', () => {
    const fixture = render();
    const element = fixture.nativeElement as HTMLElement;
    const carousel = element.querySelector<HTMLElement>('.portals')!;
    const card = element.querySelector<HTMLElement>('.carousel-main .card-surface')!;
    pointer(card, 'pointerdown', 260);
    pointer(card, 'pointermove', 190);
    pointer(carousel, 'lostpointercapture', 190);
    pointer(carousel, 'pointerup', 70);
    fixture.detectChanges();
    expect(element.querySelector('.carousel-main')!.getAttribute('data-faction')).toBe('ember');

    swipe(card, 260, 70);
    fixture.detectChanges();
    expect(element.querySelector('.carousel-main')!.getAttribute('data-faction')).toBe('tide');
  });

  it('does not select from vertical scrolling, small movement, cancellation, or secondary touches', () => {
    const fixture = render();
    const element = fixture.nativeElement as HTMLElement;
    const carousel = element.querySelector<HTMLElement>('.portals')!;
    pointer(carousel, 'pointerdown', 200, 100);
    const vertical = pointer(carousel, 'pointermove', 190, 240);
    pointer(carousel, 'pointerup', 190, 260);
    expect(vertical.defaultPrevented).toBe(false);
    swipe(carousel, 200, 180);
    pointer(carousel, 'pointerdown', 200);
    pointer(carousel, 'pointermove', 100);
    pointer(carousel, 'pointercancel', 100);
    pointer(carousel, 'pointerup', 100);
    pointer(carousel, 'pointerdown', 200, 180, false);
    pointer(carousel, 'pointerup', 100, 180, false);
    fixture.detectChanges();
    expect(element.querySelector('.carousel-main')!.getAttribute('data-faction')).toBe('ember');
    expect(element.querySelectorAll('.portal[aria-pressed="true"]').length).toBe(0);
  });

  it('keeps the centered card on incidental touch hover and supports arrow-key selection', () => {
    const fixture = render();
    TestBed.inject(Settings).update({motion: 'on'});
    fixture.detectChanges();
    const element = fixture.nativeElement as HTMLElement;
    element.querySelector('.portal[data-faction="root"]')!.dispatchEvent(new Event('pointerenter'));
    fixture.detectChanges();
    expect(element.querySelector('.world-image.visible')!.getAttribute('src')).toBe('/portals/ember.webp');
    expect(element.querySelectorAll('.portal[tabindex="0"]').length).toBe(1);
    element.querySelector('.portal[data-faction="ember"]')!.dispatchEvent(new KeyboardEvent('keydown', {key: 'ArrowRight', bubbles: true}));
    fixture.detectChanges();
    expect(element.querySelector('.carousel-main')!.getAttribute('data-faction')).toBe('tide');
    expect(element.querySelector('.portal[tabindex="0"]')!.getAttribute('data-faction')).toBe('tide');
    // A swipe can leave DOM focus on the former main card: keys still start at the visible one.
    element.querySelector('.portal[data-faction="ember"]')!.dispatchEvent(new KeyboardEvent('keydown', {key: 'ArrowRight', bubbles: true}));
    fixture.detectChanges();
    expect(element.querySelector('.carousel-main')!.getAttribute('data-faction')).toBe('root');
  });
});
