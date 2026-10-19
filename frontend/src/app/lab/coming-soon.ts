import { Component, OnDestroy, signal } from '@angular/core';
import { Icon } from '../core/icon';

/** A short notice on arrival at the lab: the LoRA Arbiter's numbers are not in the benchmark yet. */
@Component({
  selector: 'app-coming-soon', imports: [Icon],
  host: { role: 'status', '[hidden]': '!open()' },
  template: `<app-icon name="lab" /><p><strong>LoRA metrics</strong> coming soon</p><button class="quiet" aria-label="Dismiss" (click)="open.set(false)"><app-icon name="close" /></button>`,
  styleUrl: './coming-soon.css',
})
export class ComingSoon implements OnDestroy {
  protected readonly open = signal(true);
  private readonly timer = setTimeout(() => this.open.set(false), 6000);
  ngOnDestroy(): void { clearTimeout(this.timer); }
}
