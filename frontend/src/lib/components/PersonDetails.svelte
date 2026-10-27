<script lang="ts">
  import type { Person } from '../api/catalog';
  import { artworkSrc, hue, initials, roleLabel } from '../format';
  import Icon from './Icon.svelte';
  import IconButton from './IconButton.svelte';

  let {
    person,
    saved = false,
    onclose,
    onedit,
    ontoggle,
  }: { person: Person; saved?: boolean; onclose: () => void; onedit: () => void; ontoggle?: () => void } = $props();

  let imageFailed = $state(false);
  const roles = $derived([...new Set(person.filmography.map((f) => f.role))]);
</script>

<div class="detail-head">
  <div class="avatar xlarge" style="--hue: {hue(person.name)}">
    {#if person.avatarUrl && !imageFailed}
      <img src={artworkSrc(person.avatarUrl)} alt="Photo of {person.name}" onerror={() => (imageFailed = true)} />
    {:else}<span aria-hidden="true">{initials(person.name)}</span>{/if}
  </div>
  <div class="detail-main">
    <div class="dialog-head">
      <h2>{person.name}</h2>
      <button class="close" aria-label="Close details" onclick={onclose}><Icon name="close" size={20} /></button>
    </div>
    <p class="meta detail-meta"><span>{person.birthDate ? `Born ${person.birthDate}` : 'Birth Date Unknown'}</span></p>
    {#if roles.length}<div class="chips">
        {#each roles as role (role)}<span class="chip">{roleLabel(role)}</span>{/each}
      </div>{/if}
    <p class="detail-text">{person.bio || 'No biography yet.'}</p>
    <div class="detail-actions">
      {#if ontoggle}
        <IconButton
          icon="bookmark"
          pressed={saved}
          label={saved ? `Remove ${person.name} from saved` : `Save ${person.name}`}
          onclick={ontoggle}
        />
      {/if}
      <button type="button" class="primary" onclick={onedit}
        ><Icon name="edit" size={16} /><span>Edit person</span></button
      >
    </div>
  </div>
</div>

<div class="form-section">
  <h3>Filmography <span class="hint">({person.filmography.length})</span></h3>
  {#if person.filmography.length}
    <ul class="people-list">
      {#each person.filmography as entry (entry.movieId + entry.role)}
        <li>
          <span class="mini-avatar square" style="--hue: {hue(entry.title)}" aria-hidden="true"
            ><Icon name="film" size={16} /></span
          >
          <span><strong>{entry.title}</strong><small>{roleLabel(entry.role)}</small></span>
        </li>
      {/each}
    </ul>
  {:else}<p class="hint">Not credited on any movie yet.</p>{/if}
</div>
