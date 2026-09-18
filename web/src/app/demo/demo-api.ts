import { Injectable } from '@angular/core';
import { Observable, of, throwError } from 'rxjs';
import { Api } from '../core/api';
import type { DealsView, Observation, TagRule } from '../core/models';
import { DEMO_DEALS, DEMO_ENTRIES } from './demo-data';

/**
 * Serves the sample log instead of calling the API.
 *
 * <p>Substituted for {@link Api} when the app runs in demo mode, so every screen
 * keeps its own code path and nothing has to know where its data came from. It
 * reaches no network at all, which is also why the demo is instant: no key to
 * hold, nothing to leak, and none of the thirty-second cold start that a
 * scale-to-zero container makes a visitor sit through.
 */
@Injectable()
export class DemoApi extends Api {
  private readOnly<T>(): Observable<T> {
    return throwError(() => ({
      error: { error: 'This is a demo. Changes are not saved.' },
      status: 403,
    }));
  }

  override observations(): Observable<Observation[]> {
    return of(DEMO_ENTRIES);
  }

  override deals(): Observable<DealsView> {
    return of(DEMO_DEALS);
  }

  override tagRules(): Observable<TagRule[]> {
    return of([]);
  }

  // Writes are unreachable from the demo UI, but a stray call must not escape
  // to the real API with somebody else's data behind it.
  override capture(): Observable<Observation> {
    return this.readOnly();
  }

  override updateObservation(): Observable<Observation> {
    return this.readOnly();
  }

  override deleteObservation(): Observable<void> {
    return this.readOnly();
  }

  override updateTagRule(): Observable<TagRule> {
    return this.readOnly();
  }

  override photoBlob(): Observable<Blob> {
    return this.readOnly();
  }
}
