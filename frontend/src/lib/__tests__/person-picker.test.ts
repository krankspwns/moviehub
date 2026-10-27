import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/svelte';
import MovieEditor from '../components/MovieEditor.svelte';

const mockFetch = vi.fn();
const person = (id: string, name: string) => ({ id, name, bio: '', birthDate: null, avatarUrl: null, filmography: [] });
const page = (items: ReturnType<typeof person>[]) => ({
  ok: true,
  json: async () => ({ data: { people: { items, total: items.length, page: 0, totalPages: 1 } } }),
});
const sent = () => mockFetch.mock.calls.map(([, options]) => JSON.parse(options.body));

beforeEach(() => {
  vi.stubGlobal('fetch', mockFetch);
  mockFetch.mockReset();
  mockFetch.mockImplementation(async (_url, options: RequestInit) => {
    const { query, variables } = JSON.parse(String(options.body));
    if (query.includes('createMovie')) return { ok: true, json: async () => ({ data: { createMovie: { id: 'm1' } } }) };
    const term = variables.query;
    if (term === 'zed') return page([]);
    if (term === 'ada') return page([person('p1', 'Ada Lovelace')]);
    return page([person('p1', 'Ada Lovelace'), person('p2', 'Alan Turing')]);
  });
});
afterEach(() => {
  cleanup();
  vi.unstubAllGlobals();
});

async function addCredit() {
  await fireEvent.click(screen.getByRole('button', { name: '+ Add credit' }));
  return screen.getByRole('combobox', { name: 'Person' });
}

describe('type-to-search person picker', () => {
  it('lists people when focused and searches with the complete typed text', async () => {
    render(MovieEditor, { movie: null, onsaved: vi.fn(), onclose: vi.fn() });
    const box = await addCredit();
    await fireEvent.focus(box);
    expect(await screen.findByRole('option', { name: 'Alan Turing' })).toBeInTheDocument();

    await fireEvent.input(box, { target: { value: 'ada' } });
    await waitFor(() => expect(sent().some((b) => b.variables.query === 'ada')).toBe(true));
    await waitFor(() => expect(screen.queryByRole('option', { name: 'Alan Turing' })).not.toBeInTheDocument());
    expect(screen.getByRole('option', { name: 'Ada Lovelace' })).toBeInTheDocument();
  });

  it('shows the chosen person in the field and saves their id', async () => {
    render(MovieEditor, { movie: null, onsaved: vi.fn(), onclose: vi.fn() });
    await fireEvent.input(screen.getByLabelText(/Title/), { target: { value: 'Heat' } });
    const box = await addCredit();
    await fireEvent.focus(box);
    await fireEvent.mouseDown(await screen.findByRole('option', { name: 'Alan Turing' }));
    expect(box).toHaveValue('Alan Turing');
    expect(screen.queryByRole('listbox')).not.toBeInTheDocument();

    await fireEvent.submit(screen.getByRole('button', { name: 'Save Movie' }).closest('form')!);
    await waitFor(() => expect(sent().some((b) => b.variables.input)).toBe(true));
    expect(sent().find((b) => b.variables.input)!.variables.input.credits[0].personId).toBe('p2');
  });

  it('picks with the keyboard: arrows move, Enter chooses', async () => {
    render(MovieEditor, { movie: null, onsaved: vi.fn(), onclose: vi.fn() });
    const box = await addCredit();
    await fireEvent.focus(box);
    await screen.findByRole('option', { name: 'Alan Turing' });
    await fireEvent.keyDown(box, { key: 'ArrowDown' });
    await fireEvent.keyDown(box, { key: 'Enter' });
    expect(box).toHaveValue('Alan Turing');
  });

  it('closes only the list on Escape, not the whole dialog', async () => {
    const onclose = vi.fn();
    render(MovieEditor, { movie: null, onsaved: vi.fn(), onclose });
    const box = await addCredit();
    await fireEvent.focus(box);
    await screen.findByRole('listbox');
    await fireEvent.keyDown(box, { key: 'Escape' });
    expect(screen.queryByRole('listbox')).not.toBeInTheDocument();
    expect(onclose).not.toHaveBeenCalled();
  });

  it('explains when nobody matches', async () => {
    render(MovieEditor, { movie: null, onsaved: vi.fn(), onclose: vi.fn() });
    const box = await addCredit();
    await fireEvent.input(box, { target: { value: 'zed' } });
    expect(await screen.findByText(/Nobody matches “zed”/)).toBeInTheDocument();
  });
});
