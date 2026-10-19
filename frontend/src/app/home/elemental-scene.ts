import {
  AdditiveBlending, AmbientLight, BufferGeometry, Color, DirectionalLight, DoubleSide,
  DynamicDrawUsage, Float32BufferAttribute, InstancedMesh, Material, MeshBasicMaterial,
  MeshStandardMaterial, Object3D, PerspectiveCamera, Plane, PlaneGeometry, Raycaster, Scene,
  ShaderMaterial, SphereGeometry, Vector2, Vector3, WebGLRenderer,
} from 'three';
import { elementalMaterial } from './elemental-materials';

export type ElementKind = 'ember' | 'tide' | 'root';
type Shape = 'trail' | 'spark' | 'grass' | 'current';
interface Particle {
  x: number; y: number; z: number; vx: number; vy: number; vz: number;
  born: number; life: number; size: number; rotation: number; active: boolean; released: number;
}
interface Pool { mesh: InstancedMesh; particles: Particle[]; cursor: number; }
const CAPACITY: Record<Shape, number> = { trail: 128, spark: 8, grass: 32, current: 5 };

/** One lazy WebGL context for all three cards. Geometry is shared, never allocated per frame. */
export class ElementalScene {
  private readonly renderer: WebGLRenderer;
  private readonly world = new Scene();
  private readonly camera = new PerspectiveCamera(40, 1, 1, 2000);
  private readonly ray = new Raycaster();
  private readonly surface = new Plane(new Vector3(0, 0, 1), 0);
  private readonly ndc = new Vector2();
  private readonly hit = new Vector3();
  private readonly pointer = new Vector3();
  private readonly last = new Vector3();
  private readonly transform = new Object3D();
  private readonly fadeColor = new Color();
  private readonly pools: Record<Shape, Pool>;
  private readonly resize: ResizeObserver;
  private kind: ElementKind | null = null;
  private frame = 0;
  private previous = 0;
  private nextRender = 0;
  private emitted = 0;
  private sparked = 0;
  private frames = 0;
  private sampleStarted = 0;
  private disposed = false;
  private lost = false;
  private width = 0;
  private height = 0;
  private readonly onHidden = () => { if (document.hidden) this.stop(); };
  private readonly onScroll = () => this.leave();
  private readonly onLost = (event: Event) => { event.preventDefault(); this.lost = true; this.stop(); };
  private readonly onRestored = () => { this.lost = false; };

  constructor(private readonly canvas: HTMLCanvasElement) {
    this.renderer = new WebGLRenderer({ canvas, alpha: true, antialias: true, powerPreference: 'low-power' });
    this.renderer.setPixelRatio(Math.min(window.devicePixelRatio || 1, 1.5));
    this.renderer.setClearColor(0x000000, 0);
    this.camera.position.z = 700;
    const light = new DirectionalLight(0xfff4d8, 3.5);
    light.position.set(-100, 160, 250);
    this.world.add(light, new AmbientLight(0xd9edff, 2));

    this.pools = {
      trail: this.pool('trail', new PlaneGeometry(2, 2), elementalMaterial('trail')),
      spark: this.pool('spark', new SphereGeometry(1, 5, 4), new MeshBasicMaterial({ color: 0xffa04d, blending: AdditiveBlending, transparent: true, opacity: .75, depthWrite: false })),
      grass: this.pool('grass', grassGeometry(), new MeshStandardMaterial({ color: 0xbfe09a, roughness: .8, metalness: 0, side: DoubleSide, vertexColors: true, transparent: true, opacity: .95, depthWrite: false })),
      current: this.pool('current', new PlaneGeometry(2, 2), elementalMaterial('current')),
    };
    this.resize = new ResizeObserver(() => this.measure());
    this.resize.observe(canvas);
    this.measure();
    document.addEventListener('visibilitychange', this.onHidden);
    window.addEventListener('scroll', this.onScroll, { passive: true, capture: true });
    window.addEventListener('blur', this.onScroll);
    canvas.addEventListener('webglcontextlost', this.onLost);
    canvas.addEventListener('webglcontextrestored', this.onRestored);
  }

  point(kind: ElementKind, clientX: number, clientY: number): void {
    if (this.disposed || this.lost || document.hidden) return;
    const bounds = this.canvas.getBoundingClientRect();
    if (!bounds.width || !bounds.height) return;
    this.ndc.set((clientX - bounds.left) / bounds.width * 2 - 1, -(clientY - bounds.top) / bounds.height * 2 + 1);
    this.camera.updateMatrixWorld();
    this.ray.setFromCamera(this.ndc, this.camera);
    if (!this.ray.ray.intersectPlane(this.surface, this.hit)) return;
    if (this.kind && kind !== this.kind) this.leave();
    if (!this.kind) this.last.copy(this.hit);
    this.kind = kind;
    this.pointer.copy(this.hit);
    this.canvas.dataset['element'] = kind;
    if (!this.frame) {
      this.previous = performance.now(); this.sampleStarted = this.previous; this.frames = 0;
      this.nextRender = this.previous + 1000 / 60;
      this.frame = requestAnimationFrame(this.tick);
    }
  }

