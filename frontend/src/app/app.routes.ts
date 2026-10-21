import { Routes } from '@angular/router';
import { HomePage } from './home/home-page';
import { JoinPage } from './join/join-page';
import { devRoutes } from './core/dev-routes';

export const routes: Routes = [
  { path: '', component: HomePage, title: 'Shardbound' },
  { path: 'play', loadComponent: () => import('./play/play-page').then(m => m.PlayPage), title: 'Play · Shardbound' },
  { path: 'cards', loadComponent: () => import('./collection/collection-page').then(m => m.CollectionPage), title: 'Cards · Shardbound' },
  { path: 'lab', loadComponent: () => import('./lab/lab-page').then(m => m.LabPage), title: 'Lab · Shardbound' },
  { path: 'join/:gameId', component: JoinPage, title: 'Join a game · Shardbound' },
  { path: 'games/:gameId', loadComponent: () => import('./game/game-page').then(m => m.GamePage), title: 'Game · Shardbound' },
  ...devRoutes,
  { path: '**', redirectTo: '' },
];
