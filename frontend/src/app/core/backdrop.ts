import { Component } from '@angular/core';

@Component({
  selector: 'app-backdrop',
  template: `<div class="vein one"></div><div class="vein two"></div><div class="light"></div>@for (mote of motes; track mote) { <i [style.--i]="mote"></i> }`,
  host: { 'aria-hidden': 'true' },
  styles: `
    :host { position: fixed; inset: 0; pointer-events: none; z-index: -1; overflow: hidden; background: var(--obsidian-950); }
    .light { position: absolute; inset: 0; background: radial-gradient(ellipse at 20% 50%, color-mix(in srgb, var(--tide-deep) 20%, transparent), transparent 55%), radial-gradient(ellipse at 85% 25%, color-mix(in srgb, var(--ember-deep) 12%, transparent), transparent 45%); }
    .vein { position: absolute; width: 90vw; height: 60vh; border: 1px solid color-mix(in srgb, var(--prism) 8%, transparent); transform: rotate(-32deg) skewX(35deg); }
    .one { top: -32vh; left: -5vw; } .two { top: 58vh; left: 45vw; }
    i { position: absolute; left: calc(9% * var(--i)); top: calc(8% + 7% * var(--i)); width: 2px; height: 6px; background: var(--prism); opacity: .17; transform: rotate(35deg); animation: drift 14s ease-in-out infinite alternate; animation-delay: calc(var(--i) * -1.4s); }
    @keyframes drift { to { transform: translate3d(18px, -22px, 0) rotate(80deg); opacity: .35; } }
  `,
})
export class Backdrop { protected readonly motes = [1, 2, 3, 4, 5, 6, 7, 8, 9]; }
