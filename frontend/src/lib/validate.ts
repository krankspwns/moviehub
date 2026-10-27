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

export function validateMovie(movie: MovieInput, genres: string[], now = new Date()): string | null {
  const title = movie.title.trim();
  if (!title) return 'Enter a movie title.';
  if (title.length > LIMITS.title) return `Title must be at most ${LIMITS.title} characters.`;
  if (movie.synopsis.trim().length > LIMITS.synopsis) return `Synopsis must be at most ${LIMITS.synopsis} characters.`;
  const maxYear = now.getFullYear() + LIMITS.yearsAhead;
  if (!Number.isInteger(movie.releaseYear) || movie.releaseYear < LIMITS.minYear || movie.releaseYear > maxYear) {
    return `Release year must be between ${LIMITS.minYear} and ${maxYear}.`;
  }
  const runtime = movie.runtimeMinutes;
  if (!Number.isInteger(runtime) || runtime < 0 || runtime > LIMITS.maxRuntime) {
    return `Runtime must be a whole number between 0 and ${LIMITS.maxRuntime} minutes (0 = unknown).`;
  }
  if (genres.length > LIMITS.maxGenres) return `Use at most ${LIMITS.maxGenres} genres.`;
  if (genres.some((g) => g.length > LIMITS.genreLength))
    return `Each genre must be at most ${LIMITS.genreLength} characters.`;
  if (movie.credits.some((c) => !c.personId)) return 'Choose a person for every credit.';
  if (movie.credits.some((c) => c.role === 'ACTOR' && c.characterName.trim().length > LIMITS.character)) {
    return `Character names must be at most ${LIMITS.character} characters.`;
  }
  if (new Set(movie.credits.map((c) => `${c.personId}:${c.role}`)).size !== movie.credits.length) {
    return 'Each person can only have one credit per role.';
  }
  return null;
}

export function validatePerson(person: PersonInput, now = new Date()): string | null {
  const name = person.name.trim();
  if (!name) return 'Enter a name.';
  if (name.length > LIMITS.name) return `Name must be at most ${LIMITS.name} characters.`;
  if (person.bio.trim().length > LIMITS.bio) return `Biography must be at most ${LIMITS.bio} characters.`;
  if (person.birthDate) {
    const date = new Date(`${person.birthDate}T00:00:00`);
    if (Number.isNaN(date.getTime())) return 'Birth date must be a valid date.';
    if (date.getTime() > now.getTime()) return 'Birth date cannot be in the future.';
  }
  return null;
}
