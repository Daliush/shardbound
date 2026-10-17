import { Injectable, inject } from '@angular/core';
import { Settings } from '../../core/settings';

@Injectable()
export class Feedback {
  private readonly settings = inject(Settings);
  private audio?: AudioContext;
  unlock(): void {
    if (!this.settings.preferences().sound || typeof AudioContext === 'undefined') return;
    this.audio ??= new AudioContext();
    void this.audio.resume();
  }
  play(kind: string, amount = 0): void {
    if (amount >= 5 && this.settings.preferences().haptics && typeof matchMedia === 'function' && matchMedia('(pointer: coarse)').matches) navigator.vibrate?.(24);
    if (!this.settings.preferences().sound || this.audio?.state !== 'running') return;
    const oscillator = this.audio.createOscillator(), gain = this.audio.createGain();
    const now = this.audio.currentTime;
    oscillator.type = kind === 'damage' || kind === 'shatter' ? 'triangle' : 'sine';
    oscillator.frequency.setValueAtTime(kind === 'draw' ? 550 : kind === 'heal' ? 660 : 160, now);
    oscillator.frequency.exponentialRampToValueAtTime(kind === 'heal' ? 880 : 70, now + .15);
    gain.gain.setValueAtTime(.035, now);
    gain.gain.exponentialRampToValueAtTime(.001, now + .2);
    oscillator.connect(gain); gain.connect(this.audio.destination); oscillator.start(); oscillator.stop(now + .22);
  }
  close(): void { void this.audio?.close(); }
}
