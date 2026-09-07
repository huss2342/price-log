import { Injectable } from '@angular/core';
import { Observable, of, throwError } from 'rxjs';
import { delay } from 'rxjs/operators';
import { Api } from '../core/api';
import type {
  BrowsedDeal,
  CompareGroup,
  DealsView,
  Observation,
  SaleCycle,
  Store,
  StoreOffer,
  TagRule,
  WatchedItem,
} from '../core/models';
import {
  DEMO_DEALS,
  DEMO_GROUPS,
  DEMO_PUBLISHED_DEALS,
  DEMO_STORES,
  DEMO_WATCHLIST,
} from './demo-data';

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
  /** Enough latency that lists do not snap in jarringly, far short of a wait. */
  private static readonly LATENCY_MS = 120;

  private respond<T>(value: T): Observable<T> {
    return of(value).pipe(delay(DemoApi.LATENCY_MS));
  }

  private readOnly<T>(): Observable<T> {
    return throwError(() => ({
      error: { error: 'This is a demo. Changes are not saved.' },
      status: 403,
    }));
  }

  override stores(): Observable<Store[]> {
    return this.respond(DEMO_STORES);
  }

  override search(query: string): Observable<CompareGroup[]> {
    const needle = query.trim().toLowerCase();
    if (!needle) {
      return this.respond([]);
    }
    return this.respond(
      DEMO_GROUPS.filter(
        (g) =>
          g.label.toLowerCase().includes(needle) ||
          g.offers.some((o) => o.productName.toLowerCase().includes(needle)),
      ),
    );
  }

  override byCategory(category: string): Observable<CompareGroup[]> {
    return this.respond(DEMO_GROUPS.filter((g) => g.category === category));
  }

  override group(comparisonKey: string): Observable<CompareGroup> {
    const found = DEMO_GROUPS.find((g) => g.comparisonKey === comparisonKey);
    return found
      ? this.respond(found)
      : throwError(() => ({ error: { error: 'No such group.' }, status: 404 }));
  }

  override history(productId: number): Observable<StoreOffer[]> {
    return this.respond(
      DEMO_GROUPS.flatMap((g) => g.offers).filter((o) => o.productId === productId),
    );
  }

  override deals(): Observable<DealsView> {
    return this.respond(DEMO_DEALS);
  }

  override publishedDeals(query: string, warehouseOnly: boolean): Observable<BrowsedDeal[]> {
    const needle = query.trim().toLowerCase();
    return this.respond(
      DEMO_PUBLISHED_DEALS.filter((d) => !warehouseOnly || d.inWarehouse).filter(
        (d) =>
          !needle ||
          d.title.toLowerCase().includes(needle) ||
          (d.itemNumber ?? '').includes(needle),
      ),
    );
  }

  override watchlist(): Observable<WatchedItem[]> {
    return this.respond(DEMO_WATCHLIST);
  }

  override pending(): Observable<Observation[]> {
    return this.respond([]);
  }

  override recent(): Observable<Observation[]> {
    return this.respond([]);
  }

  override tagRules(): Observable<TagRule[]> {
    return this.respond([]);
  }

  override saleCycle(): Observable<SaleCycle> {
    return this.respond(DEMO_WATCHLIST[0].cycle);
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

  override createStore(): Observable<Store> {
    return this.readOnly();
  }

  override updateStore(): Observable<Store> {
    return this.readOnly();
  }

  override deleteStore(): Observable<void> {
    return this.readOnly();
  }

  override updateTagRule(): Observable<TagRule> {
    return this.readOnly();
  }

  override setWatch(): Observable<{
    productId: number;
    watched: boolean;
    targetPriceCents: number | null;
  }> {
    return this.readOnly();
  }

  override photoBlob(): Observable<Blob> {
    return this.readOnly();
  }
}
