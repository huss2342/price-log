/** What to tell someone when a request fails, preferring the API's own explanation. */
export function describeError(err: unknown, fallback: string): string {
  const status = (err as { status?: number })?.status;
  if (status === 0) {
    return 'Cannot reach the API. Check the address in Settings.';
  }
  return (err as { error?: { error?: string } })?.error?.error ?? fallback;
}
