<script lang="ts">
  import type { Movie } from '../api/catalog';
  import { artworkSrc, hue, initials } from '../format';
  import Icon from './Icon.svelte';
  import IconButton from './IconButton.svelte';

  let {
    movie,
    busy = false,
    saved = false,
    onopen,
    onedit,
    ondelete,
    ontoggle,
  }: {
    movie: Movie;
    busy?: boolean;
    saved?: boolean;
    onedit: () => void;
    ondelete: () => void;
    ontoggle?: () => void;
    onopen?: () => void;
  } = $props();

  let imageFailed = $state(false);
</script>

<article class="card movie-card">
  <div class="poster" style="--hue: {hue(movie.title)}">
    {#if movie.artworkUrl && !imageFailed}
      <img
        src={artworkSrc(movie.artworkUrl)}
        alt="Artwork for {movie.title}"
        loading="lazy"
        onerror={() => (imageFailed = true)}
      />
    {:else}
      <div class="poster-fallback" role="img" aria-label="No artwork available">
        <Icon name="film" size={34} />
        <span>{initials(movie.title)}</span>
      </div>
    {/if}
    {#if ontoggle}
      <div class="save-action" class:is-saved={saved}>
        <IconButton
          icon="bookmark"
          pressed={saved}
          label={saved ? `Remove ${movie.title} from saved` : `Save ${movie.title}`}
          onclick={ontoggle}
        />
      </div>
    {/if}
    <div class="card-actions">
      <IconButton icon="edit" label="Edit {movie.title}" disabled={busy} onclick={onedit} />
      <IconButton icon="trash" label="Delete {movie.title}" variant="danger" disabled={busy} onclick={ondelete} />
    </div>
  </div>
  <div class="card-body">
    <h3 title={movie.title}>
      {#if onopen}<button type="button" class="card-link" onclick={onopen}>{movie.title}</button
        >{:else}{movie.title}{/if}
    </h3>
    <p class="meta">
      {movie.releaseYear} · {#if movie.runtimeMinutes}{movie.runtimeMinutes} min{:else}Runtime Not Set{/if}
    </p>
    {#if movie.genres.length}
      <div class="chips">
        <span class="chip">{movie.genres[0]}</span>
        {#if movie.genres.length > 1}<span class="chip more">+{movie.genres.length - 1}</span>{/if}
      </div>
    {/if}
  </div>
</article>
