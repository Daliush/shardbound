import { Injectable } from '@angular/core';

/** Seats survive reloads where storage is available; memory keeps private sessions playable. */
@Injectable({ providedIn: 'root' })
export class SeatStore {
  private readonly memory = new Map<string, string>();
  token(gameId: string): string | null { return this.read(gameId, 'token'); }
  joinCode(gameId: string): string | null { return this.read(gameId, 'joinCode'); }
  saveToken(gameId: string, token: string): void { this.write(gameId, 'token', token); }
  saveJoinCode(gameId: string, code: string): void { this.write(gameId, 'joinCode', code); }
  private read(game: string, field: string): string | null {
    const key = 'shardbound.game.' + game + '.' + field;
    try { return localStorage.getItem(key) ?? this.memory.get(key) ?? null; }
    catch { return this.memory.get(key) ?? null; }
  }
  private write(game: string, field: string, value: string): void {
    const key = 'shardbound.game.' + game + '.' + field;
    this.memory.set(key, value);
    try { localStorage.setItem(key, value); } catch { /* The current session remains usable. */ }
  }
}
export function inviteLink(gameId: string, joinCode: string): string {
  return location.origin + '/join/' + gameId + '?code=' + encodeURIComponent(joinCode);
}

