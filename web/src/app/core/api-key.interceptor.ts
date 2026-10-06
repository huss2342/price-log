import { HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { Auth } from './auth';
import { Settings } from './settings';

/**
 * Attaches credentials to every API call. A signed-in account wins: its JWT
 * goes out as a Bearer token. Otherwise the legacy shared key goes out as
 * X-API-Key, exactly as before. Demo mode carries neither, so nothing changes
 * there.
 */
export const apiKeyInterceptor: HttpInterceptorFn = (req, next) => {
  const token = inject(Auth).token();
  if (token) {
    return next(req.clone({ setHeaders: { Authorization: `Bearer ${token}` } }));
  }
  const key = inject(Settings).apiKey();
  if (!key) {
    return next(req);
  }
  return next(req.clone({ setHeaders: { 'X-API-Key': key } }));
};
