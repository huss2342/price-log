const FORCED = 'pricelog.demo';

/**
 * Whether this browser should see the sample log rather than the real one.
 *
 * <p>Read once, before Angular starts, because it decides which service backs
 * every screen. A device with a key <em>or</em> a signed-in account is the
 * owner's and gets real data; anything else gets the demo, which is what a
 * shared link lands on.
 *
 * <p>?demo=1 forces it on so the owner can see what a visitor sees, and ?demo=0
 * clears that again. The choice sticks so a reload does not bounce back.
 */
export function isDemoMode(): boolean {
  const forced = new URLSearchParams(location.search).get('demo');
  if (forced === '1') {
    safeSet('1');
    return true;
  }
  if (forced === '0') {
    safeSet(null);
    return false;
  }
  if (safeGet() === '1') {
    return true;
  }
  return !storedCredential();
}

/**
 * Reads the credentials straight out of storage rather than through the
 * services, which cannot be constructed before the injector exists.
 */
function storedCredential(): string {
  if (new URLSearchParams(location.search).get('key')) {
    return 'pending';
  }
  try {
    const settings = localStorage.getItem('pricelog.settings');
    if (settings && (JSON.parse(settings).apiKey ?? '')) {
      return 'present';
    }
    const auth = localStorage.getItem('pricelog.auth');
    if (auth && (JSON.parse(auth).token ?? '')) {
      return 'present';
    }
    return '';
  } catch {
    return '';
  }
}

function safeGet(): string | null {
  try {
    return localStorage.getItem(FORCED);
  } catch {
    return null;
  }
}

function safeSet(value: string | null): void {
  try {
    if (value === null) {
      localStorage.removeItem(FORCED);
    } else {
      localStorage.setItem(FORCED, value);
    }
  } catch {
    // Private browsing. The mode still holds for this page load.
  }
}
