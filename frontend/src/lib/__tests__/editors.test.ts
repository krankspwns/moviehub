import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/svelte';
import MovieEditor from '../components/MovieEditor.svelte';
import PersonEditor from '../components/PersonEditor.svelte';
import SearchResults from '../components/SearchResults.svelte';
import type { Movie, Person } from '../api/catalog';

const mockFetch = vi.fn();
beforeEach(() => {
  vi.stubGlobal('fetch', mockFetch);
  mockFetch.mockReset();
});
afterEach(() => { cleanup(); vi.unstubAllGlobals(); });

const reply = (data: unknown) => ({ ok: true, json: async () => ({ data }) });
const peoplePage = { people: { items: [], total: 0, page: 0, pageSize: 12, totalPages: 0 } };
const bodyOf = (call: number) => JSON.parse(mockFetch.mock.calls[call][1].body);

describe('MovieEditor', () => {
  it('shows a specific message and does not call the server when the form is invalid', async () => {
    mockFetch.mockResolvedValue(reply(peoplePage));
    render(MovieEditor, { movie: null, onsaved: vi.fn(), onclose: vi.fn() });
    await waitFor(() => expect(mockFetch).toHaveBeenCalledTimes(1)); // person picker
    await fireEvent.submit(screen.getByRole('button', { name: 'Save movie' }).closest('form')!);
    expect(await screen.findByRole('alert')).toHaveTextContent('Enter a movie title.');
    expect(mockFetch).toHaveBeenCalledTimes(1);
  });

  it('saves a valid movie and reports success', async () => {
    const onsaved = vi.fn();
    mockFetch.mockResolvedValueOnce(reply(peoplePage)).mockResolvedValueOnce(reply({ createMovie: { id: 'm1' } }));
    render(MovieEditor, { movie: null, onsaved, onclose: vi.fn() });
    await fireEvent.input(screen.getByLabelText(/Title/), { target: { value: '  Heat  ' } });
    await fireEvent.input(screen.getByLabelText(/Genres/), { target: { value: 'Crime, crime, Drama' } });
    await fireEvent.submit(screen.getByRole('button', { name: 'Save movie' }).closest('form')!);
    await waitFor(() => expect(onsaved).toHaveBeenCalledOnce());
    const variables = bodyOf(1).variables.input;
    expect(variables.title).toBe('Heat');
    expect(variables.genres).toEqual(['Crime', 'Drama']);
  });

  it('shows server errors and stays open', async () => {
    const onsaved = vi.fn();
    mockFetch.mockResolvedValueOnce(reply(peoplePage)).mockResolvedValueOnce({
      ok: true, json: async () => ({ errors: [{ message: 'title already exists', extensions: { code: 'INVALID' } }] }),
    });
    render(MovieEditor, { movie: null, onsaved, onclose: vi.fn() });
    await fireEvent.input(screen.getByLabelText(/Title/), { target: { value: 'Heat' } });
    await fireEvent.submit(screen.getByRole('button', { name: 'Save movie' }).closest('form')!);
    expect(await screen.findByRole('alert')).toHaveTextContent('title already exists');
    expect(onsaved).not.toHaveBeenCalled();
  });

  it('closes on Escape and the close button', async () => {
    mockFetch.mockResolvedValue(reply(peoplePage));
    const onclose = vi.fn();
    render(MovieEditor, { movie: null, onsaved: vi.fn(), onclose });
    await fireEvent.keyDown(window, { key: 'Escape' });
    await fireEvent.click(screen.getByRole('button', { name: 'Close' }));
    expect(onclose).toHaveBeenCalledTimes(2);
  });
});

describe('PersonEditor', () => {
  it('rejects a future birth date and an invalid avatar before calling the server', async () => {
    render(PersonEditor, { person: null, onsaved: vi.fn(), onclose: vi.fn() });
    await fireEvent.input(screen.getByLabelText(/Name/), { target: { value: 'Ada' } });
    await fireEvent.input(screen.getByLabelText(/Birth date/), { target: { value: '2999-01-01' } });
    await fireEvent.submit(screen.getByRole('button', { name: 'Save person' }).closest('form')!);
    expect(await screen.findByRole('alert')).toHaveTextContent(/future/);

    await fireEvent.input(screen.getByLabelText(/Birth date/), { target: { value: '' } });
    const gif = new File(['x'], 'a.gif', { type: 'image/gif' });
    await fireEvent.change(screen.getByLabelText(/Photo/), { target: { files: [gif] } });
    await fireEvent.submit(screen.getByRole('button', { name: 'Save person' }).closest('form')!);
    expect(await screen.findByRole('alert')).toHaveTextContent(/JPEG, PNG or WebP/);
    expect(mockFetch).not.toHaveBeenCalled();
  });

  it('prefills when editing and sends the existing id', async () => {
    const person: Person = { id: 'p9', name: 'Ada', bio: 'Maths', birthDate: '1815-12-10', avatarUrl: null, filmography: [] };
    const onsaved = vi.fn();
    mockFetch.mockResolvedValue(reply({ updatePerson: { id: 'p9' } }));
    render(PersonEditor, { person, onsaved, onclose: vi.fn() });
    expect(screen.getByRole('heading', { name: 'Edit person' })).toBeInTheDocument();
    expect(screen.getByLabelText(/Name/)).toHaveValue('Ada');
    await fireEvent.submit(screen.getByRole('button', { name: 'Save person' }).closest('form')!);
    await waitFor(() => expect(onsaved).toHaveBeenCalledOnce());
    expect(bodyOf(0).variables.id).toBe('p9');
  });
});

describe('SearchResults', () => {
  const movie = { id: 'm1', title: 'Heat', releaseYear: 1995, genres: [], artworkUrl: null } as unknown as Movie;
  it('lists matches and reports which one to view', async () => {
    const onviewmovie = vi.fn();
    render(SearchResults, { results: { movies: [movie], people: [] }, onviewmovie, onviewperson: vi.fn() });
    expect(screen.getByText('1995 · Film')).toBeInTheDocument();
    await fireEvent.click(screen.getByRole('button', { name: 'View' }));
    expect(onviewmovie).toHaveBeenCalledWith('Heat');
  });
  it('shows an empty state', () => {
    render(SearchResults, { results: { movies: [], people: [] }, onviewmovie: vi.fn(), onviewperson: vi.fn() });
    expect(screen.getByText('No matches found.')).toBeInTheDocument();
  });
});