  leave(): void {
    this.kind = null;
    const now = performance.now();
    for (const pool of Object.values(this.pools)) for (const particle of pool.particles) {
      if (particle.active && particle.released < 0) particle.released = now;
    }
    this.emitted = 0;
  }

  stop(): void {
    cancelAnimationFrame(this.frame); this.frame = 0; this.kind = null;
    for (const pool of Object.values(this.pools)) {
      for (const particle of pool.particles) particle.active = false;
      pool.mesh.count = 0;
    }
    if (!this.lost) this.renderer.clear();
    this.canvas.dataset['renderState'] = 'idle';
    this.canvas.dataset['instances'] = '0';
  }

  dispose(): void {
    this.stop(); this.disposed = true; this.resize.disconnect();
    document.removeEventListener('visibilitychange', this.onHidden);
    window.removeEventListener('scroll', this.onScroll, true);
    window.removeEventListener('blur', this.onScroll);
    this.canvas.removeEventListener('webglcontextlost', this.onLost);
    this.canvas.removeEventListener('webglcontextrestored', this.onRestored);
    for (const pool of Object.values(this.pools)) {
      pool.mesh.geometry.dispose();
      (pool.mesh.material as Material).dispose();
      pool.mesh.dispose();
    }
    this.world.clear(); this.renderer.dispose(); this.renderer.forceContextLoss();
  }

  private measure(): void {
    const { width, height } = this.canvas.getBoundingClientRect();
    if (width === this.width && height === this.height) return;
    this.stop();
    this.width = width; this.height = height;
    if (!width || !height) return;
    this.renderer.setSize(width, height, false);
    this.camera.aspect = width / height;
    // At z = 0, one world unit equals one CSS pixel regardless of viewport size.
    this.camera.fov = 2 * Math.atan(height / (2 * this.camera.position.z)) * 180 / Math.PI;
    this.camera.updateProjectionMatrix();
  }

  private pool(shape: Shape, geometry: BufferGeometry, material: Material): Pool {
    const mesh = new InstancedMesh(geometry, material, CAPACITY[shape]);
    mesh.instanceMatrix.setUsage(DynamicDrawUsage);
    mesh.frustumCulled = false;
    mesh.count = 0;
    // Allocate instanceColor before the first shader compile, including the empty first frame.
    mesh.setColorAt(0, new Color(1, 1, 1));
    this.world.add(mesh);
    return { mesh, cursor: 0, particles: Array.from({ length: CAPACITY[shape] }, () => ({ x: 0, y: 0, z: 0, vx: 0, vy: 0, vz: 0, born: 0, life: 1, size: 1, rotation: 0, active: false, released: -1 })) };
  }

  private spawn(shape: Shape, now: number, size: number, life: number, spread: number, x = this.pointer.x, y = this.pointer.y): void {
    const pool = this.pools[shape];
    const p = pool.particles[pool.cursor++ % pool.particles.length];
    Object.assign(p, {
      x: x + (Math.random() - .5) * spread,
      y: y + (Math.random() - .5) * spread * .5,
      z: 5 + Math.random() * 4, vx: (Math.random() - .5) * 12,
      vy: 14 + Math.random() * 12, vz: 0,
      born: now, life, size: size * (.8 + Math.random() * .4), rotation: (Math.random() - .5) * .7, active: true, released: -1,
    });
  }

  private emit(now: number): void {
    if (this.kind === 'ember') this.burn(now);
    else if (this.kind === 'root') this.grow(now);
    else if (this.kind === 'tide' && now - this.emitted >= 240) {
      this.emitted = now;
      this.spawn('current', now, 48, 1.05, 6);
    }
  }

  /** Embers laid along the cursor's path make a glowing trail; a resting cursor keeps a small flicker. */
  private burn(now: number): void {
    const dx = this.pointer.x - this.last.x, dy = this.pointer.y - this.last.y;
    const steps = Math.min(16, Math.floor(Math.hypot(dx, dy) / 4));
    for (let step = 1; step <= steps; step++) this.spawn('trail', now, 13, .55, 2, this.last.x + dx * step / steps, this.last.y + dy * step / steps);
    if (steps) this.last.copy(this.pointer);
    else if (now - this.emitted >= 50) { this.emitted = now; this.spawn('trail', now, 12, .45, 6); }
    if (now - this.sparked >= 160) { this.sparked = now; this.spawn('spark', now, .9, .7, 16); }
  }

  /** Grass grows along the cursor's path and stays a moment; a resting cursor grows a small tuft. */
  private grow(now: number): void {
    const moved = Math.hypot(this.pointer.x - this.last.x, this.pointer.y - this.last.y) >= 14;
    if (now - this.emitted < (moved ? 60 : 320)) return;
    this.emitted = now; this.last.copy(this.pointer);
    this.spawn('grass', now, 26, .85, 30);
    this.spawn('grass', now, 18, .7, 26);
  }

