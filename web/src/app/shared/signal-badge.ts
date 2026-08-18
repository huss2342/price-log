import { Component, computed, input } from '@angular/core';
import { SIGNAL_LABELS, type SaleSignal } from '../core/models';

/** Renders a sale signal as a coloured pill: what the tag's price pattern means. */
@Component({
  selector: 'app-signal-badge',
  template: `<span class="badge badge-{{ tone() }}">{{ text() }}</span>`,
})
export class SignalBadge {
  readonly signal = input.required<SaleSignal>();

  protected readonly text = computed(() => SIGNAL_LABELS[this.signal()].text);
  protected readonly tone = computed(() => SIGNAL_LABELS[this.signal()].tone);
}
