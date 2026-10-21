import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { CardArt } from './card-art';

describe('CardArt', () => {
  beforeEach(() => TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] }));
  afterEach(() => TestBed.resetTestingModule());

  it('waits for the cards, then shows the art the server gives the address of, or else a placeholder', () => {
    const art = TestBed.inject(CardArt);
    expect(art.source('ember.ash-warden', 'ember')).toBe('');
    TestBed.inject(HttpTestingController).expectOne('/api/cards').flush([
      { id: 'ember.ash-warden', name: 'Ash Warden', faction: 'ember', type: 'unit', token: false, keywords: [], text: [], art: '/api/cards/ember.ash-warden/art?v=1' },
      { id: 'root.sprout', name: 'Sprout', faction: 'root', type: 'unit', token: true, keywords: [], text: [] },
    ]);
    expect(art.source('ember.ash-warden', 'ember')).toBe('/api/cards/ember.ash-warden/art?v=1');
    expect(art.source('root.sprout', 'root')).toMatch(/^data:image\/svg\+xml/);
  });
});
