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
