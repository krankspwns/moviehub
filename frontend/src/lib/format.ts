export function artworkSrc(url: string): string {
  return url.charAt(0) === '/' ? url : '/' + url;
}

export function roleLabel(role: string): string {
  return role.charAt(0) + role.slice(1).toLowerCase();
}

export function initials(name: string): string {
  const letters = name
    .trim()
    .split(/\s+/)
    .slice(0, 2)
    .map((word) => word.charAt(0).toUpperCase())
    .join('');
  return letters || '?';
}

/** Stable 0-359 hue derived from text, so each title/person keeps its own accent colour. */
export function hue(seed: string): number {
  let value = 0;
  for (let i = 0; i < seed.length; i++) value = (value * 31 + seed.charCodeAt(i)) % 360;
  return value;
}

export function errorMessage(e: unknown): string {
  return e instanceof Error ? e.message : 'Something went wrong. Please retry.';
}

/** Stops Enter inside a text field from submitting (saving) the whole form by accident. Textareas keep newlines. */
export function blockEnterSubmit(event: KeyboardEvent): void {
  const target = event.target;
  if (event.key === 'Enter' && target instanceof HTMLInputElement && !['button', 'submit'].includes(target.type)) {
    event.preventDefault();
  }
}
