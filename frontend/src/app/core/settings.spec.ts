import { TestBed } from '@angular/core/testing';
import { Settings } from './settings';

describe('Application appearance', () => {
  let change: ((event: {matches:boolean}) => void) | undefined;
  beforeEach(() => {
    localStorage.clear();
    vi.stubGlobal('matchMedia', (query: string) => ({matches:query.includes('color-scheme'),addEventListener:(_:string,callback:typeof change) => { if (query.includes('color-scheme')) change = callback; },removeEventListener:vi.fn()}));
    TestBed.configureTestingModule({});
  });
  afterEach(() => { TestBed.resetTestingModule(); vi.unstubAllGlobals(); localStorage.clear(); delete document.documentElement.dataset['theme']; });
  it('follows the device until a theme is chosen and persists the choice', () => {
    const settings = TestBed.inject(Settings);
    TestBed.tick(); expect(settings.theme()).toBe('dark');
    change?.({matches:false}); TestBed.tick(); expect(settings.theme()).toBe('light');
    settings.update({theme:'dark'}); TestBed.tick();
    expect(document.documentElement.dataset['theme']).toBe('dark');
    expect(JSON.parse(localStorage.getItem('shardbound.preferences')!).theme).toBe('dark');
    change?.({matches:false}); TestBed.tick(); expect(settings.theme()).toBe('dark');
  });
  it('loads a saved theme without dropping existing preferences', () => {
    localStorage.setItem('shardbound.preferences',JSON.stringify({theme:'light',speed:2,sound:true}));
    const settings = TestBed.inject(Settings); TestBed.tick();
    expect(settings.theme()).toBe('light'); expect(settings.preferences().speed).toBe(2); expect(settings.preferences().sound).toBe(true);
  });
  it('falls back to the device for an invalid saved theme', () => {
    localStorage.setItem('shardbound.preferences',JSON.stringify({theme:'invalid'}));
    const settings = TestBed.inject(Settings); expect(settings.preferences().theme).toBe('system'); expect(settings.theme()).toBe('dark');
  });
});
