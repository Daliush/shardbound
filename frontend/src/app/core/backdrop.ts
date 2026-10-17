import { Component, inject } from '@angular/core';
import { Ambience } from './ambience';

@Component({
  selector: 'app-backdrop',
  template: '',
  host: { 'aria-hidden': 'true', '[attr.data-faction]': 'ambience.faction()' },
  styles: `:host { position: fixed; inset: 0; pointer-events: none; z-index: -1; background: var(--obsidian-950); }
    :host::after { content: ''; position: absolute; inset: 0; background: radial-gradient(ellipse at 15% 30%, color-mix(in srgb, var(--faction-deep) 20%, transparent), transparent 65%), radial-gradient(ellipse at 90% 95%, color-mix(in srgb, var(--faction) 10%, transparent), transparent 60%); }
    :host([data-faction='neutral'])::after { opacity: 0; }`,
})
export class Backdrop { protected readonly ambience = inject(Ambience); }


