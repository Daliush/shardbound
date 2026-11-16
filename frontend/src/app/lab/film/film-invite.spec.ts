import { TestBed } from '@angular/core/testing';
import { FilmInvite } from './film-invite';

describe('The offer of the film', () => {
  it('asks a first visitor and says which answer they gave', () => {
    const fixture = TestBed.createComponent(FilmInvite), answers: string[] = [];
    fixture.componentInstance.watched.subscribe(() => answers.push('watched'));
    fixture.componentInstance.declined.subscribe(() => answers.push('declined'));
    fixture.detectChanges();
    const host = fixture.nativeElement as HTMLElement, [watch,decline] = host.querySelectorAll('button');
    expect(host.textContent).toContain('First time here?');
    watch.click(); decline.click();
    expect([watch.textContent?.trim(),decline.textContent?.trim(),answers]).toEqual(['Watch the film','Not now',['watched','declined']]);
  });
});
