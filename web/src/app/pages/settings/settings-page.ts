import { Component, OnInit, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Api } from '../../core/api';
import { Settings } from '../../core/settings';
import { CHAIN_LABELS, type Chain, type Store, type TagRule } from '../../core/models';

@Component({
  selector: 'app-settings',
  imports: [FormsModule],
  templateUrl: './settings-page.html',
  styleUrl: './settings-page.scss',
})
export class SettingsPage implements OnInit {
  private readonly api = inject(Api);
  protected readonly settings = inject(Settings);

  protected readonly chains: Chain[] = ['COSTCO', 'SAMS_CLUB', 'ALDI', 'WALMART', 'OTHER'];
  protected readonly chainLabels = CHAIN_LABELS;

  protected readonly stores = signal<Store[]>([]);
  protected readonly rules = signal<TagRule[]>([]);
  protected readonly status = signal<string | null>(null);
  protected readonly error = signal<string | null>(null);

  protected readonly editingId = signal<number | null>(null);
  protected readonly draft = signal<Partial<Store>>({});

  protected readonly newChain = signal<Chain>('COSTCO');
  protected readonly newLabel = signal('');
  protected readonly newCity = signal('');
  protected readonly newState = signal('');

  ngOnInit(): void {
    this.reload();
  }

  protected reload(): void {
    this.error.set(null);
    this.api.stores().subscribe({
      next: (rows) => this.stores.set(rows),
      error: (err) => this.error.set(this.message(err, 'Could not load stores.')),
    });
    this.api.tagRules().subscribe({
      next: (rows) => this.rules.set(rows),
      error: () => {},
    });
  }

  protected addStore(): void {
    const label = this.newLabel().trim();
    if (!label) return;

    this.api
      .createStore({
        chain: this.newChain(),
        label,
        city: this.newCity().trim() || null,
        state: this.newState().trim() || null,
      })
      .subscribe({
        next: (store) => {
          this.stores.update((rows) =>
            rows.some((r) => r.id === store.id) ? rows : [...rows, store],
          );
          this.newLabel.set('');
          this.newCity.set('');
          this.newState.set('');
          this.flash(`Added ${store.label}.`);
        },
        error: (err) => this.error.set(this.message(err, 'Could not add that store.')),
      });
  }

  protected startEdit(store: Store): void {
    this.editingId.set(store.id);
    this.draft.set({ ...store });
    this.error.set(null);
  }

  protected cancelEdit(): void {
    this.editingId.set(null);
  }

  protected patchDraft<K extends keyof Store>(key: K, value: Store[K]): void {
    this.draft.update((d) => ({ ...d, [key]: value }));
  }

  protected saveStore(): void {
    const id = this.editingId();
    const draft = this.draft();
    if (id == null || !draft.label?.trim()) return;

    this.api
      .updateStore(id, {
        chain: draft.chain,
        label: draft.label.trim(),
        city: draft.city?.trim() || null,
        state: draft.state?.trim() || null,
      })
      .subscribe({
        next: (saved) => {
          this.stores.update((rows) => rows.map((r) => (r.id === saved.id ? saved : r)));
          this.editingId.set(null);
          this.flash(`Saved ${saved.label}.`);
        },
        error: (err) => this.error.set(this.message(err, 'Could not save that store.')),
      });
  }

  protected deleteStore(store: Store): void {
    if (!globalThis.confirm(`Delete ${store.label}?`)) return;

    this.api.deleteStore(store.id).subscribe({
      next: () => {
        this.stores.update((rows) => rows.filter((r) => r.id !== store.id));
        this.editingId.set(null);
        this.flash(`Deleted ${store.label}.`);
      },
      // A store with prices logged against it is refused, not cascaded, so the
      // server's explanation is the useful thing to show.
      error: (err) => this.error.set(this.message(err, 'Could not delete that store.')),
    });
  }

  protected toggleRule(rule: TagRule): void {
    this.api.updateTagRule(rule.id, { enabled: !rule.enabled }).subscribe({
      next: (saved) => {
        this.rules.update((rows) => rows.map((r) => (r.id === saved.id ? saved : r)));
        this.flash(saved.enabled ? 'Rule enabled.' : 'Rule turned off.');
      },
      error: (err) => this.error.set(this.message(err, 'Could not update that rule.')),
    });
  }

  protected rulesFor(chain: Chain): TagRule[] {
    return this.rules().filter((r) => r.chain === chain);
  }

  protected priceLabel(rule: TagRule): string {
    if (rule.matchType === 'PRICE_ENDING') return `ends in ${rule.pattern}`;
    if (rule.matchType === 'MARKER') return rule.pattern.replaceAll('_', ' ').toLowerCase();
    return `"${rule.pattern}"`;
  }

  private flash(message: string): void {
    this.status.set(message);
    setTimeout(() => this.status.set(null), 2500);
  }

  private message(err: unknown, fallback: string): string {
    if ((err as { status?: number })?.status === 0) {
      return 'Cannot reach the API at that address.';
    }
    return (err as { error?: { error?: string } })?.error?.error ?? fallback;
  }
}
