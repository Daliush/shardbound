import { Injectable, signal } from '@angular/core';

@Injectable({ providedIn: 'root' })
export class Ambience {
  readonly faction = signal('neutral');
}
