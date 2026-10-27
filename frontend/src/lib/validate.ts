import type { MovieInput, PersonInput } from './api/catalog';

/**
 * Client-side mirror of the catalog-service rules, so users get instant, specific messages.
 * The server stays the source of truth and re-validates everything.
 */
export const LIMITS = {
  title: 200,
  synopsis: 4000,
  minYear: 1888,
  yearsAhead: 10,
  maxRuntime: 1000,
  maxGenres: 10,
  genreLength: 50,
  character: 200,
  name: 200,
  bio: 4000,
} as const;

export type MovieField = 'title' | 'synopsis' | 'releaseYear' | 'runtimeMinutes' | 'genres' | 'credits';
export type PersonField = 'name' | 'bio' | 'birthDate';
export type FieldErrors<K extends string> = Partial<Record<K, string>>;

export function parseGenres(text: string): string[] {
  const seen = new Set<string>();
  return text
    .split(',')
    .map((g) => g.trim())
    .filter((g) => {
      const key = g.toLowerCase();
      if (!g || seen.has(key)) return false;
      seen.add(key);
      return true;
    });
}

/** Problems that can be shown while typing: too long, out of range, duplicated. Empty required fields are not included. */
export function movieFieldErrors(movie: MovieInput, genres: string[], now = new Date()): FieldErrors<MovieField> {
  const errors: FieldErrors<MovieField> = {};
  if (movie.title.trim().length > LIMITS.title) errors.title = `Title must be at most ${LIMITS.title} characters.`;
  if (movie.synopsis.trim().length > LIMITS.synopsis) {
    errors.synopsis = `Synopsis must be at most ${LIMITS.synopsis} characters.`;
  }
  const maxYear = now.getFullYear() + LIMITS.yearsAhead;
  if (!Number.isInteger(movie.releaseYear) || movie.releaseYear < LIMITS.minYear || movie.releaseYear > maxYear) {
    errors.releaseYear = `Release year must be between ${LIMITS.minYear} and ${maxYear}.`;
  }
  const runtime = movie.runtimeMinutes;
  if (!Number.isInteger(runtime) || runtime < 0 || runtime > LIMITS.maxRuntime) {
    errors.runtimeMinutes = `Runtime must be a whole number between 0 and ${LIMITS.maxRuntime} minutes (0 = unknown).`;
  }
  if (genres.length > LIMITS.maxGenres) errors.genres = `Use at most ${LIMITS.maxGenres} genres.`;
  else if (genres.some((g) => g.length > LIMITS.genreLength)) {
    errors.genres = `Each genre must be at most ${LIMITS.genreLength} characters.`;
  }
  if (movie.credits.some((c) => c.role === 'ACTOR' && c.characterName.trim().length > LIMITS.character)) {
    errors.credits = `Character names must be at most ${LIMITS.character} characters.`;
  } else if (new Set(movie.credits.map((c) => `${c.personId}:${c.role}`)).size !== movie.credits.length) {
    errors.credits = 'Each person can only have one credit per role.';
  }
  return errors;
}

export function validateMovie(movie: MovieInput, genres: string[], now = new Date()): string | null {
  if (!movie.title.trim()) return 'Enter a movie title.';
  if (movie.credits.some((c) => !c.personId)) return 'Choose a person for every credit.';
  return Object.values(movieFieldErrors(movie, genres, now))[0] ?? null;
}

export function personFieldErrors(person: PersonInput, now = new Date()): FieldErrors<PersonField> {
  const errors: FieldErrors<PersonField> = {};
  if (person.name.trim().length > LIMITS.name) errors.name = `Name must be at most ${LIMITS.name} characters.`;
  if (person.bio.trim().length > LIMITS.bio) errors.bio = `Biography must be at most ${LIMITS.bio} characters.`;
  if (person.birthDate) {
    const date = new Date(`${person.birthDate}T00:00:00`);
    if (Number.isNaN(date.getTime())) errors.birthDate = 'Birth date must be a valid date.';
    else if (date.getTime() > now.getTime()) errors.birthDate = 'Birth date cannot be in the future.';
  }
  return errors;
}

export function validatePerson(person: PersonInput, now = new Date()): string | null {
  if (!person.name.trim()) return 'Enter a name.';
  return Object.values(personFieldErrors(person, now))[0] ?? null;
}
