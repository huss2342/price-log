import { Component, computed, inject } from '@angular/core';
import { LogStore } from '../../core/log-store';
import { entryCard, type CardView } from '../../shared/cards';
import { dayLabel } from '../../shared/format';
import { ItemCard } from '../../shared/item-card';
import { newestFirst } from '../../shared/pricing';

interface Day {
  date: string;
  label: string;
  cards: CardView[];
}

/** Every tag logged, by the day it was seen, with anything worth a second look on top. */
@Component({
  selector: 'app-entries',
  imports: [ItemCard],
  templateUrl: './entries.html',
})
export class EntriesPage {
  protected readonly log = inject(LogStore);

  private readonly context = computed(() => ({
    today: this.log.today(),
    showChain: this.log.multiChain(),
  }));

  /** Readings the model was unsure of, or could not size. */
  protected readonly toCheck = computed(() =>
    this.log
      .entries()
      .filter((e) => e.needsReview)
      .sort((a, b) => b.id - a.id)
      .map((e) => entryCard(e, this.context())),
  );

  protected readonly days = computed<Day[]>(() => {
    const days: Day[] = [];
    for (const entry of [...this.log.entries()].filter((e) => !e.needsReview).sort(newestFirst)) {
      let day = days.at(-1);
      if (day?.date !== entry.observedOn) {
        day = { date: entry.observedOn, label: dayLabel(entry.observedOn, this.log.today()), cards: [] };
        days.push(day);
      }
      day.cards.push(entryCard(entry, this.context()));
    }
    return days;
  });
}
