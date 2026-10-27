import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import {
  getMovies,
  graphql,
  imageProblem,
  listMovies,
  listPeople,
  saveMovie,
  uploadArtwork,
  uploadAvatar,
} from '../api/catalog';

const mockFetch = vi.fn();
beforeEach(() => {
  vi.stubGlobal('fetch', mockFetch);
});
afterEach(() => vi.unstubAllGlobals());

describe('catalog client', () => {
  it('passes search terms and paging as variables instead of interpolating them', async () => {
    mockFetch.mockResolvedValue({ ok: true, json: async () => ({ data: { movies: { items: [], total: 0 } } }) });
    await listMovies('" } injection', 2);
    const [url, options] = mockFetch.mock.calls[0];
    expect(url).toBe('/graphql');
    expect(JSON.parse(options.body).variables).toEqual({ query: '" } injection', page: 2 });
    expect(JSON.parse(options.body).query).not.toContain('injection');
  });

  it('passes a role filter as an enum variable', async () => {
    mockFetch.mockResolvedValue({ ok: true, json: async () => ({ data: { people: { items: [], total: 0 } } }) });
    await listPeople('Ada', 1, 'DIRECTOR');
    expect(JSON.parse(mockFetch.mock.calls[0][1].body).variables).toEqual({ query: 'Ada', page: 1, role: 'DIRECTOR' });
  });

  it('surfaces validation and network errors', async () => {
    mockFetch.mockResolvedValueOnce({
      ok: true,
      json: async () => ({ errors: [{ message: 'title must not be blank', extensions: { code: 'INVALID' } }] }),
    });
    await expect(graphql('query { movies { total } }')).rejects.toThrow('title must not be blank');
    mockFetch.mockRejectedValueOnce(new Error('offline'));
    await expect(graphql('query { movies { total } }')).rejects.toThrow('Cannot reach the server');
  });

  it('sends updates to the mutation with the existing ID', async () => {
    mockFetch.mockResolvedValue({ ok: true, json: async () => ({ data: { updateMovie: { id: 'm1' } } }) });
    await saveMovie(
      { title: 'One', synopsis: '', releaseYear: 2000, runtimeMinutes: 0, genres: [], credits: [] },
      'm1',
    );
    expect(JSON.parse(mockFetch.mock.calls[0][1].body).variables.id).toBe('m1');
    expect(JSON.parse(mockFetch.mock.calls[0][1].body).query).toContain('updateMovie');
  });
});

describe('artwork', () => {
  it('uploads a multipart file and reports progress', async () => {
    let xhr!: MockXHR;
    class MockXHR {
      constructor() {
        // eslint-disable-next-line @typescript-eslint/no-this-alias
        xhr = this;
      }
      upload: { onprogress?: (event: { lengthComputable: boolean; loaded: number; total: number }) => void } = {};
      onload?: () => void;
      status = 200;
      open = vi.fn();
      send = vi.fn((data: FormData) => {
        expect(data.get('file')).toBeInstanceOf(File);
        this.upload.onprogress?.({ lengthComputable: true, loaded: 2, total: 4 });
        this.onload?.();
      });
    }
    vi.stubGlobal('XMLHttpRequest', MockXHR);
    const progress = vi.fn();
    await uploadArtwork('a/b', new File(['image'], 'poster.png', { type: 'image/png' }), progress);
    expect(xhr.open).toHaveBeenCalledWith('POST', '/api/movies/a%2Fb/artwork');
    expect(progress).toHaveBeenCalledWith(50);
  });
});

describe('avatars and saved lookups', () => {
  it('uploads a person avatar to the people endpoint', async () => {
    const open = vi.fn();
    class MockXHR {
      upload = {};
      onload?: () => void;
      status = 200;
      open = open;
      send = vi.fn(() => this.onload?.());
    }
    vi.stubGlobal('XMLHttpRequest', MockXHR);
    await uploadAvatar('p/1', new File(['x'], 'a.png', { type: 'image/png' }), vi.fn());
    expect(open).toHaveBeenCalledWith('POST', '/api/people/p%2F1/avatar');
  });

  it('validates image type and size before uploading', () => {
    expect(imageProblem(null)).toBeNull();
    expect(imageProblem(new File(['x'], 'a.png', { type: 'image/png' }))).toBeNull();
    expect(imageProblem(new File(['x'], 'a.gif', { type: 'image/gif' }))).toMatch(/JPEG, PNG or WebP/);
    const big = new File([new Uint8Array(5 * 1024 * 1024 + 1)], 'big.png', { type: 'image/png' });
    expect(imageProblem(big)).toMatch(/5 MB/);
  });

  it('fetches saved movies in one query, keeps order and skips deleted ones', async () => {
    mockFetch.mockResolvedValue({
      ok: true,
      json: async () => ({ data: { m0: { id: 'a' }, m1: null, m2: { id: 'c' } } }),
    });
    const movies = await getMovies(['a', 'gone', 'c']);
    expect(movies.map((m) => m.id)).toEqual(['a', 'c']);
    const body = JSON.parse(mockFetch.mock.calls[0][1].body);
    expect(body.variables).toEqual({ id0: 'a', id1: 'gone', id2: 'c' });
    expect(body.query).not.toContain('gone');
    expect(mockFetch).toHaveBeenCalledTimes(1);
  });

  it('does not call the server for an empty saved list', async () => {
    mockFetch.mockClear();
    expect(await getMovies([])).toEqual([]);
    expect(mockFetch).not.toHaveBeenCalled();
  });
});
