export type Role = 'ACTOR' | 'DIRECTOR' | 'WRITER' | 'PRODUCER' | 'COMPOSER';
export const roles: Role[] = ['ACTOR', 'DIRECTOR', 'WRITER', 'PRODUCER', 'COMPOSER'];

export interface Credit { personId: string; personName: string; role: Role; characterName: string }
export interface CreditInput { personId: string; role: Role; characterName: string }
export interface Movie {
  id: string; title: string; synopsis: string; releaseYear: number; runtimeMinutes: number;
  genres: string[]; artworkUrl: string | null; credits: Credit[];
}
export interface Person {
  id: string; name: string; bio: string; birthDate: string | null; avatarUrl: string | null;
  filmography: { movieId: string; title: string; role: Role }[];
}
export interface Page<T> { items: T[]; total: number; page: number; pageSize: number; totalPages: number }
export interface MovieInput {
  title: string; synopsis: string; releaseYear: number; runtimeMinutes: number;
  genres: string[]; credits: CreditInput[];
}
export interface PersonInput { name: string; bio: string; birthDate: string }

const movieFields = 'id title synopsis releaseYear runtimeMinutes genres artworkUrl credits { personId personName role characterName }';
const personFields = 'id name bio birthDate avatarUrl filmography { movieId title role }';

export async function graphql<T>(query: string, variables: Record<string, unknown> = {}): Promise<T> {
  let response: Response;
  try {
    response = await fetch('/graphql', {
      method: 'POST', headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ query, variables }),
    });
  } catch {
    throw new Error('Cannot reach the server. Check your connection and retry.');
  }
  if (!response.ok) throw new Error(`Server error (${response.status}). Please retry.`);
  let result: { data?: T; errors?: { message: string; extensions?: { code?: string } }[] };
  try { result = await response.json(); }
  catch { throw new Error('The server returned an invalid response.'); }
  if (result.errors?.length) {
    const error = result.errors[0];
    throw new Error(error.extensions?.code === 'INVALID' ? error.message :
      error.extensions?.code === 'NOT_FOUND' ? error.message :
      error.extensions?.code === 'UNAVAILABLE' ? 'The catalog is unavailable. Please retry.' :
      error.extensions?.code === 'INTERNAL' ? 'The server could not complete the request. Please retry.' : error.message);
  }
  if (!result.data) throw new Error('The server returned no data.');
  return result.data;
}

export async function listMovies(query = '', page = 0): Promise<Page<Movie>> {
  const data = await graphql<{ movies: Page<Movie> }>(
    `query ($query: String, $page: Int) { movies(query: $query, page: $page) { items { ${movieFields} } total page pageSize totalPages } }`,
    { query, page });
  return data.movies;
}
export async function listPeople(query = '', page = 0, role: Role | null = null): Promise<Page<Person>> {
  const data = await graphql<{ people: Page<Person> }>(
    `query ($query: String, $page: Int, $role: Role) { people(query: $query, page: $page, role: $role) { items { ${personFields} } total page pageSize totalPages } }`,
    { query, page, role });
  return data.people;
}
export async function search(query: string): Promise<{ movies: Movie[]; people: Person[] }> {
  const data = await graphql<{ search: { movies: Movie[]; people: Person[] } }>(
    `query ($query: String!) { search(query: $query) { movies { ${movieFields} } people { ${personFields} } } }`, { query });
  return data.search;
}
export async function saveMovie(input: MovieInput, id?: string): Promise<Movie> {
  const data = await graphql<{ createMovie?: Movie; updateMovie?: Movie }>(
    id ? `mutation ($id: ID!, $input: MovieInput!) { updateMovie(id: $id, input: $input) { ${movieFields} } }`
       : `mutation ($input: MovieInput!) { createMovie(input: $input) { ${movieFields} } }`, { id, input });
  return (id ? data.updateMovie : data.createMovie)!;
}
export async function savePerson(input: PersonInput, id?: string): Promise<Person> {
  const data = await graphql<{ createPerson?: Person; updatePerson?: Person }>(
    id ? `mutation ($id: ID!, $input: PersonInput!) { updatePerson(id: $id, input: $input) { ${personFields} } }`
       : `mutation ($input: PersonInput!) { createPerson(input: $input) { ${personFields} } }`, { id, input });
  return (id ? data.updatePerson : data.createPerson)!;
}
export async function deleteMovie(id: string): Promise<boolean> {
  return (await graphql<{ deleteMovie: boolean }>('mutation ($id: ID!) { deleteMovie(id: $id) }', { id })).deleteMovie;
}
export async function deletePerson(id: string): Promise<boolean> {
  return (await graphql<{ deletePerson: boolean }>('mutation ($id: ID!) { deletePerson(id: $id) }', { id })).deletePerson;
}

/** Fetches several movies by id in one round trip; ids that no longer exist are omitted. Order is preserved. */
export async function getMovies(ids: string[]): Promise<Movie[]> {
  if (!ids.length) return [];
  // Aliases and variable names are generated from the index only; ids always travel as variables.
  const declarations = ids.map((_, i) => `$id${i}: ID!`).join(', ');
  const selections = ids.map((_, i) => `m${i}: movie(id: $id${i}) { ${movieFields} }`).join(' ');
  const variables: Record<string, unknown> = {};
  ids.forEach((id, i) => { variables[`id${i}`] = id; });
  const data = await graphql<Record<string, Movie | null>>(`query (${declarations}) { ${selections} }`, variables);
  return ids.map((_, i) => data[`m${i}`]).filter((movie): movie is Movie => movie != null);
}

function uploadImage(path: string, file: File, onProgress: (percent: number) => void): Promise<void> {
  return new Promise((resolve, reject) => {
    const xhr = new XMLHttpRequest();
    xhr.open('POST', path);
    xhr.upload.onprogress = (event) => {
      if (event.lengthComputable) onProgress(Math.round(event.loaded / event.total * 100));
    };
    xhr.onerror = () => reject(new Error('Upload failed. Check your connection and retry.'));
    xhr.onload = () => {
      if (xhr.status >= 200 && xhr.status < 300) { resolve(); return; }
      let detail = 'Upload failed. Please retry.';
      try { detail = JSON.parse(xhr.responseText).detail || detail; } catch { /* non-JSON response */ }
      reject(new Error(detail));
    };
    const form = new FormData();
    form.append('file', file);
    xhr.send(form);
  });
}

export function uploadArtwork(id: string, file: File, onProgress: (percent: number) => void): Promise<void> {
  return uploadImage(`/api/movies/${encodeURIComponent(id)}/artwork`, file, onProgress);
}

export function uploadAvatar(id: string, file: File, onProgress: (percent: number) => void): Promise<void> {
  return uploadImage(`/api/people/${encodeURIComponent(id)}/avatar`, file, onProgress);
}

export const MAX_IMAGE_BYTES = 5 * 1024 * 1024;

/** Client-side pre-check mirroring the server rules; returns a message or null when the file is acceptable. */
export function imageProblem(file: File | null): string | null {
  if (!file) return null;
  if (!['image/jpeg', 'image/png', 'image/webp'].includes(file.type) || file.size > MAX_IMAGE_BYTES) {
    return 'Image must be a JPEG, PNG or WebP file smaller than 5 MB.';
  }
  return null;
}




