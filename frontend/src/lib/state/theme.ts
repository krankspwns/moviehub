export type Theme = 'light' | 'dark';

const KEY = 'moviehub.theme.v1';
const COLORS: Record<Theme, string> = { dark: '#0e0c14', light: '#f6f3fc' };

function browserStorage(): Storage | null {
  try {
    return typeof localStorage === 'undefined' ? null : localStorage;
  } catch {
    return null;
  }
}

function systemPrefersLight(): boolean {
  return typeof matchMedia === 'function' && matchMedia('(prefers-color-scheme: light)').matches;
}

/** Saved choice wins; otherwise follow the operating system; dark when neither is available. */
export function readTheme(
  storage: Pick<Storage, 'getItem'> | null = browserStorage(),
  prefersLight: boolean = systemPrefersLight(),
): Theme {
  try {
    const value = storage?.getItem(KEY);
    if (value === 'light' || value === 'dark') return value;
  } catch {
    /* storage blocked */
  }
  return prefersLight ? 'light' : 'dark';
}

export function writeTheme(theme: Theme, storage: Pick<Storage, 'setItem'> | null = browserStorage()): void {
  try {
    storage?.setItem(KEY, theme);
  } catch {
    /* ignore */
  }
}

export const nextTheme = (theme: Theme): Theme => (theme === 'dark' ? 'light' : 'dark');

export function applyTheme(theme: Theme, doc: Document = document): void {
  doc.documentElement.dataset.theme = theme;
  doc.querySelector('meta[name="theme-color"]')?.setAttribute('content', COLORS[theme]);
}
