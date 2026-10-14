import { Component, ElementRef, effect, inject, viewChild } from '@angular/core';
import { Icon } from '../core/icon';
import { CardComponent } from './card';
import { Inspect } from './inspect';

@Component({
  selector: 'app-inspect-panel',
  imports: [CardComponent, Icon],
  template: `<dialog #dialog aria-label="Inspect card" (close)="inspect.close()" (click)="backdrop($event)">
    <button class="close quiet" aria-label="Close card" (click)="inspect.close()"><app-icon name="close" /></button>
    @if (inspect.card(); as card) { <app-card [cardId]="card.id" [hand]="card.hand ?? null" [unit]="card.unit ?? null" mode="inspect" /> }
  </dialog>`,
  styles: `dialog { width: fit-content; padding: 3rem .75rem .75rem; background: var(--obsidian-950); } .close { position: absolute; right: .5rem; top: .25rem; } app-card { margin: auto; }`,
})
export class InspectPanel {
  protected readonly inspect = inject(Inspect);
  private readonly dialog = viewChild.required<ElementRef<HTMLDialogElement>>('dialog');
  constructor() {
    effect(() => {
      const dialog = this.dialog().nativeElement;
      if (this.inspect.card()) { if (!dialog.open) dialog.showModal(); }
      else if (dialog.open) dialog.close();
    });
  }
  protected backdrop(event: MouseEvent): void {
    if (event.target === this.dialog().nativeElement) {
      const rect = this.dialog().nativeElement.getBoundingClientRect();
      if (event.clientX < rect.left || event.clientX > rect.right || event.clientY < rect.top || event.clientY > rect.bottom) this.inspect.close();
    }
  }
}
