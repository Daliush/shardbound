import { HttpClient } from '@angular/common/http';
import { Injectable, computed, inject } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { catchError, of } from 'rxjs';
import { ArtPalette, drawPlaceholder } from './procedural-art';

@Injectable({ providedIn: 'root' })
export class CardArt {
  private readonly manifest = toSignal(inject(HttpClient).get<Record<string, string>>('/cards/art/manifest.json').pipe(catchError(() => of({} as Record<string, string>))), { initialValue: {} as Record<string, string> });
  private readonly placeholders = new Map<string, string>();
  readonly entries = computed(() => this.manifest());
  source(id: string, faction: string): string {
    const file = this.manifest()[id];
    return file && /^[a-zA-Z0-9._-]+\.webp$/.test(file) ? '/cards/art/' + file : this.placeholder(id, faction);
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
