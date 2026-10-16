import { Component, computed, input } from '@angular/core';

const PATHS: Record<string, string> = {
  sun: 'M12 8a4 4 0 1 0 0 8 4 4 0 0 0 0-8 M12 2v2 M12 20v2 M2 12h2 M20 12h2 M5 5l1.5 1.5 M17.5 17.5 19 19 M5 19l1.5-1.5 M17.5 6.5 19 5',
  moon: 'M20.5 14.5A9 9 0 0 1 9.5 3.5a7 7 0 1 0 11 11Z',
  chart: 'M4 3v17h17 M8 15l4-5 4 2 5-7',
  ranking: 'M4 17h3v4H4Z M10 10h3v11h-3Z M16 4h3v17h-3Z',
  compare: 'M3 7h17 M16 3l4 4-4 4 M21 17H4 M8 13l-4 4 4 4',
  build: 'M4 5h16 M4 12h16 M4 19h16 M8 2v6 M16 9v6 M10 16v6',
  search: 'M10 3a7 7 0 1 0 0 14 7 7 0 0 0 0-14 M15 15l6 6',
  info: 'M12 3a9 9 0 1 0 0 18 9 9 0 0 0 0-18 M12 11v5 M12 7v.1',
  shard: 'M12 2 20 9 16 20 8 22 3 12Z M12 2 8 22 M3 12 20 9 M12 2 16 20',
  sword: 'm4 20 8-8 M3 15l6 6 M10 10l7-7h4v4l-7 7Z',
  shield: 'M12 2 21 6v7c0 5-9 9-9 9S3 18 3 13V6Z M12 6v11',
  heart: 'M12 21 3 12V6l4-3 5 3 5-3 4 3v6Z M12 6v15 M3 12h18',
  frozen: 'M12 2v20 M3 7l18 10 M3 17 21 7 M9 4l3 3 3-3 M9 20l3-3 3 3 M3 10l4-1-1-4 M18 19l-1-4 4-1',
  anchor: 'M12 8v13 M7 12h10 M3 14v3c0 6 18 6 18 0v-3 M9 5a3 3 0 1 0 6 0 3 3 0 0 0-6 0',
  link: 'm9 15 6-6 M9 17l-2 2a4 4 0 0 1-6-6l5-5a4 4 0 0 1 6 0 M15 7l2-2a4 4 0 0 1 6 6l-5 5a4 4 0 0 1-6 0',
  doom: 'M9 3c-2 5-6 6-6 11a9 9 0 0 0 18 0c0-4-4-8-4-8l-2 5-3-9-3 8 M8 14h1 M15 14h1 M9 18h6',
  echo: 'M6 9a4 4 0 0 1 0 8 M10 6a8 8 0 0 1 0 14 M14 3a12 12 0 0 1 0 20',
  fracture: 'M4 3h16v18H4Z M13 3l-4 7 6 2-5 9',
  lock: 'M7 10V7a5 5 0 0 1 10 0v3 M5 10h14v11H5Z M12 14v3',
  sacrifice: 'M4 20h13l4-6-2-2-4 5H9 M4 15h6l2 2 M11 11C5 9 10 4 12 2c0 4 6 6 4 9Z',
  deck: 'M7 3h13v16 M4 6h13v16H4Z M9 10l4 4-4 4-3-4Z',
  graveyard: 'M4 21h16 M7 21V8a5 5 0 0 1 10 0v13 M12 7v9 M9 11h6',
  hourglass: 'M5 3h14 M5 21h14 M7 3v4l10 10v4 M17 3v4L7 17v4',
  ember: 'M12 1 23 12 12 23 1 12Z M12 5c0 5-5 5-5 9a5 5 0 0 0 10 0c0-3-2-4-2-4l-1 4Z',
  tide: 'M21 12a9 9 0 1 1-9-9 M3 15c5 3 7-3 11-3s4 4 8 0 M16 3c-6 5-5 8 1 7',
  root: 'M12 2v11 M6 5l6 8 6-8 M12 10l-5 4-3 7 M12 10l5 4 3 7 M12 13v9 M7 14v5 M17 14v5',
  neutral: 'M12 2 22 11 12 22 2 11Z M12 2v20 M2 11h20 M7 7l5 10 5-10',
  settings: 'M4 6h16 M4 12h16 M4 18h16 M8 3v6 M16 9v6 M10 15v6',
  close: 'm6 6 12 12 M6 18 18 6',
  chevron: 'm8 4 8 8-8 8',
  check: 'm4 12 5 5L20 6',
  eye: 'M2 12S6 5 12 5s10 7 10 7-4 7-10 7S2 12 2 12 M9 12a3 3 0 1 0 6 0 3 3 0 0 0-6 0',
  lab: 'M9 2h6 M10 2v7L4 19a2 2 0 0 0 2 3h12a2 2 0 0 0 2-3L14 9V2 M7 15h10',
  menu: 'M4 6h16 M4 12h16 M4 18h16',
  volume: 'M3 9h4l5-5v16l-5-5H3Z M16 8a6 6 0 0 1 0 8 M19 4a11 11 0 0 1 0 16',
  pin: 'M7 3h10l-2 7 4 4H5l4-4Z M12 14v8',
  arrow: 'M3 12h17 M14 5l7 7-7 7',
};

@Component({
  selector: 'app-icon',
  template: `<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path [attr.d]="path()" /></svg>`,
  styles: `:host { display: inline-flex; width: 1.25em; height: 1.25em; flex: 0 0 auto; vertical-align: middle; } svg { width: 100%; height: 100%; overflow: visible; }`,
})
export class Icon {
  readonly name = input('shard');
  protected readonly path = computed(() => PATHS[this.name()] ?? PATHS['shard']);
}
