import { Component, inject } from '@angular/core';
import { RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { Settings } from './core/settings';
import { isDemoMode } from './demo/demo-mode';

@Component({
  selector: 'app-root',
  imports: [RouterOutlet, RouterLink, RouterLinkActive],
  templateUrl: './app.html',
  styleUrl: './app.scss',
})
export class App {
  protected readonly settings = inject(Settings);

  /**
   * Fixed for the life of the page: the injector was configured from this same
   * answer before the app started, so it must not be re-evaluated here.
   */
  protected readonly demo = isDemoMode();
}
