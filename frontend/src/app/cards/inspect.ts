import { Injectable, signal } from '@angular/core';
import { HandCardView, UnitView } from '../api/protocol';

export interface InspectedCard { id: string; hand?: HandCardView; unit?: UnitView; }
@Injectable({ providedIn: 'root' })
export class Inspect {
  readonly card = signal<InspectedCard | null>(null);
  readonly preview = signal<InspectedCard | null>(null);
  private owner: object | null = null;
  private timer?: ReturnType<typeof setTimeout>;
  previewAfter(owner: object, card: InspectedCard): void {
    this.clearPreview();
    if (this.card()) return;
    this.owner = owner;
    this.timer = setTimeout(() => this.preview.set(card), 380);
  }
  clearPreview(owner?: object): void {
    if (owner && owner !== this.owner) return;
    clearTimeout(this.timer); this.owner = null; this.preview.set(null);
  }
  open(card: InspectedCard): void { this.clearPreview(); this.card.set(card); }
  close(): void { this.clearPreview(); this.card.set(null); }
}
