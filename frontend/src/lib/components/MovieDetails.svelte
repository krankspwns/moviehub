<script lang="ts">
  import type { Movie, Role } from '../api/catalog';
  import { artworkSrc, hue, initials } from '../format';
  import Icon from './Icon.svelte';
  import IconButton from './IconButton.svelte';

  let {
    movie,
    saved = false,
    onclose,
    onedit,
    ontoggle,
  }: {
    movie: Movie;
    saved?: boolean;
    onclose: () => void;
    onedit: () => void;
    ontoggle: () => void;
  } = $props();

  let imageFailed = $state(false);

  const groups: { role: Role; label: string }[] = [
    { role: 'DIRECTOR', label: 'Directed by' },
    { role: 'WRITER', label: 'Written by' },
    { role: 'PRODUCER', label: 'Produced by' },
    { role: 'COMPOSER', label: 'Music by' },
  ];
  const crew = $derived(
    groups
      .map((g) => ({ label: g.label, names: movie.credits.filter((c) => c.role === g.role).map((c) => c.personName) }))
      .filter((g) => g.names.length),
  );
  const cast = $derived(movie.credits.filter((c) => c.role === 'ACTOR'));
</script>

<div class="detail-head">
  <div class="detail-poster" style="--hue: {hue(movie.title)}">
    {#if movie.artworkUrl && !imageFailed}
      <img src={artworkSrc(movie.artworkUrl)} alt="Artwork for {movie.title}" onerror={() => (imageFailed = true)} />
    {:else}
      <div class="poster-fallback" role="img" aria-label="No artwork available">
        <Icon name="film" size={34} /><span>{initials(movie.title)}</span>
      </div>
    {/if}
  </div>
  <div class="detail-main">
    <div class="dialog-head">
      <h2>{movie.title}</h2>
      <button class="close" aria-label="Close details" onclick={onclose}><Icon name="close" size={20} /></button>
    </div>
    <p class="meta detail-meta">
      <span>{movie.releaseYear}</span>
      <span
        >{#if movie.runtimeMinutes}<Icon name="clock" size={13} /> {movie.runtimeMinutes} min{:else}Runtime Not Set{/if}</span
      >
    </p>
    {#if movie.genres.length}<div class="chips">
        {#each movie.genres as genre (genre)}<span class="chip">{genre}</span>{/each}
      </div>{/if}
    <p class="detail-text">{movie.synopsis || 'No synopsis yet.'}</p>
    <div class="detail-actions">
      <IconButton
        icon="bookmark"
        pressed={saved}
        label={saved ? `Remove ${movie.title} from saved` : `Save ${movie.title}`}
        onclick={ontoggle}
      />
      <button type="button" class="primary" onclick={onedit}
        ><Icon name="edit" size={16} /><span>Edit Movie</span></button
      >
    </div>
  </div>
</div>

<div class="form-section">
  <h3>Creators</h3>
  {#if crew.length}
    <dl class="credit-list">
      {#each crew as group (group.label)}<div>
          <dt>{group.label}</dt>
          <dd>{group.names.join(', ')}</dd>
        </div>{/each}
    </dl>
  {:else}<p class="hint">No creators credited.</p>{/if}
</div>
<div class="form-section">
  <h3>Cast</h3>
  {#if cast.length}
    <ul class="people-list">
      {#each cast as credit (credit.personId)}
        <li>
          <span class="mini-avatar" style="--hue: {hue(credit.personName)}" aria-hidden="true"
            >{initials(credit.personName)}</span
          >
          <span
            ><strong>{credit.personName}</strong>{#if credit.characterName}<small>as {credit.characterName}</small
              >{/if}</span
          >
        </li>
      {/each}
    </ul>
  {:else}<p class="hint">No cast credited.</p>{/if}
</div>
