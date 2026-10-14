import { Routes } from '@angular/router';
import { GamePage } from './game/game-page';
import { HomePage } from './home/home-page';
import { JoinPage } from './join/join-page';
import { devRoutes } from './core/dev-routes';

export const routes: Routes = [
  { path: '', component: HomePage, title: 'Shardbound' },
  { path: 'play', component: HomePage, title: 'Play · Shardbound' },
  { path: 'join/:gameId', component: JoinPage, title: 'Join a game · Shardbound' },
  { path: 'games/:gameId', component: GamePage, title: 'Game · Shardbound' },
  ...devRoutes,
  { path: '**', redirectTo: '' },
];
