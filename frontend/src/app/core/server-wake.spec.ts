import { HttpClient, provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { AWAKE_FOR_MS, LONG_AFTER_S, SLOW_AFTER_MS, ServerWake, watchServerWake } from './server-wake';
import { ServerWakeBanner } from './server-wake-banner';

describe('ServerWake', () => {
  let wake: ServerWake;

  beforeEach(() => {
    vi.useFakeTimers();
    TestBed.configureTestingModule({ providers: [provideHttpClient(withInterceptors([watchServerWake])), provideHttpClientTesting()] });
    wake = TestBed.inject(ServerWake);
  });

  afterEach(() => vi.useRealTimers());

  it('says nothing when the server answers quickly', () => {
    wake.startWait().end(true);
    vi.advanceTimersByTime(SLOW_AFTER_MS * 2);

    expect(wake.state()).toBe('idle');
  });

  it('says the server is waking up once a wait lasts, and counts the seconds', () => {
    wake.startWait();
    vi.advanceTimersByTime(SLOW_AFTER_MS);
    expect(wake.state()).toBe('waking');
    expect(wake.waitedSeconds()).toBe(3);

    vi.advanceTimersByTime(9000);
    expect(wake.waitedSeconds()).toBe(12);
    expect(wake.takingLong()).toBe(false);

    vi.advanceTimersByTime((LONG_AFTER_S - 12) * 1000);
    expect(wake.takingLong()).toBe(true);
  });

  it('says the server is up when it answers, then hides', () => {
    const wait = wake.startWait();
    vi.advanceTimersByTime(SLOW_AFTER_MS);
    wait.end(true);
    expect(wake.state()).toBe('awake');

    vi.advanceTimersByTime(AWAKE_FOR_MS);
    expect(wake.state()).toBe('idle');
  });

  it('hides without saying the server is up when the wait fails', () => {
    const wait = wake.startWait();
    vi.advanceTimersByTime(SLOW_AFTER_MS);
    wait.end(false);

    expect(wake.state()).toBe('idle');
  });

  it('counts a wait once, however many times it ends', () => {
    const first = wake.startWait();
    const second = wake.startWait();
    vi.advanceTimersByTime(SLOW_AFTER_MS);
    first.end(false);
    first.end(false);

    expect(wake.state()).toBe('waking');
    second.end(false);
    expect(wake.state()).toBe('idle');
  });

  it('stays hidden once dismissed, until the next wait lasts', () => {
    const wait = wake.startWait();
    vi.advanceTimersByTime(SLOW_AFTER_MS);
    wake.dismiss();
    wait.end(true);
    expect(wake.state()).toBe('idle');

    wake.startWait();
    vi.advanceTimersByTime(SLOW_AFTER_MS);
    expect(wake.state()).toBe('waking');
  });

  it('times the requests to the game server only, and takes an error page as an answer', () => {
    const http = TestBed.inject(HttpClient);
    const server = TestBed.inject(HttpTestingController);
    http.get('/portals/prompts.json').subscribe();
    vi.advanceTimersByTime(SLOW_AFTER_MS);
    expect(wake.state()).toBe('idle');

    http.get('/api/decks').subscribe({ error: () => undefined });
    vi.advanceTimersByTime(SLOW_AFTER_MS);
    expect(wake.state()).toBe('waking');

    server.expectOne('/api/decks').flush('', { status: 404, statusText: 'Not Found' });
    expect(wake.state()).toBe('awake');
  });

  it('does not take a gateway error as an answer', () => {
    TestBed.inject(HttpClient).get('/api/decks').subscribe({ error: () => undefined });
    vi.advanceTimersByTime(SLOW_AFTER_MS);
    TestBed.inject(HttpTestingController).expectOne('/api/decks').flush('', { status: 503, statusText: 'Service Unavailable' });

    expect(wake.state()).toBe('idle');
  });

  it('shows the wait and the reason at the top of the page', async () => {
    const fixture = TestBed.createComponent(ServerWakeBanner);
    wake.startWait();
    vi.advanceTimersByTime(SLOW_AFTER_MS);
    fixture.detectChanges();
    await fixture.whenStable();

    const text = fixture.nativeElement.textContent;
    expect(text).toContain('Waking up the server');
    expect(text).toContain('3s');
    expect(text).toContain('hosting costs almost nothing');
  });
});
