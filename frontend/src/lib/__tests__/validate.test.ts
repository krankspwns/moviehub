import { describe, expect, it } from 'vitest';
import { parseGenres, validateMovie, validatePerson } from '../validate';
import type { MovieInput } from '../api/catalog';

const now = new Date('2026-06-01T00:00:00');
const movie = (over: Partial<MovieInput> = {}): MovieInput => ({
  title: 'Heat', synopsis: '', releaseYear: 1995, runtimeMinutes: 170, genres: [], credits: [], ...over,
});

describe('validateMovie', () => {
  it('accepts a valid movie and boundary values', () => {
    expect(validateMovie(movie(), [], now)).toBeNull();
    expect(validateMovie(movie({ releaseYear: 1888, runtimeMinutes: 0 }), [], now)).toBeNull();
    expect(validateMovie(movie({ releaseYear: 2036, runtimeMinutes: 1000 }), [], now)).toBeNull();
    expect(validateMovie(movie({ title: 'x'.repeat(200) }), [], now)).toBeNull();
  });
  it('rejects blank or oversized text', () => {
    expect(validateMovie(movie({ title: '   ' }), [], now)).toMatch(/title/i);
    expect(validateMovie(movie({ title: 'x'.repeat(201) }), [], now)).toMatch(/200/);
    expect(validateMovie(movie({ synopsis: 'x'.repeat(4001) }), [], now)).toMatch(/4000/);
  });
  it('rejects out-of-range or fractional year and runtime', () => {
    expect(validateMovie(movie({ releaseYear: 1887 }), [], now)).toMatch(/year/i);
    expect(validateMovie(movie({ releaseYear: 2037 }), [], now)).toMatch(/year/i);
    expect(validateMovie(movie({ releaseYear: 1999.5 }), [], now)).toMatch(/year/i);
    expect(validateMovie(movie({ releaseYear: Number.NaN }), [], now)).toMatch(/year/i);
    expect(validateMovie(movie({ runtimeMinutes: -1 }), [], now)).toMatch(/runtime/i);
    expect(validateMovie(movie({ runtimeMinutes: 1001 }), [], now)).toMatch(/runtime/i);
  });
  it('limits genres', () => {
    expect(validateMovie(movie(), Array.from({ length: 11 }, (_, i) => `g${i}`), now)).toMatch(/10 genres/);
    expect(validateMovie(movie(), ['x'.repeat(51)], now)).toMatch(/50/);
  });
  it('checks credits', () => {
    const credit = { personId: 'p1', role: 'ACTOR' as const, characterName: '' };
    expect(validateMovie(movie({ credits: [{ ...credit, personId: '' }] }), [], now)).toMatch(/person/i);
    expect(validateMovie(movie({ credits: [credit, credit] }), [], now)).toMatch(/one credit/);
    expect(validateMovie(movie({ credits: [{ ...credit, characterName: 'x'.repeat(201) }] }), [], now)).toMatch(/200/);
    expect(validateMovie(movie({ credits: [credit, { ...credit, role: 'DIRECTOR' }] }), [], now)).toBeNull();
  });
});

describe('validatePerson', () => {
  it('validates name, bio and birth date', () => {
    expect(validatePerson({ name: 'Ada', bio: '', birthDate: '' }, now)).toBeNull();
    expect(validatePerson({ name: '  ', bio: '', birthDate: '' }, now)).toMatch(/name/i);
    expect(validatePerson({ name: 'x'.repeat(201), bio: '', birthDate: '' }, now)).toMatch(/200/);
    expect(validatePerson({ name: 'Ada', bio: 'x'.repeat(4001), birthDate: '' }, now)).toMatch(/4000/);
    expect(validatePerson({ name: 'Ada', bio: '', birthDate: '2027-01-01' }, now)).toMatch(/future/);
    expect(validatePerson({ name: 'Ada', bio: '', birthDate: '2026-06-01' }, now)).toBeNull();
    expect(validatePerson({ name: 'Ada', bio: '', birthDate: 'nope' }, now)).toMatch(/valid/);
  });
});

describe('parseGenres', () => {
  it('trims, drops blanks and de-duplicates case-insensitively', () => {
    expect(parseGenres(' Drama, ,drama,Crime ,')).toEqual(['Drama', 'Crime']);
    expect(parseGenres('')).toEqual([]);
  });
});

