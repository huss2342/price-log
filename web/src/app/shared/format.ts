import { Pipe, PipeTransform } from '@angular/core';
import { UNIT_LABELS, type BaseUnit } from '../core/models';

/** Integer cents to "$12.97". */
@Pipe({ name: 'money' })
export class MoneyPipe implements PipeTransform {
  transform(cents: number | null | undefined): string {
    if (cents == null) return '—';
    return `$${(cents / 100).toFixed(2)}`;
  }
}

/**
 * Unit prices are fractions of a cent, so a flat "$0.00" would collapse most of
 * them to nothing. Sub-dollar values are shown in cents instead.
 */
@Pipe({ name: 'unitPrice' })
export class UnitPricePipe implements PipeTransform {
  transform(cents: number | null | undefined, unit: BaseUnit | null | undefined): string {
    if (cents == null || !unit || unit === 'NONE') return '';
    const label = UNIT_LABELS[unit];
    if (cents >= 100) {
      return `$${(cents / 100).toFixed(2)}/${label}`;
    }
    return `${cents.toFixed(cents < 1 ? 2 : 1)}¢/${label}`;
  }
}

/** "today", "3 days ago", "2 months ago". */
@Pipe({ name: 'ago' })
export class AgoPipe implements PipeTransform {
  transform(days: number | null | undefined): string {
    if (days == null) return '';
    if (days <= 0) return 'today';
    if (days === 1) return 'yesterday';
    if (days < 30) return `${days} days ago`;
    const months = Math.round(days / 30);
    return months === 1 ? 'a month ago' : `${months} months ago`;
  }
}
