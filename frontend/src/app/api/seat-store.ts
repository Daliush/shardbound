import { Injectable } from '@angular/core';

/**
 * Seat tokens and invite codes, kept across reloads (spec §14). Every tab of a browser shares them: to play
 * both seats on one machine, use a second browser or a private window.
 */
@Injectable({ providedIn: 'root' })
export class SeatStore {
  token(gameId: string): string | null {
    return localStorage.getItem(key(gameId, 'token'));
  }

  saveToken(gameId: string, token: string): void {
    localStorage.setItem(key(gameId, 'token'), token);
  }

  joinCode(gameId: string): string | null {
    return localStorage.getItem(key(gameId, 'joinCode'));
  }

  saveJoinCode(gameId: string, code: string): void {
    localStorage.setItem(key(gameId, 'joinCode'), code);
  }
}

function key(gameId: string, field: string): string {
  return `shardbound.game.${gameId}.${field}`;
}

/** Where the second player joins a game. */
export function inviteLink(gameId: string, joinCode: string): string {
  return `${location.origin}/join/${gameId}?code=${encodeURIComponent(joinCode)}`;
}
