import { cleanup, fireEvent, render, screen } from '@testing-library/svelte';
import { afterEach, describe, expect, it, vi } from 'vitest';
import type { Person } from '../api/catalog';
import Header from '../components/Header.svelte';
import PersonCard from '../components/PersonCard.svelte';
import PersonDetails from '../components/PersonDetails.svelte';

const person: Person = {
  id: 'p1',
  name: 'Ada Lovelace',
  bio: '',
  birthDate: '',
  avatarUrl: null,
  filmography: [],
};

describe('saving people', () => {
  afterEach(cleanup);
  it('toggles a person from the card', async () => {
    const ontoggle = vi.fn();
    render(PersonCard, { person, onedit: vi.fn(), ondelete: vi.fn(), ontoggle });
    await fireEvent.click(screen.getByRole('button', { name: 'Save Ada Lovelace' }));
    expect(ontoggle).toHaveBeenCalledOnce();
  });

  it('shows the saved state on the card and in the popup', () => {
    render(PersonCard, { person, saved: true, onedit: vi.fn(), ondelete: vi.fn(), ontoggle: vi.fn() });
    expect(screen.getByRole('button', { name: 'Remove Ada Lovelace from saved' })).toBeInTheDocument();
  });

  it('toggles a person from the details popup', async () => {
    const ontoggle = vi.fn();
    render(PersonDetails, { person, onclose: vi.fn(), onedit: vi.fn(), ontoggle });
    await fireEvent.click(screen.getByRole('button', { name: 'Save Ada Lovelace' }));
    expect(ontoggle).toHaveBeenCalledOnce();
  });

  it('hides the bookmark when no handler is given', () => {
    render(PersonCard, { person, onedit: vi.fn(), ondelete: vi.fn() });
    expect(screen.queryByRole('button', { name: /Save Ada Lovelace/ })).toBeNull();
  });
});

describe('header', () => {
  it('goes home when the MovieHub brand is clicked', async () => {
    const onhome = vi.fn();
    render(Header, { tab: 'people', savedCount: 0, theme: 'dark', onswitch: vi.fn(), ontheme: vi.fn(), onhome });
    await fireEvent.click(screen.getByRole('button', { name: 'MovieHub home' }));
    expect(onhome).toHaveBeenCalledOnce();
  });
});
