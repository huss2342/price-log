import { inject } from '@angular/core';
import { Router, type CanMatchFn } from '@angular/router';
import { Auth } from './auth';
import { Settings } from './settings';

/**
 * Keeps the write-only screens out of reach of a visitor with no credentials.
 * A signed-in account counts just like the legacy shared key. The API refuses
 * them anyway; this stops the app from presenting a camera button that can
 * only ever return 401.
 */
export const writerGuard: CanMatchFn = () => {
  if (inject(Auth).isLoggedIn() || inject(Settings).canWrite()) {
    return true;
  }
  // Browse lists the whole log straight away, so it is the natural first screen.
  return inject(Router).createUrlTree(['/browse']);
};
