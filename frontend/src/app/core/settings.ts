import { Injectable, OnDestroy, computed, effect, signal } from '@angular/core';

export interface Preferences {
  theme: 'system' | 'light' | 'dark';
  speed: 0 | 0.5 | 1 | 2;
  motion: 'system' | 'on' | 'off';
  sound: boolean;
  haptics: boolean;
  gyroscope: boolean;
  ruleIds: boolean;
}
const DEFAULTS: Preferences = { theme: 'system', speed: 1, motion: 'system', sound: false, haptics: true, gyroscope: false, ruleIds: true };
const KEY = 'shardbound.preferences';

@Injectable({ providedIn: 'root' })
export class Settings implements OnDestroy {
  readonly preferences = signal<Preferences>(readPreferences());
  private readonly colorQuery = typeof matchMedia === 'function' ? matchMedia('(prefers-color-scheme: dark)') : null;
  private readonly systemDark = signal(this.colorQuery?.matches ?? false);
  readonly theme = computed(() => this.preferences().theme === 'system' ? (this.systemDark() ? 'dark' : 'light') : this.preferences().theme);
  private readonly colorChanged = (event: MediaQueryListEvent) => this.systemDark.set(event.matches);
  private readonly query = typeof matchMedia === 'function' ? matchMedia('(prefers-reduced-motion: reduce)') : null;
  private readonly systemMotion = signal(this.query?.matches ?? false);
  readonly reducedMotion = computed(() => this.preferences().motion === 'on' || (this.preferences().motion === 'system' && this.systemMotion()));
  readonly tilt = signal({ x: 0, y: 0 });
  private readonly motionChanged = (event: MediaQueryListEvent) => this.systemMotion.set(event.matches);
  private readonly visibilityChanged = () => { document.documentElement.dataset['hidden'] = String(document.hidden); };
  private readonly orientationChanged = (event: DeviceOrientationEvent) => {
    if (this.preferences().gyroscope && !this.reducedMotion()) this.tilt.set({ x: Math.max(-8, Math.min(8, (event.beta ?? 0) - 45)), y: Math.max(-8, Math.min(8, event.gamma ?? 0)) });
  };

  constructor() {
    this.colorQuery?.addEventListener('change', this.colorChanged);
    this.query?.addEventListener('change', this.motionChanged);
    document.addEventListener('visibilitychange', this.visibilityChanged);
    window.addEventListener('deviceorientation', this.orientationChanged);
    effect(() => {
      const settings = this.preferences();
      try { localStorage.setItem(KEY, JSON.stringify(settings)); } catch { /* Private browsing may deny storage. */ }
      document.documentElement.dataset['reducedMotion'] = String(this.reducedMotion());
      document.documentElement.dataset['theme'] = this.theme();
      document.querySelector('meta[name="theme-color"]')?.setAttribute('content', this.theme() === 'dark' ? '#252d3a' : '#e6e3dc');
    });
  }

  update(changes: Partial<Preferences>): void { this.preferences.update(value => ({ ...value, ...changes })); }

  async enableGyroscope(): Promise<boolean> {
    const sensor = globalThis.DeviceOrientationEvent as typeof DeviceOrientationEvent & { requestPermission?: () => Promise<string> };
    if (!sensor) return false;
    try {
      if (sensor.requestPermission && await sensor.requestPermission() !== 'granted') return false;
      this.update({ gyroscope: true });
      return true;
    } catch { return false; }
  }

  ngOnDestroy(): void {
    this.colorQuery?.removeEventListener('change', this.colorChanged);
    this.query?.removeEventListener('change', this.motionChanged);
    document.removeEventListener('visibilitychange', this.visibilityChanged);
    window.removeEventListener('deviceorientation', this.orientationChanged);
  }
}

function readPreferences(): Preferences {
  try {
    const saved = JSON.parse(localStorage.getItem(KEY) ?? '{}');
    const value = { ...DEFAULTS };
    if (['system', 'light', 'dark'].includes(saved.theme)) value.theme = saved.theme;
    if ([0, 0.5, 1, 2].includes(saved.speed)) value.speed = saved.speed;
    if (['system', 'on', 'off'].includes(saved.motion)) value.motion = saved.motion;
    for (const key of ['sound', 'haptics', 'ruleIds'] as const) if (typeof saved[key] === 'boolean') value[key] = saved[key];
    // Sensor permission must be obtained again from a user gesture.
    return value;
  } catch { return { ...DEFAULTS }; }
}
