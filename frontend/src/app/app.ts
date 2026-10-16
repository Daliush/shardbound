import { Component, computed, inject } from '@angular/core';
import { NavigationEnd, Router, RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { toSignal } from '@angular/core/rxjs-interop';
import { filter, map } from 'rxjs';
import { Backdrop } from './core/backdrop';
import { Icon } from './core/icon';
import { SettingsPanel } from './core/settings-panel';
import { InspectPanel } from './cards/inspect-panel';
import { ThemeToggle } from './core/theme-toggle';

@Component({
  selector: 'app-root',
  imports: [RouterLink, RouterLinkActive, RouterOutlet, Backdrop, Icon, SettingsPanel, InspectPanel, ThemeToggle],
  template: `
    <app-backdrop />
    @if (!inGame()) {
      <header><a class="brand" routerLink="/" aria-label="Shardbound home"><app-icon name="neutral" /><span>Shardbound</span></a>
        <nav aria-label="Main navigation"><a routerLink="/play" routerLinkActive="active">Play</a><a routerLink="/lab" routerLinkActive="active">Lab</a></nav><div class="header-actions"><app-theme-toggle /><app-settings-panel /></div>
      </header>
    }
    <main [class.in-game]="inGame()"><router-outlet /></main>
    <app-inspect-panel />
  `,
  styleUrl: './app.css',
})
export class App {
  private readonly router = inject(Router);
  private readonly url = toSignal(this.router.events.pipe(filter(event => event instanceof NavigationEnd), map(() => this.router.url)), { initialValue: this.router.url });
  protected readonly inGame = computed(() => this.url().startsWith('/games/'));
}
