import { Component, OnInit, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { Subject, debounceTime, distinctUntilChanged, switchMap, catchError, of } from 'rxjs';
import { Api } from '../../core/api';
import type { CompareGroup } from '../../core/models';
import { GroupCard } from '../../shared/group-card';

/** Categories worth a one-tap tile, in roughly the order groceries get bought. */
const CATEGORY_TILES = [
  'EGGS',
  'DAIRY',
  'MEAT',
  'SEAFOOD',
  'PRODUCE',
  'PANTRY',
  'FROZEN',
  'BAKERY',
  'BEVERAGES',
  'SNACKS',
  'PAPER_GOODS',
  'HOUSEHOLD',
  'PERSONAL_CARE',
  'SUPPLEMENTS',
  'PET',
];

@Component({
  selector: 'app-browse',
  imports: [FormsModule, GroupCard],
  templateUrl: './browse.html',
  styleUrl: './browse.scss',
})
export class BrowsePage implements OnInit {
  private readonly api = inject(Api);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly queries = new Subject<string>();

  protected readonly categories = CATEGORY_TILES;
  protected readonly query = signal('');
  protected readonly activeCategory = signal<string | null>(null);
  protected readonly groups = signal<CompareGroup[]>([]);
  protected readonly loading = signal(false);
  protected readonly searched = signal(false);
  protected readonly error = signal<string | null>(null);

  ngOnInit(): void {
    this.queries
      .pipe(
        debounceTime(280),
        distinctUntilChanged(),
        switchMap((q) => {
          if (!q.trim()) {
            this.loading.set(false);
            this.searched.set(false);
            return of<CompareGroup[]>([]);
          }
          this.loading.set(true);
          return this.api.search(q).pipe(catchError((err) => this.fail(err)));
        }),
      )
      .subscribe((groups) => {
        this.loading.set(false);
        this.groups.set(groups);
      });

    // Deep links from a capture card land here with the item already typed in.
    const initial = this.route.snapshot.queryParamMap.get('q');
    if (initial) {
      this.query.set(initial);
      this.onQuery(initial);
    }
  }

  protected onQuery(value: string): void {
    this.query.set(value);
    this.activeCategory.set(null);
    this.error.set(null);
    this.searched.set(value.trim().length > 0);
    this.queries.next(value);
  }

  protected pickCategory(category: string): void {
    const next = this.activeCategory() === category ? null : category;
    this.activeCategory.set(next);
    this.query.set('');
    this.error.set(null);

    if (!next) {
      this.groups.set([]);
      this.searched.set(false);
      return;
    }

    this.loading.set(true);
    this.searched.set(true);
    this.api
      .byCategory(next)
      .pipe(catchError((err) => this.fail(err)))
      .subscribe((groups) => {
        this.loading.set(false);
        this.groups.set(groups);
      });
  }

  protected clear(): void {
    this.query.set('');
    this.activeCategory.set(null);
    this.groups.set([]);
    this.searched.set(false);
    this.error.set(null);
    void this.router.navigate([], { queryParams: {} });
  }

  protected label(category: string): string {
    return category.replaceAll('_', ' ').toLowerCase();
  }

  private fail(err: unknown) {
    this.loading.set(false);
    const status = (err as { status?: number })?.status;
    this.error.set(
      status === 0
        ? 'Cannot reach the API. Check the address in Settings.'
        : ((err as { error?: { error?: string } })?.error?.error ?? 'Search failed.'),
    );
    return of<CompareGroup[]>([]);
  }
}
