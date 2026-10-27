import { describe, expect, it, vi } from 'vitest';
import { readSaved, toggleSaved, writeSaved } from '../state/saved';

const storageWith = (value: string | null) => ({ getItem: () => value });

describe('saved list', () => {
  it('toggles ids, newest first, without mutating the input', () => {
    const original = ['a'];
    expect(toggleSaved(original, 'b')).toEqual(['b', 'a']);
    expect(toggleSaved(['b', 'a'], 'b')).toEqual(['a']);
    expect(original).toEqual(['a']);
  });

  it('reads valid ids and de-duplicates them', () => {
    expect(readSaved(storageWith('["a","b","a"]'))).toEqual(['a', 'b']);
  });

  it('tolerates missing, corrupt or wrongly shaped data', () => {
    expect(readSaved(storageWith(null))).toEqual([]);
    expect(readSaved(storageWith('not json'))).toEqual([]);
    expect(readSaved(storageWith('{"a":1}'))).toEqual([]);
    expect(readSaved(storageWith('["a",1,null,""]'))).toEqual(['a']);
    expect(readSaved(null)).toEqual([]);
  });

  it('survives unavailable or full storage when writing', () => {
    const setItem = vi.fn(() => {
      throw new Error('quota');
    });
    expect(() => writeSaved(['a'], { setItem })).not.toThrow();
    expect(() => writeSaved(['a'], null)).not.toThrow();
    const ok = vi.fn();
    writeSaved(['a', 'b'], { setItem: ok });
    expect(ok).toHaveBeenCalledWith('moviehub.saved.v1', '["a","b"]');
  });
});
