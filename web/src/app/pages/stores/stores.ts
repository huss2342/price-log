import { Component, OnInit, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { describeError } from '../../core/errors';
import { CHAIN_LABELS, CHAINS, type Chain, type Store } from '../../core/models';
import { Stores } from '../../core/stores';
import { Toast } from '../../core/toast';

/**
 * The user's named stores: add one, rename one, or delete one that has no
 * logged prices. Deleting a store the log mentions is refused by the API with
 * a 409 and its reason, which is shown rather than retried.
 */
@Component({
  selector: 'app-stores',
  imports: [FormsModule, RouterLink],
  templateUrl: './stores.html',
  styleUrl: './stores.scss',
})
export class StoresPage implements OnInit {
  protected readonly stores = inject(Stores);
  private readonly toast = inject(Toast);

  protected readonly chains = CHAINS.filter((chain) => chain !== 'OTHER');
  protected readonly chainLabels = CHAIN_LABELS;

  protected readonly newChain = signal<Chain>('COSTCO');
  protected readonly newLabel = signal('');
  protected readonly newCity = signal('');
  protected readonly newState = signal('');
  protected readonly adding = signal(false);
  protected readonly addError = signal<string | null>(null);

  protected readonly editingId = signal<number | null>(null);
  protected readonly editLabel = signal('');
  protected readonly editCity = signal('');
  protected readonly editState = signal('');
  protected readonly editError = signal<string | null>(null);
  protected readonly saving = signal(false);

  protected readonly deleteError = signal<{ id: number; message: string } | null>(null);
  protected readonly deletingId = signal<number | null>(null);

  ngOnInit(): void {
    void this.stores.load();
  }

  protected place(store: Store): string {
    return [store.city, store.state].filter((part) => part).join(', ');
  }

  protected async add(): Promise<void> {
    const label = this.newLabel().trim();
    if (!label) {
      this.addError.set('Give the store a name.');
      return;
    }
    this.adding.set(true);
    this.addError.set(null);
    try {
      const store = await this.stores.create({
        chain: this.newChain(),
        label,
        city: this.newCity().trim() || undefined,
        state: this.newState().trim() || undefined,
      });
      this.newLabel.set('');
      this.newCity.set('');
      this.newState.set('');
      this.toast.show(`“${store.label}” added`);
    } catch (err) {
      this.addError.set(describeError(err, 'Could not add that store.'));
    } finally {
      this.adding.set(false);
    }
  }

  protected startRename(store: Store): void {
    this.deleteError.set(null);
    this.editingId.set(store.id);
    this.editLabel.set(store.label);
    this.editCity.set(store.city ?? '');
    this.editState.set(store.state ?? '');
    this.editError.set(null);
  }

  protected async saveRename(id: number): Promise<void> {
    const label = this.editLabel().trim();
    if (!label) {
      this.editError.set('Give the store a name.');
      return;
    }
    this.saving.set(true);
    this.editError.set(null);
    try {
      await this.stores.rename(id, {
        label,
        city: this.editCity().trim() || null,
        state: this.editState().trim() || null,
      });
      this.editingId.set(null);
      this.toast.show('Store renamed');
    } catch (err) {
      this.editError.set(describeError(err, 'Could not rename that store.'));
    } finally {
      this.saving.set(false);
    }
  }

  protected async remove(id: number, label: string): Promise<void> {
    if (!globalThis.confirm(`Delete “${label}”? This cannot be undone.`)) return;
    this.deletingId.set(id);
    this.deleteError.set(null);
    try {
      await this.stores.remove(id);
      this.toast.show(`“${label}” deleted`);
    } catch (err) {
      // 409: the log still mentions this store; the API says how many prices.
      this.deleteError.set({ id, message: describeError(err, 'Could not delete that store.') });
    } finally {
      this.deletingId.set(null);
    }
  }
}
