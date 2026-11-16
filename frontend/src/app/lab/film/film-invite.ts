import { Component, output } from '@angular/core';
import { Icon } from '../../core/icon';

/** On a first visit, the offer of the film that explains a pipeline, which the visitor plays or declines. */
@Component({
  selector: 'app-film-invite', imports: [Icon],
  host: { role: 'region', 'aria-label': 'First visit' },
  template: `<p><strong>First time here?</strong> A short film shows how a pipeline finds the rules.</p><div class="actions"><button class="primary" (click)="watched.emit()"><app-icon name="play" />Watch the film</button><button class="quiet" (click)="declined.emit()">Not now</button></div>`,
  styleUrl: './film-invite.css',
})
export class FilmInvite {
  readonly watched = output<void>();
  readonly declined = output<void>();
}
