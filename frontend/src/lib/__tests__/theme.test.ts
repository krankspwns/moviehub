import { describe, expect, it, vi } from 'vitest';
import { applyTheme, nextTheme, readTheme, writeTheme } from '../state/theme';

const stored = (value: string | null) => ({ getItem: () => value });

describe('theme', () => {
  it('prefers the saved choice over the system setting', () => {
    expect(readTheme(stored('light'), false)).toBe('light');
    expect(readTheme(stored('dark'), true)).toBe('dark');
  });
  it('falls back to the system setting, then dark, for missing or invalid values', () => {
    expect(readTheme(stored(null), true)).toBe('light');
    expect(readTheme(stored('purple'), false)).toBe('dark');
    expect(readTheme(null, false)).toBe('dark');
    expect(readTheme({ getItem: () => { throw new Error('blocked'); } }, true)).toBe('light');
  });
  it('toggles and persists without throwing when storage fails', () => {
    expect(nextTheme('dark')).toBe('light');
    expect(nextTheme('light')).toBe('dark');
    const setItem = vi.fn();
    writeTheme('light', { setItem });
    expect(setItem).toHaveBeenCalledWith('moviehub.theme.v1', 'light');
    expect(() => writeTheme('dark', { setItem: () => { throw new Error('quota'); } })).not.toThrow();
  });
  it('applies the theme to the document and the browser theme colour', () => {
    document.head.innerHTML = '<meta name="theme-color" content="#000">';
    applyTheme('light');
    expect(document.documentElement.dataset.theme).toBe('light');
    expect(document.querySelector('meta[name="theme-color"]')?.getAttribute('content')).toBe('#f6f3fc');
  });
});
