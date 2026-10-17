import { Component, ElementRef, inject, signal, viewChild } from '@angular/core';
import { Icon } from './icon';
import { Preferences, Settings } from './settings';

@Component({
  selector: 'app-settings-panel',
  imports: [Icon],
  template: `
    <button class="quiet" aria-label="Settings" (click)="open()"><app-icon name="settings" /></button>
    <dialog #dialog aria-labelledby="settings-title">
      <div class="dialog-heading"><h2 id="settings-title">Settings</h2><button class="quiet" aria-label="Close settings" (click)="dialog.close()"><app-icon name="close" /></button></div>
      <label>Appearance<select [value]="settings.preferences().theme" (change)="theme($event)"><option value="system">Follow your device</option><option value="light">Light</option><option value="dark">Dark</option></select></label>
      <label>Animation speed<select [value]="settings.preferences().speed" (change)="speed($event)"><option value="0.5">Slow · time to read</option><option value="1">Normal · cinematic</option><option value="2">Fast</option><option value="0">Instant</option></select></label>
      <label>Reduced motion<select [value]="settings.preferences().motion" (change)="motion($event)"><option value="system">Follow your device</option><option value="on">On</option><option value="off">Off</option></select></label>
      <label><span>Sound<small>Quiet, synthesized card and impact sounds</small></span><input type="checkbox" role="switch" [checked]="settings.preferences().sound" (change)="toggle('sound', $event)" /></label>
      <label>Haptics<input type="checkbox" role="switch" [checked]="settings.preferences().haptics" (change)="toggle('haptics', $event)" /></label>
      <label><span>Gyroscope tilt<small>Uses your device’s motion sensor</small></span><input type="checkbox" role="switch" [checked]="settings.preferences().gyroscope" (change)="gyro($event)" /></label>
      @if (sensorMessage()) { <p role="status" class="muted">{{ sensorMessage() }}</p> }
      <label>Rule IDs in the Chronicle<input type="checkbox" role="switch" [checked]="settings.preferences().ruleIds" (change)="toggle('ruleIds', $event)" /></label>
    </dialog>
  `,
  styles: `label { display: flex; align-items: center; justify-content: space-between; gap: 1rem; min-height: 60px; padding: .6rem 0; border-bottom: 1px solid var(--line); } small { display: block; margin-top: .25rem; max-width: 30ch; } select { max-width: 58%; }`,
})
export class SettingsPanel {
  protected readonly settings = inject(Settings);
  private readonly dialog = viewChild.required<ElementRef<HTMLDialogElement>>('dialog');
  protected readonly sensorMessage = signal('');
  open(): void { this.dialog().nativeElement.showModal(); }
  protected theme(event: Event): void { this.settings.update({ theme: (event.target as HTMLSelectElement).value as Preferences['theme'] }); }
  protected speed(event: Event): void { this.settings.update({ speed: Number((event.target as HTMLSelectElement).value) as Preferences['speed'] }); }
  protected motion(event: Event): void { this.settings.update({ motion: (event.target as HTMLSelectElement).value as Preferences['motion'] }); }
  protected toggle(key: 'sound' | 'haptics' | 'ruleIds', event: Event): void { this.settings.update({ [key]: (event.target as HTMLInputElement).checked }); }
  protected async gyro(event: Event): Promise<void> {
    const checkbox = event.target as HTMLInputElement;
    if (!checkbox.checked) { this.settings.update({ gyroscope: false }); return; }
    if (!await this.settings.enableGyroscope()) { checkbox.checked = false; this.sensorMessage.set('Motion sensing is unavailable or permission was declined.'); }
  }
}
