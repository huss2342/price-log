import { Directive, ElementRef, effect, inject, input } from '@angular/core';
import { DestroyRef } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { Api } from '../core/api';

/**
 * Points an <img> at a private tag photo.
 *
 * The bytes are fetched through the normal API client, so the key goes in the
 * X-API-Key header and never appears in a URL. The result is held as an object
 * URL and revoked when it is replaced or the image goes away, so a long review
 * session does not accumulate blobs.
 */
@Directive({
  selector: 'img[appPhotoSrc]',
})
export class PhotoSrc {
  private readonly api = inject(Api);
  private readonly image = inject<ElementRef<HTMLImageElement>>(ElementRef);
  private readonly destroyRef = inject(DestroyRef);

  /** The stored photo key, as it appears on an observation. */
  readonly appPhotoSrc = input.required<string>();

  private objectUrl: string | null = null;

  constructor() {
    this.destroyRef.onDestroy(() => this.release());

    effect(() => {
      const key = this.appPhotoSrc();
      this.release();
      this.image.nativeElement.removeAttribute('src');
      if (!key) {
        return;
      }
      this.api
        .photoBlob(key)
        .pipe(takeUntilDestroyed(this.destroyRef))
        .subscribe({
          next: (blob) => {
            this.objectUrl = URL.createObjectURL(blob);
            this.image.nativeElement.src = this.objectUrl;
          },
          // A missing photo just leaves the image empty; the reading itself is
          // still perfectly usable without it.
          error: () => this.image.nativeElement.removeAttribute('src'),
        });
    });
  }

  private release(): void {
    if (this.objectUrl) {
      URL.revokeObjectURL(this.objectUrl);
      this.objectUrl = null;
    }
  }
}
