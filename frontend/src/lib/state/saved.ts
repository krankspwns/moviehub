const MOVIES_KEY = 'moviehub.saved.v1';
const PEOPLE_KEY = 'moviehub.saved.people.v1';

type ReadableStorage = Pick<Storage, 'getItem'>;
type WritableStorage = Pick<Storage, 'setItem'>;

function browserStorage(): Storage | null {
  try {
    return typeof localStorage === 'undefined' ? null : localStorage;
  } catch {
    return null;
  }
}

/** Reads saved ids; tolerates missing storage, corrupt JSON, wrong shapes and duplicates. */
function read(key: string, storage: ReadableStorage | null): string[] {
  if (!storage) return [];
  try {
    const parsed: unknown = JSON.parse(storage.getItem(key) ?? '[]');
    if (!Array.isArray(parsed)) return [];
    return [...new Set(parsed.filter((id): id is string => typeof id === 'string' && id.length > 0))];
  } catch {
    return [];
  }
}

/** Best effort: private mode / quota errors must never break the UI. */
function write(key: string, ids: string[], storage: WritableStorage | null): void {
  if (!storage) return;
  try {
    storage.setItem(key, JSON.stringify(ids));
  } catch {
    /* ignore */
  }
}

export const readSaved = (storage: ReadableStorage | null = browserStorage()): string[] => read(MOVIES_KEY, storage);
export const writeSaved = (ids: string[], storage: WritableStorage | null = browserStorage()): void =>
  write(MOVIES_KEY, ids, storage);

export const readSavedPeople = (storage: ReadableStorage | null = browserStorage()): string[] =>
  read(PEOPLE_KEY, storage);
export const writeSavedPeople = (ids: string[], storage: WritableStorage | null = browserStorage()): void =>
  write(PEOPLE_KEY, ids, storage);

/** Adds the id at the front (newest first) or removes it when already saved. */
export function toggleSaved(ids: string[], id: string): string[] {
  return ids.includes(id) ? ids.filter((existing) => existing !== id) : [id, ...ids];
}
