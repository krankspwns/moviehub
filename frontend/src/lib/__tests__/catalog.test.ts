import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { graphql, listMovies, listPeople, saveMovie, uploadArtwork } from '../api/catalog';

const mockFetch = vi.fn();
beforeEach(() => { vi.stubGlobal('fetch', mockFetch); });
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
    mockFetch.mockResolvedValueOnce({ ok: true, json: async () => ({ errors: [{ message: 'title must not be blank', extensions: { code: 'INVALID' } }] }) });
    await expect(graphql('query { movies { total } }')).rejects.toThrow('title must not be blank');
    mockFetch.mockRejectedValueOnce(new Error('offline'));
    await expect(graphql('query { movies { total } }')).rejects.toThrow('Cannot reach the server');
  });

  it('sends updates to the mutation with the existing ID', async () => {
    mockFetch.mockResolvedValue({ ok: true, json: async () => ({ data: { updateMovie: { id: 'm1' } } }) });
    await saveMovie({ title: 'One', synopsis: '', releaseYear: 2000, runtimeMinutes: 0, genres: [], credits: [] }, 'm1');
    expect(JSON.parse(mockFetch.mock.calls[0][1].body).variables.id).toBe('m1');
    expect(JSON.parse(mockFetch.mock.calls[0][1].body).query).toContain('updateMovie');
  });
});

describe('artwork', () => {
  it('uploads a multipart file and reports progress', async () => {
    let xhr!: MockXHR;
    class MockXHR {
      constructor() { xhr = this; }
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


