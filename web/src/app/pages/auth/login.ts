import { Component, inject, signal } from '@angular/core';
import { Router, RouterLink } from '@angular/router';
import { Auth } from '../../core/auth';
import { describeError } from '../../core/errors';

const EMAIL = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;

@Component({
  selector: 'app-login',
  imports: [RouterLink],
  templateUrl: './login.html',
})
export class LoginPage {
  private readonly auth = inject(Auth);
  private readonly router = inject(Router);

  protected readonly email = signal('');
  protected readonly password = signal('');
  protected readonly busy = signal(false);
  protected readonly error = signal<string | null>(null);

  protected setEmail(event: Event): void {
    this.email.set((event.target as HTMLInputElement).value.trim());
    this.error.set(null);
  }

  protected setPassword(event: Event): void {
    this.password.set((event.target as HTMLInputElement).value);
    this.error.set(null);
  }

  protected canSubmit(): boolean {
    return EMAIL.test(this.email()) && this.password().length >= 8 && !this.busy();
  }

  protected submit(event: Event): void {
    event.preventDefault();
    if (!this.canSubmit()) return;
    this.busy.set(true);
    this.error.set(null);
    this.auth.login(this.email(), this.password()).subscribe({
      next: () => this.router.navigate(['/capture']),
      error: (err: { status?: number }) => {
        this.busy.set(false);
        this.error.set(
          err.status === 401
            ? 'Wrong email or password.'
            : describeError(err, 'Could not log in.'),
        );
      },
    });
  }
}
