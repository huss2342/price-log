import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { Settings } from './settings';
import type {
  CompareGroup,
  DealsView,
  SaleCycle,
  WatchedItem,
  Observation,
  ObservationUpdate,
  Store,
  StoreOffer,
  TagRule,
} from './models';

@Injectable({ providedIn: 'root' })
export class Api {
  private readonly http = inject(HttpClient);
  private readonly settings = inject(Settings);

  private url(path: string): string {
    return `${this.settings.apiBase().replace(/\/+$/, '')}${path}`;
  }

  capture(photo: File, storeId: number | null): Observable<Observation> {
    const form = new FormData();
    form.append('photo', photo, photo.name || 'tag.jpg');
    let params = new HttpParams();
    if (storeId != null) {
      params = params.set('storeId', String(storeId));
    }
    return this.http.post<Observation>(this.url('/api/captures'), form, { params });
  }

  search(query: string): Observable<CompareGroup[]> {
    return this.http.get<CompareGroup[]>(this.url('/api/search'), {
      params: new HttpParams().set('q', query),
    });
  }

  byCategory(category: string): Observable<CompareGroup[]> {
    return this.http.get<CompareGroup[]>(this.url(`/api/categories/${category}`));
  }

  group(comparisonKey: string): Observable<CompareGroup> {
    return this.http.get<CompareGroup>(this.url(`/api/groups/${encodeURIComponent(comparisonKey)}`));
  }

  history(productId: number): Observable<StoreOffer[]> {
    return this.http.get<StoreOffer[]>(this.url(`/api/products/${productId}/history`));
  }

  pending(): Observable<Observation[]> {
    return this.http.get<Observation[]>(this.url('/api/observations/pending'));
  }

  recent(limit = 50): Observable<Observation[]> {
    return this.http.get<Observation[]>(this.url('/api/observations/recent'), {
      params: new HttpParams().set('limit', String(limit)),
    });
  }

  updateObservation(id: number, update: ObservationUpdate): Observable<Observation> {
    return this.http.put<Observation>(this.url(`/api/observations/${id}`), update);
  }

  deleteObservation(id: number): Observable<void> {
    return this.http.delete<void>(this.url(`/api/observations/${id}`));
  }

  stores(): Observable<Store[]> {
    return this.http.get<Store[]>(this.url('/api/stores'));
  }

  createStore(store: Partial<Store>): Observable<Store> {
    return this.http.post<Store>(this.url('/api/stores'), store);
  }

  updateStore(id: number, store: Partial<Store>): Observable<Store> {
    return this.http.put<Store>(this.url(`/api/stores/${id}`), store);
  }

  deleteStore(id: number): Observable<void> {
    return this.http.delete<void>(this.url(`/api/stores/${id}`));
  }

  /** @param force re-read Costco's listing even if the cached copy is fresh */
  deals(force = false): Observable<DealsView> {
    return this.http.get<DealsView>(this.url('/api/deals'), {
      params: new HttpParams().set('force', String(force)),
    });
  }

  watchlist(): Observable<WatchedItem[]> {
    return this.http.get<WatchedItem[]>(this.url('/api/watchlist'));
  }

  saleCycle(productId: number): Observable<SaleCycle> {
    return this.http.get<SaleCycle>(this.url(`/api/products/${productId}/cycle`));
  }

  setWatch(
    productId: number,
    body: { watched?: boolean; targetPriceCents?: number | null; clearTarget?: boolean },
  ): Observable<{ productId: number; watched: boolean; targetPriceCents: number | null }> {
    return this.http.put<{ productId: number; watched: boolean; targetPriceCents: number | null }>(
      this.url(`/api/products/${productId}/watch`),
      body,
    );
  }

  tagRules(): Observable<TagRule[]> {
    return this.http.get<TagRule[]>(this.url('/api/tag-rules'));
  }

  updateTagRule(id: number, update: Partial<TagRule>): Observable<TagRule> {
    return this.http.put<TagRule>(this.url(`/api/tag-rules/${id}`), update);
  }

  /**
   * Photos are served from a private container behind the API key, and an
   * <img> tag cannot send headers, so the key rides as a query parameter.
   */
  photoUrl(key: string): string {
    const base = this.url(`/api/photos/${key}`);
    const apiKey = this.settings.apiKey();
    return apiKey ? `${base}?apiKey=${encodeURIComponent(apiKey)}` : base;
  }
}
