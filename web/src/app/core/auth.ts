import { HttpClient } from '@angular/common/http';
import { Injectable, inject, signal } from '@angular/core';
import { Observable, tap } from 'rxjs';
import { Settings } from './settings';

const KEY = 'pricelog.auth';

export interface AuthUser {
  id: number;
  email: string;
  displayName: string | null;
  createdAt?: string;
}

interface TokenResponse {
  id: number;
  email: string;
  displayName: string | null;
  token: string;
}

interface Persisted {
  token: string;
}

/**
 * Who this device is acting as.
 *
 * <p>The JWT lives in its own storage key ({@value KEY}), separate from the
 * legacy shared key in {@code pricelog.settings}, and rides every API call as
 * a Bearer token (see {@code api-key.interceptor}). The legacy key keeps
 * working when no account is signed in.
 *
 * <p>On construction a stored token is validated against /api/auth/me; a token
 * the API no longer honors is thrown away.
 */
@Injectable({ providedIn: 'root' })
export class Auth {
  private readonly http = inject(HttpClient);
  private readonly settings = inject(Settings);

  /** The signed-in account, or null. */
  readonly user = signal<AuthUser | null>(null);
  /** The JWT, or null when logged out. */
  readonly token = signal<string | null>(null);

  constructor() {
    const stored = this.readStored();
    if (stored) {
      this.token.set(stored);
      // Throws the token away when the API answers 401.
      this.loadMe().subscribe({ error: () => this.clear() });
    }
  }

  /** Signed in when a token is on hand. */
  isLoggedIn(): boolean {
    return (this.token() ?? '').length > 0;
  }

  register(email: string, password: string, displayName?: string): Observable<AuthUser> {
    return this.http
      .post<TokenResponse>(this.url('/api/auth/register'), {
        email,
        password,
        displayName: displayName?.trim() || undefined,
      })
      .pipe(tap((res) => this.store(res)));
  }

  login(email: string, password: string): Observable<AuthUser> {
    return this.http
      .post<TokenResponse>(this.url('/api/auth/login'), { email, password })
      .pipe(tap((res) => this.store(res)));
  }

  /** Re-reads who the token belongs to; drops a token the API answers 401 to. */
  loadMe(): Observable<AuthUser> {
    return this.http.get<AuthUser>(this.url('/api/auth/me')).pipe(
      tap({
        next: (me) => this.user.set(me),
        error: (err) => {
          if ((err as { status?: number })?.status === 401) {
            this.clear();
          }
        },
      }),
    );
  }

  changePassword(currentPassword: string, newPassword: string): Observable<void> {
    return this.http.put<void>(this.url('/api/auth/password'), {
      currentPassword,
      newPassword,
    });
  }

  /** Deletes the account server-side, then signs this device out. */
  deleteAccount(): Observable<void> {
    return this.http
      .delete<void>(this.url('/api/auth/account'))
      .pipe(tap(() => this.clear()));
  }

  logout(): void {
    this.clear();
  }

  private store(res: TokenResponse): void {
    this.user.set({ id: res.id, email: res.email, displayName: res.displayName });
    this.token.set(res.token);
    try {
      localStorage.setItem(KEY, JSON.stringify({ token: res.token } satisfies Persisted));
    } catch {
      // Private browsing. The session still holds for this page load.
    }
  }

  private clear(): void {
    this.user.set(null);
    this.token.set(null);
    try {
      localStorage.removeItem(KEY);
    } catch {
      // Nothing stored to clear.
    }
  }

  private readStored(): string | null {
    try {
      const raw = localStorage.getItem(KEY);
      if (!raw) return null;
      const parsed = JSON.parse(raw) as Partial<Persisted>;
      return parsed.token ? parsed.token : null;
    } catch {
      return null;
    }
  }

  private url(path: string): string {
    return `${this.settings.apiBase().replace(/\/+$/, '')}${path}`;
  }
}
