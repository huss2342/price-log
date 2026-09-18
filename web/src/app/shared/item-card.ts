import { Component, input } from '@angular/core';
import { RouterLink } from '@angular/router';
import type { CardView } from './cards';
import { MoneyPipe } from './format';

/**
 * The only item card. Snap, Browse, Deals and Entries all render this, from a
 * {@link CardView}, so the same thing never looks different in two places.
 */
@Component({
  selector: 'app-item-card',
  imports: [RouterLink, MoneyPipe],
  template: `
    @let c = card();
    <a
      class="item"
      [routerLink]="['/item', c.productId]"
      [queryParams]="c.entryId ? { entry: c.entryId } : {}"
    >
      <span class="item-name">{{ c.name }}</span>
      <span class="item-price">{{ c.priceCents | money }}</span>
      <span class="item-detail">{{ c.detail }}</span>
      @if (c.wasCents) {
        <span class="item-was">{{ c.wasCents | money }}</span>
      }
      @if (c.chips.length) {
        <span class="item-chips">
          @for (chip of c.chips; track chip.text) {
            <span class="chip chip-{{ chip.tone }}">{{ chip.text }}</span>
          }
        </span>
      }
    </a>
  `,
})
export class ItemCard {
  readonly card = input.required<CardView>();
}
