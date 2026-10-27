import { afterEach, describe, expect, it, vi } from 'vitest';
import { cleanup, fireEvent, render, screen } from '@testing-library/svelte';
import MovieCard from '../components/MovieCard.svelte';
import MovieDetails from '../components/MovieDetails.svelte';
import PersonDetails from '../components/PersonDetails.svelte';
import type { Movie, Person } from '../api/catalog';

afterEach(cleanup);

const movie: Movie = {
  id: 'm1',
  title: 'Heat',
  synopsis: 'Cops and robbers.',
  releaseYear: 1995,
  runtimeMinutes: 170,
  genres: ['Crime'],
  artworkUrl: null,
  credits: [
    { personId: 'p1', personName: 'Michael Mann', role: 'DIRECTOR', characterName: '' },
    { personId: 'p2', personName: 'Al Pacino', role: 'ACTOR', characterName: 'Hanna' },
    { personId: 'p3', personName: 'Robert De Niro', role: 'ACTOR', characterName: '' },
  ],
};
const person: Person = {
  id: 'p2',
  name: 'Al Pacino',
  bio: 'Actor.',
  birthDate: '1940-04-25',
  avatarUrl: null,
  filmography: [{ movieId: 'm1', title: 'Heat', role: 'ACTOR' }],
};

describe('details popup', () => {
  it('opens from the card title link', async () => {
    const onopen = vi.fn();
    render(MovieCard, { movie, onedit: vi.fn(), ondelete: vi.fn(), onopen });
    await fireEvent.click(screen.getByRole('button', { name: 'Heat' }));
    expect(onopen).toHaveBeenCalledOnce();
  });

  it('shows full movie information with creators and cast roles', async () => {
    const onclose = vi.fn();
    const onedit = vi.fn();
    const ontoggle = vi.fn();
    render(MovieDetails, { movie, onclose, onedit, ontoggle });
    expect(screen.getByRole('heading', { name: 'Heat' })).toBeInTheDocument();
    expect(screen.getByText('Cops and robbers.')).toBeInTheDocument();
    expect(screen.getByText('Directed by')).toBeInTheDocument();
    expect(screen.getByText('Michael Mann')).toBeInTheDocument();
    expect(screen.getByText('as Hanna')).toBeInTheDocument();
    expect(screen.getByText('Robert De Niro')).toBeInTheDocument();
    await fireEvent.click(screen.getByRole('button', { name: 'Close details' }));
    await fireEvent.click(screen.getByRole('button', { name: 'Edit movie' }));
    await fireEvent.click(screen.getByRole('button', { name: 'Save Heat' }));
    expect(onclose).toHaveBeenCalledOnce();
    expect(onedit).toHaveBeenCalledOnce();
    expect(ontoggle).toHaveBeenCalledOnce();
  });

  it('handles movies without synopsis or credits', () => {
    render(MovieDetails, {
      movie: { ...movie, synopsis: '', credits: [], runtimeMinutes: 0 },
      onclose: vi.fn(),
      onedit: vi.fn(),
      ontoggle: vi.fn(),
    });
    expect(screen.getByText('No synopsis yet.')).toBeInTheDocument();
    expect(screen.getByText('No creators credited.')).toBeInTheDocument();
    expect(screen.getByText('No cast credited.')).toBeInTheDocument();
    expect(screen.getByText(/Runtime Not Set/)).toBeInTheDocument();
  });

  it('shows person details and filmography, including empty states', () => {
    const { unmount } = render(PersonDetails, { person, onclose: vi.fn(), onedit: vi.fn() });
    expect(screen.getByText('Born 1940-04-25')).toBeInTheDocument();
    expect(screen.getByText('Heat')).toBeInTheDocument();
    unmount();
    render(PersonDetails, {
      person: { ...person, bio: '', birthDate: null, filmography: [] },
      onclose: vi.fn(),
      onedit: vi.fn(),
    });
    expect(screen.getByText('No biography yet.')).toBeInTheDocument();
    expect(screen.getByText('Birth Date Unknown')).toBeInTheDocument();
    expect(screen.getByText('Not credited on any movie yet.')).toBeInTheDocument();
  });
});
