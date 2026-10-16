import { Component, inject } from '@angular/core';
import { Icon } from './icon';
import { Settings } from './settings';

@Component({
  selector: 'app-theme-toggle',
  imports: [Icon],
  template: `<button class="quiet" [attr.aria-label]="settings.theme() === 'dark' ? 'Switch to light mode' : 'Switch to dark mode'" [title]="settings.theme() === 'dark' ? 'Light mode' : 'Dark mode'" (click)="toggle()"><app-icon [name]="settings.theme() === 'dark' ? 'sun' : 'moon'" /></button>`,
  styles: `button { width: 44px; padding: .65rem; color: var(--muted); }`,
})
export class ThemeToggle {
  protected readonly settings = inject(Settings);
  protected toggle(): void { this.settings.update({ theme: this.settings.theme() === 'dark' ? 'light' : 'dark' }); }
}
