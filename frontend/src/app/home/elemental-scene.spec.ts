import { vi } from 'vitest';
import type { ElementalScene } from './elemental-scene';

// Keep real geometry, raycasting and particle updates; only the GPU and frame clock are replaced.
vi.doMock('three', async importOriginal => ({
  ...await importOriginal<typeof import('three')>(),
  WebGLRenderer: class {
    setPixelRatio() {} getPixelRatio() { return 1; } setClearColor() {} setSize() {}
    clear() {} render() {} dispose() {} forceContextLoss() {}
  },
}));

describe('Elemental trail lifecycle', () => {
  let scene: ElementalScene;
  let canvas: HTMLCanvasElement;
  let now: number;
  let nextId: number;
  let frames: Map<number, FrameRequestCallback>;

  function advance(ms: number) {
    for (let elapsed = 0; elapsed < ms; elapsed += 20) {
      now += 20;
      const pending = [...frames.values()];
      frames.clear();
      for (const frame of pending) frame(now);
    }
  }

  beforeEach(async () => {
    now = 1000; nextId = 0; frames = new Map();
    vi.spyOn(performance, 'now').mockImplementation(() => now);
    vi.stubGlobal('requestAnimationFrame', (callback: FrameRequestCallback) => { frames.set(++nextId, callback); return nextId; });
    vi.stubGlobal('cancelAnimationFrame', (id: number) => frames.delete(id));
    vi.stubGlobal('ResizeObserver', class { observe() {} disconnect() {} });
    canvas = document.createElement('canvas');
    canvas.getBoundingClientRect = () => ({left: 0, top: 0, width: 800, height: 600, right: 800, bottom: 600, x: 0, y: 0, toJSON: () => ({})});
    const { ElementalScene } = await import('./elemental-scene');
    scene = new ElementalScene(canvas);
  });

  afterEach(() => { scene.dispose(); vi.restoreAllMocks(); vi.unstubAllGlobals(); });

  it('keeps stationary grass sparse and retracts completely within 100ms of leaving', () => {
    scene.point('root', 400, 300);
    advance(1000);
    expect(Number(canvas.dataset['instances'])).toBeGreaterThan(0);
    expect(Number(canvas.dataset['instances'])).toBeLessThanOrEqual(8);
    scene.leave();
    advance(100);
    expect(canvas.dataset['instances']).toBe('0');
    expect(canvas.dataset['renderState']).toBe('idle');
    expect(frames.size).toBe(0);
  });

  it.each(['ember', 'tide'] as const)('fades %s out and stops rendering after leaving', kind => {
    scene.point(kind, 400, 300);
    advance(600);
    expect(Number(canvas.dataset['instances'])).toBeGreaterThan(0);
    expect(Number(canvas.dataset['instances'])).toBeLessThanOrEqual(8);
    scene.leave();
    advance(240);
    expect(canvas.dataset['renderState']).toBe('idle');
    expect(canvas.dataset['instances']).toBe('0');
    expect(frames.size).toBe(0);
  });

  it('stops immediately and can restart after a preference or visibility change', () => {
    scene.point('root', 400, 300);
    advance(200);
    scene.stop();
    expect(frames.size).toBe(0);
    expect(canvas.dataset['instances']).toBe('0');
    scene.point('tide', 400, 300);
    advance(300);
    expect(canvas.dataset['renderState']).toBe('running');
    expect(Number(canvas.dataset['instances'])).toBeGreaterThan(0);
  });
});
