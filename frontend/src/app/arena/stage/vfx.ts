import { hashCard } from '../../cards/procedural-art';

interface Fragment { points: [number, number][]; cx: number; cy: number; vx: number; vy: number; spin: number; }
interface Mote { x:number; y:number; vx:number; vy:number; size:number; }
export class VfxCanvas {
  private frame = 0;
  private readonly motes: Mote[] = Array.from({length:48},() => ({x:0,y:0,vx:0,vy:0,size:0}));
  constructor(private readonly canvas: HTMLCanvasElement) {}
  clear(): void {
    cancelAnimationFrame(this.frame);
    this.canvas.getContext('2d')?.clearRect(0, 0, this.canvas.width, this.canvas.height);
  }
  burst(origin: {x:number;y:number}, color: string, preset: string, pace = 3): void {
    this.clear();
    const context = this.canvas.getContext('2d');
    if (!context) return;
    const dpr = Math.min(devicePixelRatio || 1,2);
    this.canvas.width = innerWidth*dpr; this.canvas.height = innerHeight*dpr; context.scale(dpr,dpr);
    const count = innerWidth < 700 ? 22 : 40, rising = preset === 'heal' || preset === 'root';
    for (let i=0;i<count;i++) {
      const angle = i/count*Math.PI*2, mote = this.motes[i];
      mote.x = origin.x; mote.y = origin.y; mote.vx = Math.cos(angle)*(30+i%5*11); mote.vy = rising ? -45-i%7*10 : Math.sin(angle)*65; mote.size=1.5+i%3;
    }
    const start = performance.now();
    const draw = (now:number) => {
      if (document.hidden) { this.clear(); return; }
      const t=(now-start)/1000/pace;
      context.clearRect(0,0,innerWidth,innerHeight); context.globalCompositeOperation='lighter'; context.fillStyle=color; context.globalAlpha=Math.max(0,1-t/.7);
      for (let i=0;i<count;i++) {
        const p=this.motes[i]; context.save(); context.translate(p.x+p.vx*t,p.y+p.vy*t+(rising?0:35*t*t)); context.rotate(t+i);
        if (preset === 'tide' || preset === 'heal') { context.beginPath(); context.ellipse(0,0,p.size,p.size*1.6,0,0,Math.PI*2); context.fill(); }
        else if (preset === 'root') { context.beginPath(); context.ellipse(0,0,p.size*2,p.size,0,0,Math.PI*2); context.fill(); }
        else context.fillRect(-p.size/2,-p.size/2,p.size,preset === 'ember'?p.size*2.5:p.size);
        context.restore();
      }
      if(t<.7) this.frame=requestAnimationFrame(draw); else this.clear();
    };
    this.frame=requestAnimationFrame(draw);
  }
  shatter(element: HTMLElement, color: string, scale = 1, pace = 3): void {
    this.clear();
    const context = this.canvas.getContext('2d');
    if (!context) return;
    const bounds = element.getBoundingClientRect();
    const dpr = Math.min(devicePixelRatio || 1, 2);
    this.canvas.width = innerWidth * dpr;
    this.canvas.height = innerHeight * dpr;
    context.scale(dpr, dpr);
    const texture = document.createElement('canvas');
    texture.width = Math.max(1, Math.ceil(bounds.width)); texture.height = Math.max(1, Math.ceil(bounds.height));
    const paint = texture.getContext('2d')!;
    paint.fillStyle = color; paint.fillRect(0, 0, texture.width, texture.height);
    const image = element.querySelector('img');
    if (image?.complete && image.naturalWidth) paint.drawImage(image, 3, 3, texture.width - 6, texture.height - 6);
    let state = hashCard(element.dataset['anchor'] ?? 'shatter');
    const random = () => { state = (Math.imul(state, 1664525) + 1013904223) >>> 0; return state / 4294967296; };
    const columns = scale > 1 ? (innerWidth < 700 ? 8 : 10) : 5, rows = scale > 1 ? (innerWidth < 700 ? 8 : 10) : 4;
    const mesh: [number, number][][] = [];
    for (let y = 0; y <= rows; y++) {
      mesh[y] = [];
      for (let x = 0; x <= columns; x++) mesh[y][x] = [(x + (x > 0 && x < columns ? random() * .5 - .25 : 0)) / columns * bounds.width, (y + (y > 0 && y < rows ? random() * .5 - .25 : 0)) / rows * bounds.height];
    }
    const fragments: Fragment[] = [];
    for (let y = 0; y < rows; y++) for (let x = 0; x < columns; x++) {
      for (const points of [[mesh[y][x], mesh[y][x + 1], mesh[y + 1][x]], [mesh[y + 1][x], mesh[y][x + 1], mesh[y + 1][x + 1]]]) {
        const cx = points.reduce((sum, point) => sum + point[0], 0) / 3, cy = points.reduce((sum, point) => sum + point[1], 0) / 3;
        fragments.push({ points, cx, cy, vx: (cx / bounds.width - .5) * 380 * scale + (random() - .5) * 100, vy: (cy / bounds.height - .8) * 200 * scale, spin: (random() - .5) * 8 });
      }
    }
    const start = performance.now();
    const draw = (now: number) => {
      if (document.hidden) { this.clear(); return; }
      const time = (now - start) / 1000 / pace;
      context.clearRect(0, 0, innerWidth, innerHeight);
      for (const fragment of fragments) {
        context.save();
        context.globalAlpha = Math.max(0, 1 - time / 1.2);
        context.translate(bounds.left + fragment.cx + fragment.vx * time, bounds.top + fragment.cy + fragment.vy * time + 150 * time * time);
        context.rotate(fragment.spin * time);
        context.beginPath();
        fragment.points.forEach((point, index) => index ? context.lineTo(point[0] - fragment.cx, point[1] - fragment.cy) : context.moveTo(point[0] - fragment.cx, point[1] - fragment.cy));
        context.closePath(); context.clip();
        context.drawImage(texture, -fragment.cx, -fragment.cy);
        context.strokeStyle = color; context.lineWidth = 1.5; context.stroke();
        context.restore();
      }
      if (time < 1.2) this.frame = requestAnimationFrame(draw);
      else context.clearRect(0, 0, innerWidth, innerHeight);
    };
    this.frame = requestAnimationFrame(draw);
  }
}

