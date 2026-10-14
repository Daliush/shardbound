import { Injectable, signal } from '@angular/core';
import { HandCardView, UnitView } from '../api/protocol';

export interface InspectedCard { id: string; hand?: HandCardView; unit?: UnitView; }
@Injectable({ providedIn: 'root' })
export class Inspect {
  readonly card = signal<InspectedCard | null>(null);
  open(card: InspectedCard): void { this.card.set(card); }
  close(): void { this.card.set(null); }
}
