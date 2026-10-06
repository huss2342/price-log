import { Component, inject, signal } from '@angular/core';
import { Router, RouterLink } from '@angular/router';
import { Auth } from '../../core/auth';
import { describeError } from '../../core/errors';

const EMAIL = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;

@Component({
  selector: 'app-register',
  imports: [RouterLink],
  templateUrl: './register.html',
})
export class RegisterPage {
  private readonly auth = inject(Auth);
  private readonly router = inject(Router);

  protected readonly displayName = signal('');
  protected readonly email = signal('');
  protected readonly password = signal('');
  protected readonly busy = signal(false);
  protected readonly error = signal<string | null>(null);

  protected setDisplayName(event: Event): void {
    this.displayName.set((event.target as HTMLInputElement).value);
    this.error.set(null);
  }

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
    this.auth.register(this.email(), this.password(), this.displayName()).subscribe({
      next: () => this.router.navigate(['/capture']),
      error: (err: { status?: number }) => {
        this.busy.set(false);
        this.error.set(
          err.status === 409
            ? 'An account with that email already exists.'
            : describeError(err, 'Could not create the account.'),
        );
      },
    });
  }
}
