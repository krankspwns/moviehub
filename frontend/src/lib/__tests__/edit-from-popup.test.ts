import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { cleanup, fireEvent, render, screen } from '@testing-library/svelte';
import App from '../../App.svelte';

const movie = {
  id: 'm1',
  title: 'Heat',
  synopsis: 'A crew of thieves.',
  releaseYear: 1995,
  runtimeMinutes: 170,
  genres: ['Crime'],
  artworkUrl: null,
  credits: [{ personId: 'p1', personName: 'Al Pacino', role: 'ACTOR', characterName: 'Vincent' }],
};

beforeEach(() => {
  vi.stubGlobal(
    'fetch',
    vi.fn(async (_url, options: RequestInit) => {
      const { query } = JSON.parse(String(options.body));
      if (query.includes('people('))
        return { ok: true, json: async () => ({ data: { people: { items: [], total: 0, page: 0, totalPages: 0 } } }) };
      return {
        ok: true,
        json: async () => ({ data: { movies: { items: [movie], total: 1, page: 0, totalPages: 1 } } }),
      };
    }),
  );
});
afterEach(() => {
  cleanup();
  vi.unstubAllGlobals();
});

describe('editing from the details popup', () => {
  it('opens the editor pre-filled with the movie, not blank', async () => {
    render(App);
    await fireEvent.click(await screen.findByRole('button', { name: 'Heat' }));
    await fireEvent.click(screen.getByRole('button', { name: 'Edit Movie' }));
    expect(await screen.findByRole('heading', { name: 'Edit Movie' })).toBeInTheDocument();
    expect(screen.getByLabelText(/Title/)).toHaveValue('Heat');
    expect(screen.getByLabelText(/Genres/)).toHaveValue('Crime');
    expect(screen.getByRole('combobox', { name: 'Person' })).toHaveValue('Al Pacino');
  });
});
