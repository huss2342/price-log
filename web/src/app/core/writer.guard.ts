import { inject } from '@angular/core';
import { Router, type CanMatchFn } from '@angular/router';
import { Settings } from './settings';

/**
 * Keeps the write-only screens out of reach of a visitor with no key. The API
 * refuses them anyway; this stops the app from presenting a camera button that
 * can only ever return 401.
 */
export const writerGuard: CanMatchFn = () => {
  if (inject(Settings).canWrite()) {
    return true;
  }
  // Deals, not Browse: Browse opens empty until something is searched, which is
  // a poor first thing to see, while Deals has matches and a watchlist on it.
  return inject(Router).createUrlTree(['/deals']);
};
