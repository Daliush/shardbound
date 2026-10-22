import { Component, computed, inject } from '@angular/core';
import { Icon } from './icon';
import { ServerWake } from './server-wake';

/** Says, at the top of every page, that the server is starting up, and why that is fine. */
@Component({
  selector: 'app-server-wake-banner',
  imports: [Icon],
  template: `
    <div role="status" aria-live="polite">
      @if (wake.state() !== 'idle') {
        <aside class="wake" [class.awake]="awake()" animate.enter="wake-enter" animate.leave="wake-leave">
          <span class="sigil" aria-hidden="true">
            <app-icon [name]="awake() ? 'check' : 'neutral'" />
            @if (!awake()) { <span class="z">z</span><span class="z">z</span><span class="z">z</span> }
          </span>
          <div class="copy">
            @if (awake()) {
              <p class="title">The server is up</p>
              <p class="line">Thanks for waiting!</p>
            } @else {
              <p class="title">Waking up the server<span class="seconds number" aria-hidden="true">{{ wake.waitedSeconds() }}s</span></p>
              <p class="line">{{ wake.takingLong() ? 'Taking longer than usual. Hang on, or try again in a minute.' : 'It naps when nobody plays, so hosting costs almost nothing 🙂 A few seconds more.' }}</p>
            }
          </div>
          <button class="quiet close" aria-label="Hide this message" (click)="wake.dismiss()"><app-icon name="close" /></button>
          <span class="progress" aria-hidden="true"></span>
        </aside>
      }
    </div>
  `,
  styleUrl: './server-wake-banner.css',
})
export class ServerWakeBanner {
  protected readonly wake = inject(ServerWake);
  protected readonly awake = computed(() => this.wake.state() === 'awake');
}
