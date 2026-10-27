const KEY = 'moviehub.saved.v1';

type ReadableStorage = Pick<Storage, 'getItem'>;
type WritableStorage = Pick<Storage, 'setItem'>;

function browserStorage(): Storage | null {
  try {
    return typeof localStorage === 'undefined' ? null : localStorage;
  } catch {
    return null;
  }
}

/** Reads saved movie ids; tolerates missing storage, corrupt JSON, wrong shapes and duplicates. */
export function readSaved(storage: ReadableStorage | null = browserStorage()): string[] {
  if (!storage) return [];
  try {
    const parsed: unknown = JSON.parse(storage.getItem(KEY) ?? '[]');
    if (!Array.isArray(parsed)) return [];
    return [...new Set(parsed.filter((id): id is string => typeof id === 'string' && id.length > 0))];
  } catch {
    return [];
  }
}

/** Best effort: private mode / quota errors must never break the UI. */
export function writeSaved(ids: string[], storage: WritableStorage | null = browserStorage()): void {
  if (!storage) return;
  try {
    storage.setItem(KEY, JSON.stringify(ids));
  } catch {
    /* ignore */
  }
}

/** Adds the id at the front (newest first) or removes it when already saved. */
export function toggleSaved(ids: string[], id: string): string[] {
  return ids.includes(id) ? ids.filter((existing) => existing !== id) : [id, ...ids];
}
