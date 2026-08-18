import { Routes } from '@angular/router';

export const routes: Routes = [
  { path: '', pathMatch: 'full', redirectTo: 'capture' },
  {
    path: 'capture',
    title: 'Snap a tag',
    loadComponent: () => import('./pages/capture/capture').then((m) => m.CapturePage),
  },
  {
    path: 'browse',
    title: 'Browse prices',
    loadComponent: () => import('./pages/browse/browse').then((m) => m.BrowsePage),
  },
  {
    path: 'group/:key',
    title: 'Price comparison',
    loadComponent: () => import('./pages/group/group').then((m) => m.GroupPage),
  },
  {
    path: 'review',
    title: 'Review queue',
    loadComponent: () => import('./pages/review/review').then((m) => m.ReviewPage),
  },
  {
    path: 'settings',
    title: 'Settings',
    loadComponent: () => import('./pages/settings/settings-page').then((m) => m.SettingsPage),
  },
  { path: '**', redirectTo: 'capture' },
];
