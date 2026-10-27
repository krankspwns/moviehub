import { afterEach, describe, expect, it, vi } from 'vitest';
import { cleanup, fireEvent, render, screen } from '@testing-library/svelte';
import MovieCard from '../components/MovieCard.svelte';
import PersonCard from '../components/PersonCard.svelte';
import { hue, initials, roleLabel, artworkSrc } from '../format';
import type { Movie, Person } from '../api/catalog';

afterEach(cleanup);

const movie: Movie = {
  id: 'm1',
  title: 'Arrival',
  synopsis: 'Linguist meets aliens.',
  releaseYear: 2016,
  runtimeMinutes: 116,
  genres: ['Sci-Fi', 'Drama', 'Mystery', 'Thriller'],
  artworkUrl: null,
  credits: [
    { personId: 'p1', personName: 'Denis Villeneuve', role: 'DIRECTOR', characterName: '' },
    { personId: 'p2', personName: 'Amy Adams', role: 'ACTOR', characterName: 'Louise' },
  ],
};
const person: Person = {
  id: 'p2',
  name: 'Amy Adams',
  bio: '',
  birthDate: null,
  avatarUrl: null,
  filmography: [{ movieId: 'm1', title: 'Arrival', role: 'ACTOR' }],
};

describe('MovieCard', () => {
  it('exposes labelled icon buttons that call the edit and delete handlers', async () => {
    const onedit = vi.fn();
    const ondelete = vi.fn();
    render(MovieCard, { movie, onedit, ondelete });
    await fireEvent.click(screen.getByRole('button', { name: 'Edit Arrival' }));
    await fireEvent.click(screen.getByRole('button', { name: 'Delete Arrival' }));
    expect(onedit).toHaveBeenCalledOnce();
    expect(ondelete).toHaveBeenCalledOnce();
  });

  it('disables the actions while busy', () => {
    render(MovieCard, { movie, busy: true, onedit: vi.fn(), ondelete: vi.fn() });
    expect(screen.getByRole('button', { name: 'Edit Arrival' })).toBeDisabled();
    expect(screen.getByRole('button', { name: 'Delete Arrival' })).toBeDisabled();
  });

  it('shows a fallback poster without artwork and a compact genre summary', () => {
    render(MovieCard, { movie, onedit: vi.fn(), ondelete: vi.fn() });
    expect(screen.getByRole('img', { name: 'No artwork available' })).toBeInTheDocument();
    expect(screen.getByText('Sci-Fi')).toBeInTheDocument();
    expect(screen.getByText('+3')).toBeInTheDocument();
    expect(screen.queryByText('Mystery')).not.toBeInTheDocument();
    expect(screen.getByText(/2016 · 116 min/)).toBeInTheDocument();
  });

  it('falls back to the placeholder when the image fails to load', async () => {
    render(MovieCard, {
      movie: { ...movie, artworkUrl: '/api/movies/m1/artwork?v=1' },
      onedit: vi.fn(),
      ondelete: vi.fn(),
    });
    await fireEvent.error(screen.getByRole('img', { name: 'Artwork for Arrival' }));
    expect(screen.getByRole('img', { name: 'No artwork available' })).toBeInTheDocument();
  });

  it('offers a save toggle that reflects the saved state', async () => {
    const ontoggle = vi.fn();
    const { rerender } = render(MovieCard, { movie, onedit: vi.fn(), ondelete: vi.fn(), ontoggle });
    const save = screen.getByRole('button', { name: 'Save Arrival' });
    expect(save).toHaveAttribute('aria-pressed', 'false');
    await fireEvent.click(save);
    expect(ontoggle).toHaveBeenCalledOnce();
    await rerender({ saved: true });
    expect(screen.getByRole('button', { name: 'Remove Arrival from saved' })).toHaveAttribute('aria-pressed', 'true');
  });

  it('hides the save toggle when no handler is provided', () => {
    render(MovieCard, { movie, onedit: vi.fn(), ondelete: vi.fn() });
    expect(screen.queryByRole('button', { name: /Save Arrival/ })).not.toBeInTheDocument();
  });

  it('handles unset runtime', () => {
    render(MovieCard, { movie: { ...movie, runtimeMinutes: 0 }, onedit: vi.fn(), ondelete: vi.fn() });
    expect(screen.getByText(/Runtime Not Set/)).toBeInTheDocument();
  });
});

describe('PersonCard', () => {
  it('shows icon actions, role chips and handles a missing birth date', async () => {
    const onedit = vi.fn();
    const ondelete = vi.fn();
    render(PersonCard, { person, onedit, ondelete });
    expect(screen.getByText('Birth Date Unknown')).toBeInTheDocument();
    expect(screen.getByText('Actor')).toBeInTheDocument();
    await fireEvent.click(screen.getByRole('button', { name: 'Edit Amy Adams' }));
    await fireEvent.click(screen.getByRole('button', { name: 'Delete Amy Adams' }));
    expect(onedit).toHaveBeenCalledOnce();
    expect(ondelete).toHaveBeenCalledOnce();
  });

  it('shows the uploaded avatar and falls back to initials when it fails to load', async () => {
    render(PersonCard, {
      person: { ...person, avatarUrl: '/api/people/p2/avatar?v=1' },
      onedit: vi.fn(),
      ondelete: vi.fn(),
    });
    await fireEvent.error(screen.getByRole('img', { name: 'Photo of Amy Adams' }));
    expect(screen.queryByRole('img', { name: 'Photo of Amy Adams' })).not.toBeInTheDocument();
    expect(screen.getByText('AA')).toBeInTheDocument();
  });
});

describe('format helpers', () => {
  it('builds initials from one, two and many words, and handles blanks', () => {
    expect(initials('Amy')).toBe('A');
    expect(initials('amy lou adams')).toBe('AL');
    expect(initials('   ')).toBe('?');
  });
  it('produces a stable hue within 0-359', () => {
    expect(hue('Arrival')).toBe(hue('Arrival'));
    expect(hue('')).toBe(0);
    expect(hue('x'.repeat(500))).toBeLessThan(360);
  });
  it('formats roles and artwork paths', () => {
    expect(roleLabel('DIRECTOR')).toBe('Director');
    expect(artworkSrc('/api/a')).toBe('/api/a');
    expect(artworkSrc('api/a')).toBe('/api/a');
  });
});
