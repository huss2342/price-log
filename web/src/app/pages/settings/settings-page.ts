import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { Api } from '../../core/api';
import { describeError } from '../../core/errors';
import { LogStore } from '../../core/log-store';
import { CHAIN_LABELS, CHAINS, type SaleSignal, type TagRule } from '../../core/models';
import { Settings } from '../../core/settings';
import { isDemoMode } from '../../demo/demo-mode';

const SIGNAL_LABELS: Record<SaleSignal, string> = {
  REGULAR: 'Everyday price',
  INSTANT_SAVINGS: 'Instant savings',
  CLEARANCE: 'Clearance',
  MANAGER_MARKDOWN: 'Manager markdown',
  DISCONTINUED: 'Not restocking',
  UNKNOWN: 'Unread',
};

@Component({
  selector: 'app-settings',
  templateUrl: './settings-page.html',
  styleUrl: './settings-page.scss',
})
export class SettingsPage implements OnInit {
  private readonly api = inject(Api);
  private readonly log = inject(LogStore);
  protected readonly settings = inject(Settings);

  protected readonly demo = isDemoMode();
  protected readonly chainLabels = CHAIN_LABELS;
  protected readonly signalLabels = SIGNAL_LABELS;

  protected readonly rules = signal<TagRule[]>([]);
  protected readonly testing = signal(false);
  protected readonly result = signal<{ ok: boolean; text: string } | null>(null);

  protected readonly ruleChains = computed(() =>
    CHAINS.filter((chain) => this.rules().some((rule) => rule.chain === chain)),
  );

  ngOnInit(): void {
    this.loadRules();
  }

  protected setApiBase(event: Event): void {
    this.settings.apiBase.set((event.target as HTMLInputElement).value.trim());
    this.result.set(null);
  }

  protected setApiKey(event: Event): void {
    this.settings.apiKey.set((event.target as HTMLInputElement).value.trim());
    this.result.set(null);
  }

  protected async test(): Promise<void> {
    this.testing.set(true);
    await this.log.refresh(true);
    this.testing.set(false);
    const error = this.log.syncError();
    this.result.set(
      error
        ? { ok: false, text: error }
        : { ok: true, text: `Connected. ${this.log.entries().length} entries in the log.` },
    );
    this.loadRules();
  }

  /** The key decides whether the sample log or the real one is loaded, and that is settled at startup. */
  protected reload(): void {
    location.reload();
  }

  protected rulesFor(chain: string): TagRule[] {
    return this.rules().filter((rule) => rule.chain === chain);
  }

  protected pattern(rule: TagRule): string {
    if (rule.matchType === 'PRICE_ENDING') return `Ends ${rule.pattern}`;
    if (rule.matchType === 'MARKER') return rule.pattern.replace('_MARKER', '').replaceAll('_', ' ').toLowerCase();
    return `“${rule.pattern.toLowerCase()}”`;
  }

  protected toggle(rule: TagRule): void {
    this.api.updateTagRule(rule.id, { enabled: !rule.enabled }).subscribe({
      next: (saved) => this.rules.update((rows) => rows.map((r) => (r.id === saved.id ? saved : r))),
      error: (err) =>
        this.result.set({ ok: false, text: describeError(err, 'Could not change that rule.') }),
    });
  }

  private loadRules(): void {
    this.api.tagRules().subscribe({
      next: (rows) => this.rules.set(rows),
      error: () => this.rules.set([]),
    });
  }
}