  private readonly tick = (now: number): void => {
    this.frame = 0;
    if (this.disposed || this.lost || document.hidden) { this.stop(); return; }
    // High refresh-rate displays need no more than 60 renders per second here.
    if (now < this.nextRender) { this.frame = requestAnimationFrame(this.tick); return; }
    const dt = Math.min((now - this.previous) / 1000, .04);
    const interval = 1000 / 60;
    this.nextRender = now + interval - ((now - this.nextRender) % interval);
    this.previous = now;
    this.emit(now);
    for (const shape of ['trail', 'current'] as const) (this.pools[shape].mesh.material as ShaderMaterial).uniforms['time'].value = now / 1000;
    let alive = 0;
    for (const [shape, pool] of Object.entries(this.pools) as [Shape, Pool][]) {
      let count = 0;
      for (const p of pool.particles) {
        if (!p.active) continue;
        const age = (now - p.born) / 1000, progress = age / p.life;
        const release = p.released < 0 ? 1 : Math.max(0, 1 - (now - p.released) / (shape === 'grass' ? 180 : 220));
        if (progress >= 1 || release <= 0) { p.active = false; continue; }
        const fade = (shape === 'trail' ? 1 - progress : Math.min(1, (1 - progress) * 3)) * release;
        this.transform.position.set(p.x, p.y, p.z);
        this.transform.rotation.set(0, 0, p.rotation);
        if (shape === 'grass') {
          const grow = Math.min(1, age / .18);
          this.transform.scale.set(p.size * Math.sqrt(fade), p.size * grow * fade, p.size * fade);
          this.transform.rotation.set(-.3, p.rotation, Math.sin(now / 650 + p.x) * .13 + p.rotation * .18);
        } else if (shape === 'current') {
          const radius = p.size * (.55 + progress * .95);
          this.transform.rotation.set(.28, .12, p.rotation);
          this.transform.scale.set(radius, radius, 1);
        } else if (shape === 'trail') {
          p.x += p.vx * dt; p.y += p.vy * dt;
          const size = p.size * (1 - progress * .55);
          this.transform.scale.set(size, size, 1);
        } else {
          p.x += p.vx * dt; p.y += p.vy * dt; p.z += p.vz * dt;
          const size = p.size * fade * Math.min(1, age / .08);
          this.transform.scale.set(size, size * 1.7, size);
        }
        const opacity = fade * Math.min(1, age / (shape === 'trail' ? .03 : .12));
        // The trail's shader reads green as heat: a fresh ember burns orange, an old one fades to deep red.
        pool.mesh.setColorAt(count, this.fadeColor.setRGB(opacity, shape === 'trail' ? 1 - progress : opacity, opacity));
        this.transform.updateMatrix();
        pool.mesh.setMatrixAt(count++, this.transform.matrix);
      }
      pool.mesh.count = count;
      pool.mesh.instanceMatrix.needsUpdate = true;
      if (pool.mesh.instanceColor) pool.mesh.instanceColor.needsUpdate = true;
      alive += count;
    }
    this.renderer.render(this.world, this.camera);
    this.canvas.dataset['instances'] = String(alive);
    this.canvas.dataset['renderState'] = this.kind || alive ? 'running' : 'idle';
    this.frames++;
    if (now - this.sampleStarted > 1000) {
      const fps = this.frames * 1000 / (now - this.sampleStarted);
      this.canvas.dataset['fps'] = String(Math.round(fps));
      if (fps < 50 && this.renderer.getPixelRatio() > 1) this.renderer.setPixelRatio(1);
      this.sampleStarted = now; this.frames = 0;
    }
    if (alive || this.kind) this.frame = requestAnimationFrame(this.tick);
  };
}

/** Short, tapered blades with a fixed 32-instance ceiling. */
function grassGeometry(): BufferGeometry {
  const positions: number[] = [], colors: number[] = [], indices: number[] = [];
  const base = new Color('#4c7a32'), tip = new Color('#d2ec9e');
  for (let row = 0; row <= 5; row++) {
    const t = row / 5, width = .075 * (1 - t), bend = t * t * .25;
    for (let col = 0; col < 3; col++) {
      positions.push(bend + (col - 1) * width, t, Math.sin(t * 2) * .12 + (col === 1 ? .025 : 0));
      const color = base.clone().lerp(tip, t);
      colors.push(color.r, color.g, color.b);
    }
    if (row < 5) for (let col = 0; col < 2; col++) {
      const a = row * 3 + col;
      indices.push(a, a + 1, a + 3, a + 1, a + 4, a + 3);
    }
  }
  const geometry = new BufferGeometry();
  geometry.setAttribute('position', new Float32BufferAttribute(positions, 3));
  geometry.setAttribute('color', new Float32BufferAttribute(colors, 3));
  geometry.setIndex(indices); geometry.computeVertexNormals();
  return geometry;
}
