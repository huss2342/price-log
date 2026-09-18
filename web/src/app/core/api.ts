import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { Settings } from './settings';
import type {
  Chain,
  DealsView,
  Observation,
  ObservationUpdate,
  TagRule,
} from './models';

@Injectable({ providedIn: 'root' })
export class Api {
  private readonly http = inject(HttpClient);
  private readonly settings = inject(Settings);

  private url(path: string): string {
    return `${this.settings.apiBase().replace(/\/+$/, '')}${path}`;
  }

  /** @param chain where the photo was taken, or null to let the tag's design say */
  capture(photo: File, chain: Chain | null): Observable<Observation> {
    const form = new FormData();
    form.append('photo', photo, photo.name || 'tag.jpg');
    let params = new HttpParams();
    if (chain) {
      params = params.set('chain', chain);
    }
    return this.http.post<Observation>(this.url('/api/captures'), form, { params });
  }

  /** The whole log, newest first. The app keeps it on the device and works from that copy. */
  observations(): Observable<Observation[]> {
    return this.http.get<Observation[]>(this.url('/api/observations'));
  }

  updateObservation(id: number, update: ObservationUpdate): Observable<Observation> {
    return this.http.put<Observation>(this.url(`/api/observations/${id}`), update);
  }

  deleteObservation(id: number): Observable<void> {
    return this.http.delete<void>(this.url(`/api/observations/${id}`));
  }

  /** @param force re-read Costco's listing even if the cached copy is fresh */
  deals(force = false): Observable<DealsView> {
    return this.http.get<DealsView>(this.url('/api/deals'), {
      params: new HttpParams().set('force', String(force)),
    });
  }

  tagRules(): Observable<TagRule[]> {
    return this.http.get<TagRule[]>(this.url('/api/tag-rules'));
  }

  updateTagRule(id: number, update: Partial<TagRule>): Observable<TagRule> {
    return this.http.put<TagRule>(this.url(`/api/tag-rules/${id}`), update);
  }

  /**
   * Photos come back as bytes rather than as a URL an <img> can point at, so
   * the key travels in a header like every other call. It used to ride as a
   * query parameter -- an <img> cannot send headers -- which wrote the secret
   * into browser history and into any access log along the way. The caller
   * turns this into an object URL; see the appPhotoSrc directive.
   */
  photoBlob(key: string): Observable<Blob> {
    return this.http.get(this.url(`/api/photos/${key}`), { responseType: 'blob' });
  }
}
