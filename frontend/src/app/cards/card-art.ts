import { Injectable, inject } from '@angular/core';
import { CardBook } from '../game/card-book';
import { ArtPalette, drawPlaceholder } from './procedural-art';

/** A card's art: the image the server gives the address of, or else a placeholder drawn from the card's id. */
@Injectable({ providedIn: 'root' })
export class CardArt {
  private readonly book = inject(CardBook);
  private readonly placeholders = new Map<string, string>();
  /** Empty until the cards are loaded, so a card never flashes its placeholder before its art. */
  source(id: string, faction: string): string {
    if (!this.book.loaded()) return '';
    return this.book.card(id)?.art ?? this.placeholder(id, faction);
  }
  placeholder(id: string, faction: string): string {
    if (!this.placeholders.has(id)) {
      this.placeholders.set(id, 'data:image/svg+xml;charset=utf-8,' + encodeURIComponent(drawPlaceholder(id, this.palette(faction))));
    }
    return this.placeholders.get(id)!;
  }
  palette(faction: string): ArtPalette {
    const styles = getComputedStyle(document.documentElement);
    const family = ['ember', 'tide', 'root'].includes(faction) ? faction : 'prism';
    const token = (name: string) => styles.getPropertyValue('--' + name).trim() || 'currentColor';
    return { light: token(family + '-light'), mid: token(family), dark: token(family + '-deep'), ink: token('obsidian-950') };
  }
}
