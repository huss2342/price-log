import { Routes } from '@angular/router';
import { writerGuard } from './core/writer.guard';

export const routes: Routes = [
  { path: '', pathMatch: 'full', redirectTo: 'capture' },
  {
    path: 'capture',
    title: 'Snap a tag',
    canMatch: [writerGuard],
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
    path: 'deals',
    title: 'On sale now',
    loadComponent: () => import('./pages/deals/deals').then((m) => m.DealsPage),
  },
  {
    path: 'deals/published',
    title: "Costco's listing",
    loadComponent: () =>
      import('./pages/published-deals/published-deals').then((m) => m.PublishedDealsPage),
  },
  {
    path: 'review',
    title: 'Review queue',
    canMatch: [writerGuard],
    loadComponent: () => import('./pages/review/review').then((m) => m.ReviewPage),
  },
  {
    path: 'settings',
    title: 'Settings',
    loadComponent: () => import('./pages/settings/settings-page').then((m) => m.SettingsPage),
  },
  { path: '**', redirectTo: 'browse' },
];
