import { Component } from '@angular/core';
import { RouterLink, RouterOutlet } from '@angular/router';

@Component({
  selector: 'app-root',
  imports: [RouterLink, RouterOutlet],
  template: `
    <header><a routerLink="/">Shardbound</a> <span>test client</span></header>
    <main><router-outlet /></main>
  `,
  styleUrl: './app.css',
})
export class App {}
