import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/svelte';
import App from '../../App.svelte';

const mockFetch = vi.fn();
const movie = {
  id: 'm1',
  title: 'Heat',
  synopsis: '',
  releaseYear: 1995,
  runtimeMinutes: 170,
  genres: [],
  artworkUrl: null,
  credits: [],
};

const queries = () => mockFetch.mock.calls.map(([, options]) => String(JSON.parse(options.body).query));

beforeEach(() => {
  vi.stubGlobal('fetch', mockFetch);
  mockFetch.mockImplementation(async (_url, options: RequestInit) => {
    const { query } = JSON.parse(String(options.body));
    if (query.includes('deleteMovie')) return { ok: true, json: async () => ({ data: { deleteMovie: true } }) };
    if (query.includes('people('))
      return { ok: true, json: async () => ({ data: { people: { items: [], total: 0, page: 0, totalPages: 0 } } }) };
    return {
      ok: true,
      json: async () => ({ data: { movies: { items: [movie], total: 1, page: 0, totalPages: 1 } } }),
    };
  });
});
afterEach(() => {
  cleanup();
  vi.unstubAllGlobals();
  vi.clearAllMocks();
});

describe('deleting', () => {
  it('asks for confirmation and deletes nothing when cancelled', async () => {
    render(App);
    await fireEvent.click(await screen.findByRole('button', { name: 'Delete Heat' }));
    expect(screen.getByRole('dialog', { name: 'Delete Heat?' })).toBeInTheDocument();
    await fireEvent.click(screen.getByRole('button', { name: 'Cancel' }));
    expect(screen.queryByRole('dialog', { name: 'Delete Heat?' })).not.toBeInTheDocument();
    expect(queries().some((q) => q.includes('deleteMovie'))).toBe(false);
  });

  it('deletes after confirming and shows a dismissible toast', async () => {
    render(App);
    await fireEvent.click(await screen.findByRole('button', { name: 'Delete Heat' }));
    await fireEvent.click(screen.getByRole('button', { name: 'Delete' }));
    await waitFor(() => expect(screen.getByRole('status')).toHaveTextContent('Heat deleted.'));
    expect(queries().some((q) => q.includes('deleteMovie'))).toBe(true);
    await fireEvent.click(screen.getByRole('button', { name: 'Dismiss message' }));
    expect(screen.queryByText('Heat deleted.')).not.toBeInTheDocument();
  });

  it('cancels with Escape', async () => {
    render(App);
    await fireEvent.click(await screen.findByRole('button', { name: 'Delete Heat' }));
    await fireEvent.keyDown(window, { key: 'Escape' });
    expect(screen.queryByRole('dialog', { name: 'Delete Heat?' })).not.toBeInTheDocument();
  });
});
