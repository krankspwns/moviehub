<script lang="ts">
  import type { Movie, Person } from '../api/catalog';
  import { artworkSrc, hue, initials } from '../format';
  import Icon from './Icon.svelte';

  let {
    results,
    loading = false,
    onviewmovie,
    onviewperson,
  }: {
    results: { movies: Movie[]; people: Person[] };
    loading?: boolean;
    onviewmovie: (title: string) => void;
    onviewperson: (name: string) => void;
  } = $props();
</script>

{#if !results.movies.length && !results.people.length && !loading}<p class="empty">No matches found.</p>{/if}
<div class="search-results">
  <h3>Movies <span>{results.movies.length}</span></h3>
  {#each results.movies as movie (movie.id)}
    <div class="result-row">
      <div class="result-thumb" style="--hue: {hue(movie.title)}">
        {#if movie.artworkUrl}<img src={artworkSrc(movie.artworkUrl)} alt="" />{:else}<Icon
            name="film"
            size={20}
          />{/if}
      </div>
      <div class="result-text">
        <strong>{movie.title}</strong>
        <p>{movie.releaseYear} · {movie.genres.join(', ') || 'Film'}</p>
      </div>
      <button onclick={() => onviewmovie(movie.title)}>View</button>
    </div>
  {/each}
  <h3>People <span>{results.people.length}</span></h3>
  {#each results.people as person (person.id)}
    <div class="result-row">
      <div class="result-thumb round" style="--hue: {hue(person.name)}">
        {#if person.avatarUrl}<img src={artworkSrc(person.avatarUrl)} alt="" />{:else}{initials(person.name)}{/if}
      </div>
      <div class="result-text">
        <strong>{person.name}</strong>
        <p>{person.filmography.length} film credits</p>
      </div>
      <button onclick={() => onviewperson(person.name)}>View</button>
    </div>
  {/each}
</div>
