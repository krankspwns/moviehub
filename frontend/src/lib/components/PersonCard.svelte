<script lang="ts">
  import type { Person } from '../api/catalog';
  import { artworkSrc, hue, initials, roleLabel } from '../format';
  import IconButton from './IconButton.svelte';

  let { person, busy = false, onedit, ondelete }: {
    person: Person;
    busy?: boolean;
    onedit: () => void;
    ondelete: () => void;
  } = $props();

  let imageFailed = $state(false);
  const roles = $derived([...new Set(person.filmography.map((f) => f.role))]);
  const titles = $derived([...new Set(person.filmography.map((f) => f.title))]);
</script>

<article class="card person-card">
  <div class="avatar" style="--hue: {hue(person.name)}">
    {#if person.avatarUrl && !imageFailed}
      <img src={artworkSrc(person.avatarUrl)} alt="Photo of {person.name}" loading="lazy" onerror={() => (imageFailed = true)} />
    {:else}
      <span aria-hidden="true">{initials(person.name)}</span>
    {/if}
  </div>
  <div class="card-body">
    <h3 title={person.name}>{person.name}</h3>
    <p class="meta">{person.birthDate ? `Born ${person.birthDate}` : 'Birth date unknown'}</p>
    {#if roles.length}
      <div class="chips">{#each roles as role}<span class="chip">{roleLabel(role)}</span>{/each}</div>
    {/if}
    {#if person.bio}<p class="synopsis">{person.bio}</p>{/if}
    {#if titles.length}
      <p class="credit-line">
        <span><b>{titles.length} {titles.length === 1 ? 'film' : 'films'}</b> {titles.slice(0, 3).join(', ')}{titles.length > 3 ? ` +${titles.length - 3}` : ''}</span>
      </p>
    {/if}
  </div>
  <div class="card-actions inline">
    <IconButton icon="edit" label="Edit {person.name}" disabled={busy} onclick={onedit} />
    <IconButton icon="trash" label="Delete {person.name}" variant="danger" disabled={busy} onclick={ondelete} />
  </div>
</article>

