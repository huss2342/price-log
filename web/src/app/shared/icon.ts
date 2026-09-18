import { Component, computed, input } from '@angular/core';

/** Line icons drawn inline, so they look the same on every phone rather than as whatever emoji a glyph falls back to. */
const PATHS = {
  camera:
    'M14.5 4h-5L7 7H4a2 2 0 0 0-2 2v9a2 2 0 0 0 2 2h16a2 2 0 0 0 2-2V9a2 2 0 0 0-2-2h-3z M12 9a4 4 0 1 0 0 8a4 4 0 1 0 0-8z',
  browse: 'M4 4h6v6H4z M14 4h6v6h-6z M4 14h6v6H4z M14 14h6v6h-6z',
  tag: 'M12.6 2.6A2 2 0 0 0 11.2 2H4a2 2 0 0 0-2 2v7.2a2 2 0 0 0 .6 1.4l8.7 8.7a2.4 2.4 0 0 0 3.4 0l6.6-6.6a2.4 2.4 0 0 0 0-3.4z M7.5 7.5h.01',
  list: 'M8 6h13 M8 12h13 M8 18h13 M3.5 6h.01 M3.5 12h.01 M3.5 18h.01',
  settings: 'M4 21v-7 M4 10V3 M12 21v-9 M12 8V3 M20 21v-5 M20 12V3 M1 14h6 M9 8h6 M17 16h6',
  search: 'M11 19a8 8 0 1 0 0-16a8 8 0 0 0 0 16z M21 21l-4.3-4.3',
  upload: 'M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4 M17 8l-5-5-5 5 M12 3v12',
  back: 'M15 18l-6-6 6-6',
  refresh: 'M21 12a9 9 0 1 1-2.6-6.4L21 8 M21 3v5h-5',
  close: 'M18 6L6 18 M6 6l12 12',
  check: 'M20 6L9 17l-5-5',
} as const;

export type IconName = keyof typeof PATHS;

@Component({
  selector: 'app-icon',
  host: { class: 'icon' },
  template: `
    <svg
      [attr.width]="size()"
      [attr.height]="size()"
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      stroke-width="1.9"
      stroke-linecap="round"
      stroke-linejoin="round"
      aria-hidden="true"
    >
      <path [attr.d]="path()" />
    </svg>
  `,
})
export class Icon {
  readonly name = input.required<IconName>();
  readonly size = input(22);

  protected readonly path = computed(() => PATHS[this.name()]);
}
