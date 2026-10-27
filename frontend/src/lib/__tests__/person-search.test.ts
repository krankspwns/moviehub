import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/svelte';
import MovieEditor from '../components/MovieEditor.svelte';

const mockFetch = vi.fn();
const people = (names: string[]) => ({
  ok: true,
  json: async () => ({
    data: {
      people: {
        items: names.map((name, i) => ({
          id: `p${i}`,
          name,
          bio: '',
          birthDate: null,
          avatarUrl: null,
          filmography: [],
        })),
        total: names.length,
        page: 0,
        totalPages: 1,
      },
    },
  }),
});
const searches = () => mockFetch.mock.calls.map(([, options]) => JSON.parse(options.body).variables.query);

beforeEach(() => {
  vi.stubGlobal('fetch', mockFetch);
  mockFetch.mockImplementation(async (_url, options: RequestInit) => {
    const { variables } = JSON.parse(String(options.body));
    return people(variables.query === 'Ada' ? ['Ada Lovelace'] : ['Ada Lovelace', 'Alan Turing']);
  });
});
afterEach(() => {
  cleanup();
  vi.unstubAllGlobals();
  vi.clearAllMocks();
});

describe('person search in the movie editor', () => {
  it('searches with the complete typed text and reports the matches', async () => {
    render(MovieEditor, { movie: null, onsaved: vi.fn(), onclose: vi.fn() });
    await fireEvent.click(screen.getByRole('button', { name: '+ Add credit' }));
    await fireEvent.input(screen.getByRole('searchbox', { name: /Search people/ }), { target: { value: 'Ada' } });
    await waitFor(() => expect(searches()).toContain('Ada'));
    expect(await screen.findByText(/1\s+person matches\s+“Ada”/)).toBeInTheDocument();
  });

  it('explains when nobody matches', async () => {
    mockFetch.mockImplementation(async () => people([]));
    render(MovieEditor, { movie: null, onsaved: vi.fn(), onclose: vi.fn() });
    await fireEvent.click(screen.getByRole('button', { name: '+ Add credit' }));
    await fireEvent.input(screen.getByRole('searchbox', { name: /Search people/ }), { target: { value: 'Zed' } });
    expect(await screen.findByText(/No one matches that search/)).toBeInTheDocument();
  });
});
