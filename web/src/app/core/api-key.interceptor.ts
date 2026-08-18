import { HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { Settings } from './settings';

/** Attaches the shared secret to every API call. */
export const apiKeyInterceptor: HttpInterceptorFn = (req, next) => {
  const key = inject(Settings).apiKey();
  if (!key) {
    return next(req);
  }
  return next(req.clone({ setHeaders: { 'X-API-Key': key } }));
};
