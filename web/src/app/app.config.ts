import {
  ApplicationConfig,
  isDevMode,
  provideBrowserGlobalErrorListeners,
  provideZoneChangeDetection,
} from '@angular/core';
import { provideRouter, withComponentInputBinding } from '@angular/router';
import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { provideServiceWorker } from '@angular/service-worker';

import { routes } from './app.routes';
import { Api } from './core/api';
import { apiKeyInterceptor } from './core/api-key.interceptor';
import { DemoApi } from './demo/demo-api';
import { isDemoMode } from './demo/demo-mode';

export const appConfig: ApplicationConfig = {
  providers: [
    provideBrowserGlobalErrorListeners(),
    provideZoneChangeDetection({ eventCoalescing: true }),
    provideRouter(routes, withComponentInputBinding()),
    provideHttpClient(withInterceptors([apiKeyInterceptor])),
    // Swapped at the root, so every screen keeps one code path and none of them
    // needs to know whether the log behind it is real.
    ...(isDemoMode() ? [{ provide: Api, useClass: DemoApi }] : []),
    provideServiceWorker('ngsw-worker.js', {
      enabled: !isDevMode(),
      registrationStrategy: 'registerWhenStable:30000',
    }),
  ],
};
