import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/svelte';
import App from '../../App.svelte';

const mockFetch = vi.fn();
beforeEach(() => {
  vi.stubGlobal('fetch', mockFetch);
  mockFetch.mockImplementation(async (_url, options: RequestInit) => {
    const { query } = JSON.parse(String(options.body));
    if (query.includes('createPerson'))
      return { ok: true, json: async () => ({ data: { createPerson: { id: 'p1', name: 'Ada' } } }) };
    if (query.includes('search('))
      return { ok: true, json: async () => ({ data: { search: { movies: [], people: [] } } }) };
    if (query.includes('people('))
      return { ok: true, json: async () => ({ data: { people: { items: [], total: 0, page: 0, totalPages: 0 } } }) };
    return { ok: true, json: async () => ({ data: { movies: { items: [], total: 0, page: 0, totalPages: 0 } } }) };
  });
});
afterEach(() => {
  cleanup();
  vi.unstubAllGlobals();
  vi.clearAllMocks();
});

describe('catalog UI', () => {
  it('shows loading feedback and a helpful empty state', async () => {
    render(App);
    expect(screen.getByRole('status')).toHaveTextContent('Loading movies');
    expect(await screen.findByText(/No movies found/)).toBeInTheDocument();
  });

  it('creates a person and confirms success', async () => {
    render(App);
    await fireEvent.click(screen.getByRole('button', { name: 'Cast & creators' }));
    await fireEvent.click(screen.getByRole('button', { name: /Add Person/ }));
    await fireEvent.input(screen.getByRole('textbox', { name: /^Name/ }), { target: { value: 'Ada' } });
    await fireEvent.click(screen.getByRole('button', { name: 'Save Person' }));
    await waitFor(() => expect(screen.getByRole('status')).toHaveTextContent('Person saved successfully'));
    expect(mockFetch.mock.calls.some(([, options]) => JSON.parse(options.body).query.includes('createPerson'))).toBe(
      true,
    );
  });

  it('searches both movies and people after entering a term', async () => {
    render(App);
    await fireEvent.click(screen.getByRole('button', { name: 'Explore all' }));
    await fireEvent.input(screen.getByRole('textbox', { name: 'Search' }), { target: { value: 'Ada' } });
    await waitFor(
      () =>
        expect(mockFetch.mock.calls.some(([, options]) => JSON.parse(options.body).query.includes('search('))).toBe(
          true,
        ),
      { timeout: 2000 },
    );
  });
});
