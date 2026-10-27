import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { cleanup, fireEvent, render, screen } from '@testing-library/svelte';
import MovieEditor from '../components/MovieEditor.svelte';

beforeEach(() => {
  vi.stubGlobal(
    'fetch',
    vi.fn(async () => ({
      ok: true,
      json: async () => ({ data: { people: { items: [], total: 0, page: 0, totalPages: 0 } } }),
    })),
  );
});
afterEach(() => {
  cleanup();
  vi.unstubAllGlobals();
});

describe('Enter key in editors', () => {
  it('does not submit from a text field, but still allows new lines in a textarea', async () => {
    render(MovieEditor, { movie: null, onsaved: vi.fn(), onclose: vi.fn() });
    const title = screen.getByRole('textbox', { name: /^Title/ });
    // fireEvent resolves to false when the event's default action was prevented.
    expect(await fireEvent.keyDown(title, { key: 'Enter' })).toBe(false);
    const synopsis = screen.getByRole('textbox', { name: /^Synopsis/ });
    expect(await fireEvent.keyDown(synopsis, { key: 'Enter' })).toBe(true);
  });
});
