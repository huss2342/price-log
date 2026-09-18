import { Pipe, PipeTransform } from '@angular/core';
import { UNIT_LABELS, type BaseUnit } from '../core/models';

const MONTHS = ['Jan', 'Feb', 'Mar', 'Apr', 'May', 'Jun', 'Jul', 'Aug', 'Sep', 'Oct', 'Nov', 'Dec'];

/** Integer cents to "$12.97", or "$4" for a round amount when `compact`. */
export function money(cents: number | null | undefined, compact = false): string {
  if (cents == null) return '—';
  const dollars = cents / 100;
  if (compact && cents % 100 === 0) return `$${dollars.toLocaleString('en-US')}`;
  return `$${dollars.toLocaleString('en-US', { minimumFractionDigits: 2, maximumFractionDigits: 2 })}`;
}

/**
 * Unit prices are fractions of a cent, so a flat "$0.00" would collapse most of
 * them to nothing. Sub-dollar values are shown in cents instead.
 */
export function unitPrice(cents: number | null | undefined, unit: BaseUnit | null | undefined): string {
  if (cents == null || !unit || unit === 'NONE') return '';
  const label = UNIT_LABELS[unit];
  if (cents >= 100) return `$${(cents / 100).toFixed(2)}/${label}`;
  return `${cents.toFixed(cents < 1 ? 2 : 1)}¢/${label}`;
}

/** "40 oz", "2 × 32 oz", "30 ct". */
export function sizeLabel(o: {
  sizeValue: number | null;
  sizeUnit: string | null;
  packCount: number | null;
}): string {
  if (o.sizeValue == null || !o.sizeUnit) return '';
  const size = `${Number(o.sizeValue.toFixed(2))} ${o.sizeUnit.toLowerCase()}`;
  return o.packCount && o.packCount > 1 ? `${o.packCount} × ${size}` : size;
}

/** A size or pack count at the end of a name, which the size line already says. */
const TRAILING_SIZE =
  /[\s,–-]+\d+(\.\d+)?\s*-?\s*(fl\.?\s*oz|ounces?|oz|lbs?|pounds?|ct|count|pk|packs?|rolls?|sheets?|g|kg|ml|l|liters?|gal|gallons?)\b.*$/i;

/** "AmyLu Andouille Sausage 40 Ounce Package" becomes "AmyLu Andouille Sausage". */
export function shortName(name: string): string {
  const trimmed = name.replace(TRAILING_SIZE, '').trim();
  return trimmed.length >= 3 ? trimmed : name;
}

/** "2026-09-20" to "Sep 20", with the year only when it is not this one. */
export function shortDate(iso: string | null | undefined, today?: string): string {
  if (!iso) return '';
  const [year, month, day] = iso.split('-').map(Number);
  const label = `${MONTHS[month - 1]} ${day}`;
  return today && !today.startsWith(String(year)) ? `${label}, ${year}` : label;
}

/** "Today", "Yesterday", or the date. */
export function dayLabel(iso: string, today: string): string {
  if (iso === today) return 'Today';
  const yesterday = new Date(`${today}T12:00:00`);
  yesterday.setDate(yesterday.getDate() - 1);
  const y = `${yesterday.getFullYear()}-${String(yesterday.getMonth() + 1).padStart(2, '0')}-${String(yesterday.getDate()).padStart(2, '0')}`;
  return iso === y ? 'Yesterday' : shortDate(iso, today);
}

@Pipe({ name: 'money' })
export class MoneyPipe implements PipeTransform {
  transform(cents: number | null | undefined, compact = false): string {
    return money(cents, compact);
  }
}

@Pipe({ name: 'unitPrice' })
export class UnitPricePipe implements PipeTransform {
  transform(cents: number | null | undefined, unit: BaseUnit | null | undefined): string {
    return unitPrice(cents, unit);
  }
}
