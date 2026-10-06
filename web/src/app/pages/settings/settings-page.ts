import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { Router, RouterLink } from '@angular/router';
import { Api } from '../../core/api';
import { Auth } from '../../core/auth';
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
  imports: [RouterLink],
  templateUrl: './settings-page.html',
  styleUrl: './settings-page.scss',
})
export class SettingsPage implements OnInit {
  private readonly api = inject(Api);
  private readonly log = inject(LogStore);
  private readonly router = inject(Router);
  protected readonly auth = inject(Auth);
  protected readonly settings = inject(Settings);

  protected readonly demo = isDemoMode();
  protected readonly chainLabels = CHAIN_LABELS;
  protected readonly signalLabels = SIGNAL_LABELS;

  protected readonly rules = signal<TagRule[]>([]);
  protected readonly testing = signal(false);
  protected readonly result = signal<{ ok: boolean; text: string } | null>(null);

  // Account section state: no toast system exists yet, so account feedback
  // follows the same .notice pattern as the connection panel above.
  protected readonly currentPassword = signal('');
  protected readonly newPassword = signal('');
  protected readonly confirmPassword = signal('');
  protected readonly pwBusy = signal(false);
  protected readonly pwNotice = signal<{ ok: boolean; text: string } | null>(null);
  protected readonly exportBusy = signal(false);
  protected readonly exportNotice = signal<{ ok: boolean; text: string } | null>(null);
  protected readonly deleteArmed = signal(false);
  protected readonly deleteBusy = signal(false);
  protected readonly deleteError = signal<string | null>(null);

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

  /** Signs this device out of the account; the legacy key, if any, stays put. */
  protected logout(): void {
    this.auth.logout();
    this.resetAccountForms();
    this.router.navigate(['/browse']);
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

  protected setCurrentPassword(event: Event): void {
    this.currentPassword.set((event.target as HTMLInputElement).value);
    this.pwNotice.set(null);
  }

  protected setNewPassword(event: Event): void {
    this.newPassword.set((event.target as HTMLInputElement).value);
    this.pwNotice.set(null);
  }

  protected setConfirmPassword(event: Event): void {
    this.confirmPassword.set((event.target as HTMLInputElement).value);
    this.pwNotice.set(null);
  }

  protected changePassword(): void {
    const next = this.newPassword();
    if (next.length < 8) {
      this.pwNotice.set({ ok: false, text: 'The new password needs at least 8 characters.' });
      return;
    }
    if (next !== this.confirmPassword()) {
      this.pwNotice.set({ ok: false, text: 'The new passwords do not match.' });
      return;
    }
    this.pwBusy.set(true);
    this.pwNotice.set(null);
    this.auth.changePassword(this.currentPassword(), next).subscribe({
      next: () => {
        this.pwBusy.set(false);
        this.currentPassword.set('');
        this.newPassword.set('');
        this.confirmPassword.set('');
        this.pwNotice.set({ ok: true, text: 'Password changed.' });
      },
      error: (err: { status?: number }) => {
        this.pwBusy.set(false);
        this.pwNotice.set({
          ok: false,
          text:
            err.status === 401 || err.status === 403
              ? 'Your current password is wrong.'
              : describeError(err, 'Could not change the password.'),
        });
      },
    });
  }

  protected exportData(): void {
    this.exportBusy.set(true);
    this.exportNotice.set(null);
    this.api.exportData().subscribe({
      next: (blob) => {
        this.exportBusy.set(false);
        const url = URL.createObjectURL(new Blob([blob], { type: 'application/json' }));
        const anchor = document.createElement('a');
        anchor.href = url;
        anchor.download = 'price-log-export.json';
        document.body.appendChild(anchor);
        anchor.click();
        anchor.remove();
        setTimeout(() => URL.revokeObjectURL(url), 1000);
        this.exportNotice.set({ ok: true, text: 'Downloaded your data.' });
      },
      error: (err) => {
        this.exportBusy.set(false);
        this.exportNotice.set({ ok: false, text: describeError(err, 'Could not export your data.') });
      },
    });
  }

  /** A two-step delete: the first tap only arms the button. */
  protected deleteAccount(): void {
    if (!this.deleteArmed()) {
      this.deleteArmed.set(true);
      return;
    }
    this.deleteBusy.set(true);
    this.deleteError.set(null);
    this.auth.deleteAccount().subscribe({
      // The token is already cleared by Auth; land somewhere read-only.
      next: () => this.router.navigate(['/browse']),
      error: (err) => {
        this.deleteBusy.set(false);
        this.deleteArmed.set(false);
        this.deleteError.set(describeError(err, 'Could not delete the account.'));
      },
    });
  }

  private resetAccountForms(): void {
    this.currentPassword.set('');
    this.newPassword.set('');
    this.confirmPassword.set('');
    this.pwNotice.set(null);
    this.exportNotice.set(null);
    this.deleteArmed.set(false);
    this.deleteError.set(null);
  }

  private loadRules(): void {
    this.api.tagRules().subscribe({
      next: (rows) => this.rules.set(rows),
      error: () => this.rules.set([]),
    });
  }
}
