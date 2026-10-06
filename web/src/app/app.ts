import { Component, computed, inject } from '@angular/core';
import { RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { SwUpdate } from '@angular/service-worker';
import { filter } from 'rxjs';
import { Auth } from './core/auth';
import { LogStore } from './core/log-store';
import { Settings } from './core/settings';
import { Toast, type ToastAction } from './core/toast';
import { isDemoMode } from './demo/demo-mode';
import { Icon, type IconName } from './shared/icon';

interface Tab {
  path: string;
  label: string;
  icon: IconName;
}

@Component({
  selector: 'app-root',
  imports: [RouterOutlet, RouterLink, RouterLinkActive, Icon],
  templateUrl: './app.html',
  styleUrl: './app.scss',
})
export class App {
  protected readonly settings = inject(Settings);
  protected readonly auth = inject(Auth);
  protected readonly toast = inject(Toast);
  private readonly log = inject(LogStore);
  private readonly updates = inject(SwUpdate);

  /**
   * Fixed for the life of the page: the injector was configured from this same
   * answer before the app started, so it must not be re-evaluated here.
   */
  protected readonly demo = isDemoMode();

  /**
   * Without credentials nothing can be captured or corrected, so those tabs
   * are not offered. A signed-in account counts just like the shared key.
   */
  protected readonly tabs = computed<Tab[]>(() => {
    const write = this.settings.canWrite() || this.auth.isLoggedIn();
    return [
      ...(write ? [{ path: '/capture', label: 'Snap', icon: 'camera' as const }] : []),
      { path: '/browse', label: 'Browse', icon: 'browse' },
      { path: '/deals', label: 'Deals', icon: 'tag' },
      ...(write ? [{ path: '/entries', label: 'Entries', icon: 'list' as const }] : []),
      { path: '/settings', label: 'Settings', icon: 'settings' },
    ];
  });

  private updateReady = false;

  constructor() {
    // Starts the API waking up while the camera is still being aimed, and
    // brings the log on the device up to date.
    void this.log.refresh();

    document.addEventListener('visibilitychange', () => {
      if (document.visibilityState === 'hidden') {
        // Swapping in a new version while nobody is looking costs nothing.
        if (this.updateReady) location.reload();
        return;
      }
      void this.log.refresh();
    });

    if (this.updates.isEnabled) {
      this.updates.versionUpdates
        .pipe(filter((event) => event.type === 'VERSION_READY'))
        .subscribe(() => {
          this.updateReady = true;
          if (document.visibilityState === 'visible') {
            // Seen while reading, so it asks instead of reloading underfoot.
            // Hidden, the visibility handler above swaps it in silently.
            this.toast.show('New version available', 'info', {
              sticky: true,
              action: { label: 'Reload', run: () => location.reload() },
            });
          }
        });
    }
  }

  /** Runs a toast's action, then clears it — the update prompt's Reload. */
  protected runToastAction(item: { id: number; action?: ToastAction }, event: Event): void {
    event.stopPropagation();
    const action = item.action;
    if (!action) return;
    this.toast.dismiss(item.id);
    action.run();
  }
}
