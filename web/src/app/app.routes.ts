import { Routes } from '@angular/router';
import { authGuard } from './core/auth.guard';
import { writerGuard } from './core/writer.guard';

export const routes: Routes = [
  { path: '', pathMatch: 'full', redirectTo: 'capture' },
  {
    path: 'login',
    title: 'Log in',
    canMatch: [authGuard],
    loadComponent: () => import('./pages/auth/login').then((m) => m.LoginPage),
  },
  {
    path: 'register',
    title: 'Sign up',
    canMatch: [authGuard],
    loadComponent: () => import('./pages/auth/register').then((m) => m.RegisterPage),
  },
  {
    path: 'capture',
    title: 'Snap',
    canMatch: [writerGuard],
    loadComponent: () => import('./pages/capture/capture').then((m) => m.CapturePage),
  },
  {
    path: 'browse',
    title: 'Browse',
    loadComponent: () => import('./pages/browse/browse').then((m) => m.BrowsePage),
  },
  {
    path: 'item/:id',
    title: 'Item',
    loadComponent: () => import('./pages/item/item').then((m) => m.ItemPage),
  },
  {
    path: 'deals',
    title: 'Deals',
    loadComponent: () => import('./pages/deals/deals').then((m) => m.DealsPage),
  },
  {
    path: 'entries',
    title: 'Entries',
    canMatch: [writerGuard],
    loadComponent: () => import('./pages/entries/entries').then((m) => m.EntriesPage),
  },
  {
    path: 'stores',
    title: 'Stores',
    canMatch: [writerGuard],
    loadComponent: () => import('./pages/stores/stores').then((m) => m.StoresPage),
  },
  {
    path: 'settings',
    title: 'Settings',
    loadComponent: () => import('./pages/settings/settings-page').then((m) => m.SettingsPage),
  },
  // Old addresses a home-screen app may still open on.
  { path: 'review', redirectTo: 'entries' },
  { path: 'group/:key', redirectTo: 'browse' },
  { path: 'deals/published', redirectTo: 'deals' },
  { path: '**', redirectTo: 'browse' },
];
