import { inject } from '@angular/core';
import { Router, type CanMatchFn } from '@angular/router';
import { Auth } from './auth';

/**
 * Keeps the sign-in and sign-up pages for visitors only. Someone already
 * signed in has no use for the login form; they land on the camera instead.
 */
export const authGuard: CanMatchFn = () => {
  if (inject(Auth).isLoggedIn()) {
    return inject(Router).createUrlTree(['/capture']);
  }
  return true;
};
