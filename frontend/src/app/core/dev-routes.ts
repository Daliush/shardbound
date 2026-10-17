import { Routes } from '@angular/router';

export const devRoutes: Routes = [
  { path: 'dev/stage', loadComponent: () => import('../arena/stage/sandbox').then(module => module.Sandbox), title: 'Stage · Shardbound' },
];
