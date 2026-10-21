import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { Inspect } from '../cards/inspect';
import { CollectionPage } from './collection-page';

describe('CollectionPage', () => {
  beforeEach(() => TestBed.configureTestingModule({ imports: [CollectionPage], providers: [provideRouter([]), provideHttpClient(), provideHttpClientTesting()] }));
  afterEach(() => TestBed.resetTestingModule());

  async function render() {
    const fixture = TestBed.createComponent(CollectionPage);
    fixture.detectChanges();
    const http = TestBed.inject(HttpTestingController);
    http.expectOne('/api/cards').flush([
      { id: 'ember.ash-warden', name: 'Ash Warden', faction: 'ember', type: 'unit', cost: 3, defense: 6, token: false, keywords: [], text: [] },
      { id: 'tide.moonpull', name: 'Moonpull', faction: 'tide', type: 'spell', token: false, keywords: [], text: [{ kind: 'effect', text: 'Freeze an enemy unit.' }], fracture: [{ step: 1, cost: 1 }] },
    ]);
    http.expectOne('/api/decks').flush([{ id: 'tide-starter', name: 'Tide Starter', faction: 'tide', cards: [{ card: 'tide.moonpull', count: 2 }] }]);
    await fixture.whenStable();
    return fixture;
  }

  it('shows every card by faction, regroups by deck with the copies, and opens a card', async () => {
    const fixture = await render(), page = fixture.nativeElement as HTMLElement;
    expect([...page.querySelectorAll('.shelf-head h2')].map(heading => heading.textContent?.trim())).toEqual(['Ember', 'Tide']);
    expect(page.querySelectorAll('.tile app-card').length).toBe(2);
    [...page.querySelectorAll<HTMLButtonElement>('.segmented button')].find(button => button.textContent?.trim() === 'Deck')!.click();
    await fixture.whenStable();
    expect([...page.querySelectorAll('.shelf-head h2')].map(heading => heading.textContent?.trim())).toEqual(['Tide Starter', 'In no starter deck']);
    expect(page.querySelector('.copies')?.textContent).toBe('×2');
    page.querySelector<HTMLButtonElement>('.tile .open')!.click();
    expect(TestBed.inject(Inspect).card()?.id).toBe('tide.moonpull');
  });

  it('narrows the cards to a search, and says so when nothing matches', async () => {
    const fixture = await render(), page = fixture.nativeElement as HTMLElement, search = page.querySelector<HTMLInputElement>('.search input')!;
    search.value = 'freeze'; search.dispatchEvent(new Event('input'));
    await fixture.whenStable();
    expect(page.querySelector('.tally')?.textContent).toContain('1 of 2 cards');
    search.value = 'dragon'; search.dispatchEvent(new Event('input'));
    await fixture.whenStable();
    expect(page.querySelector('.empty')?.textContent).toContain('No card matches “dragon”');
  });
});
