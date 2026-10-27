<script lang="ts">
  import { onMount } from 'svelte';
  import {
    deleteMovie, deletePerson, listMovies, listPeople, roles, saveMovie, savePerson, search, uploadArtwork,
    type CreditInput, type Movie, type MovieInput, type Page, type Person, type PersonInput, type Role,
  } from './lib/api/catalog';
  import { artworkSrc, hue, initials, roleLabel } from './lib/format';
  import Icon from './lib/components/Icon.svelte';
  import MovieCard from './lib/components/MovieCard.svelte';
  import PersonCard from './lib/components/PersonCard.svelte';

  type Tab = 'movies' | 'people' | 'search';
  let tab: Tab = 'movies';
  let query = '';
  let roleFilter: Role | '' = '';
  let page = 0;
  let movies: Page<Movie> | null = null;
  let people: Page<Person> | null = null;
  let results: { movies: Movie[]; people: Person[] } | null = null;
  let loading = false;
  let busy = false;
  let error = '';
  let notice = '';
  let requestId = 0;
  let timer: ReturnType<typeof setTimeout>;

  let modal: 'movie' | 'person' | null = null;
  let editingMovie: Movie | null = null;
  let editingPerson: Person | null = null;
  let movieForm: MovieInput = blankMovie();
  let personForm: PersonInput = { name: '', bio: '', birthDate: '' };
  let genreText = '';
  let file: File | null = null;
  let uploadPercent: number | null = null;
  let pickerQuery = '';
  let pickerPeople: Person[] = [];
  let pickerPage = 0;
  let pickerHasMore = false;
  let pickerBusy = false;
  let pickerRequestId = 0;
  let knownPeople: Record<string, string> = {};

  function blankMovie(): MovieInput {
    return { title: '', synopsis: '', releaseYear: new Date().getFullYear(), runtimeMinutes: 0, genres: [], credits: [] };
  }
  function message(e: unknown): string { return e instanceof Error ? e.message : 'Something went wrong. Please retry.'; }
  function artwork(url: string): string { return url.startsWith('/') ? url : `/${url}`; }

  async function load() {
    const current = ++requestId;
    loading = true; error = '';
    try {
      if (tab === 'movies') {
        const data = await listMovies(query, page);
        if (current === requestId) movies = data;
      } else if (tab === 'people') {
        const data = await listPeople(query, page, roleFilter || null);
        if (current === requestId) people = data;
      } else {
        const data = query.trim() ? await search(query.trim()) : { movies: [], people: [] };
        if (current === requestId) results = data;
      }
    } catch (e) { if (current === requestId) error = message(e); }
    finally { if (current === requestId) loading = false; }
  }
  onMount(() => { void load(); return () => clearTimeout(timer); });
  function switchTab(next: Tab) {
    tab = next; query = ''; page = 0; error = ''; notice = ''; void load();
  }
  function updateQuery() {
    page = 0;
    clearTimeout(timer);
    timer = setTimeout(() => void load(), 300);
  }

  async function loadPicker(reset = false) {
    if (reset) { pickerPage = 0; pickerPeople = []; pickerHasMore = false; }
    const current = ++pickerRequestId;
    pickerBusy = true;
    try {
      const data = await listPeople(pickerQuery, pickerPage);
      if (current !== pickerRequestId) return;
      pickerPeople = reset ? data.items : [...pickerPeople, ...data.items];
      for (const person of data.items) knownPeople[person.id] = person.name;
      pickerHasMore = pickerPage + 1 < data.totalPages;
    } catch (e) { if (current === pickerRequestId) error = message(e); }
    finally { if (current === pickerRequestId) pickerBusy = false; }
  }
  function openMovie(movie: Movie | null = null) {
    editingMovie = movie;
    movieForm = movie ? {
      title: movie.title, synopsis: movie.synopsis, releaseYear: movie.releaseYear,
      runtimeMinutes: movie.runtimeMinutes, genres: [...movie.genres],
      credits: movie.credits.map(({ personId, role, characterName }) => ({ personId, role, characterName })),
    } : blankMovie();
    genreText = movie?.genres.join(', ') ?? '';
    pickerQuery = ''; pickerPage = 0; pickerPeople = []; file = null; uploadPercent = null;
    knownPeople = {};
    for (const credit of movie?.credits ?? []) knownPeople[credit.personId] = credit.personName;
    error = ''; modal = 'movie'; void loadPicker(true);
  }
  function openPerson(person: Person | null = null) {
    editingPerson = person;
    personForm = person ? { name: person.name, bio: person.bio, birthDate: person.birthDate ?? '' }
      : { name: '', bio: '', birthDate: '' };
    error = ''; modal = 'person';
  }
  function addCredit() {
    movieForm.credits = [...movieForm.credits, { personId: '', role: 'ACTOR', characterName: '' }];
  }
  function removeCredit(index: number) {
    movieForm.credits = movieForm.credits.filter((_, i) => i !== index);
  }
  async function submitMovie() {
    error = ''; notice = '';
    if (!movieForm.title.trim()) { error = 'Enter a movie title.'; return; }
    if (movieForm.credits.some(c => !c.personId)) { error = 'Choose a person for every credit.'; return; }
    if (file && (!['image/jpeg', 'image/png', 'image/webp'].includes(file.type) || file.size > 5 * 1024 * 1024)) {
      error = 'Artwork must be a JPEG, PNG or WebP image smaller than 5 MB.'; return;
    }
    if (new Set(movieForm.credits.map(c => `${c.personId}:${c.role}`)).size !== movieForm.credits.length) {
      error = 'Each person can only have one credit per role.'; return;
    }
    busy = true;
    try {
      const saved = await saveMovie({ ...movieForm,
        title: movieForm.title.trim(), genres: genreText.split(',').map(g => g.trim()).filter(Boolean),
        credits: movieForm.credits.map(c => ({ ...c, characterName: c.role === 'ACTOR' ? c.characterName : '' })),
      }, editingMovie?.id);
      editingMovie = saved; // Retain the ID if the subsequent upload fails, so retry cannot create a duplicate.
      if (file) {
        uploadPercent = 0;
        await uploadArtwork(saved.id, file, n => uploadPercent = n);
        file = null;
      }
      modal = null; notice = 'Movie saved successfully.'; void load();
    } catch (e) { error = message(e); }
    finally { busy = false; uploadPercent = null; }
  }
  async function submitPerson() {
    error = ''; notice = '';
    if (!personForm.name.trim()) { error = 'Enter a name.'; return; }
    busy = true;
    try {
      await savePerson({ ...personForm, name: personForm.name.trim() }, editingPerson?.id);
      modal = null; notice = 'Person saved successfully.'; void load();
    } catch (e) { error = message(e); }
    finally { busy = false; }
  }
  async function remove(kind: 'movie' | 'person', id: string, name: string) {
    if (!confirm(`Delete ${name}? This cannot be undone${kind === 'person' ? ' and will remove their movie credits' : ''}.`)) return;
    busy = true; error = ''; notice = '';
    try {
      const deleted = kind === 'movie' ? await deleteMovie(id) : await deletePerson(id);
      if (!deleted) throw new Error(`${name} no longer exists. Refresh the list.`);
      notice = `${name} deleted.`;
      if (page > 0 && (kind === 'movie' ? movies?.items.length : people?.items.length) === 1) page--;
      await load();
    } catch (e) { error = message(e); }
    finally { busy = false; }
  }
  function chosenPerson(id: string, credit: CreditInput): string {
    return knownPeople[id] || editingMovie?.credits.find(c => c.personId === id)?.personName || credit.personId;
  }
</script>

<svelte:head>
  <title>MovieHub — Your film collection</title>
  <meta name="description" content="Browse and manage movies, artwork, cast and creators." />
</svelte:head>

<header class="topbar">
  <div class="wrap header-inner">
    <div class="brand"><span class="brand-mark"><Icon name="film" size={20} /></span> MovieHub</div>
    <nav class="tabs" aria-label="Catalog sections">
      <button class:active={tab === 'movies'} aria-current={tab === 'movies' ? 'page' : undefined} onclick={() => switchTab('movies')}><Icon name="film" size={18} /><span>Movies</span></button>
      <button class:active={tab === 'people'} aria-current={tab === 'people' ? 'page' : undefined} onclick={() => switchTab('people')}><Icon name="users" size={18} /><span>Cast & creators</span></button>
      <button class:active={tab === 'search'} aria-current={tab === 'search' ? 'page' : undefined} onclick={() => switchTab('search')}><Icon name="compass" size={18} /><span>Explore all</span></button>
    </nav>
  </div>
</header>
<main class="wrap">
  <section class="hero">
    <div>
      <p class="eyebrow">Your film collection</p>
      <h1>Stories worth <em>keeping</em>.</h1>
      <p class="subhead">Explore films and the people who bring them to life.</p>
    </div>
    <div class="stats" aria-label="Collection summary">
      <div class="stat"><strong>{movies?.total ?? '–'}</strong><span>Movies</span></div>
      <div class="stat"><strong>{people?.total ?? '–'}</strong><span>People</span></div>
    </div>
  </section>
  <div class="toolbar">
    <div class="searchbox"><Icon name="search" size={19} /><input aria-label="Search" placeholder={tab === 'movies' ? 'Search movies, genres, cast…' : tab === 'people' ? 'Search cast & creators…' : 'Search everything…'} bind:value={query} oninput={updateQuery} /></div>
    {#if tab === 'people'}
      <select aria-label="Filter by role" bind:value={roleFilter} onchange={() => { page = 0; void load(); }}>
        <option value="">All roles</option>{#each roles as role}<option value={role}>{roleLabel(role)}</option>{/each}
      </select>
    {/if}
    {#if tab !== 'search'}<button class="primary add-btn" disabled={busy} onclick={() => tab === 'movies' ? openMovie() : openPerson()}><Icon name="plus" size={18} /><span>Add {tab === 'movies' ? 'movie' : 'person'}</span></button>{/if}
  </div>
  {#if notice}<p class="alert success" role="status">{notice}</p>{/if}
  {#if error && !modal}<div class="alert failure" role="alert"><span>{error}</span> <button onclick={() => void load()}>Retry</button></div>{/if}
  {#if loading}
    <p class="sr-only" role="status">Loading {tab === 'search' ? 'results' : tab}…</p>
    <div class="grid skeletons" aria-hidden="true">{#each [1, 2, 3, 4] as n (n)}<div class="skeleton"></div>{/each}</div>
  {/if}

  {#if tab === 'movies'}
    <div class="section-heading"><h2>Movies</h2>{#if movies}<span>{movies.total} titles</span>{/if}</div>
    {#if movies && (!loading || movies.items.length)}
      {#if !movies.items.length}<p class="empty">No movies found. Try another search or add your first movie.</p>{/if}
      <div class="grid">{#each movies.items as movie (movie.id)}
        <MovieCard {movie} {busy} onedit={() => openMovie(movie)} ondelete={() => void remove('movie', movie.id, movie.title)} />
      {/each}</div>
    {/if}
  {:else if tab === 'people'}
    <div class="section-heading"><h2>Cast & creators</h2>{#if people}<span>{people.total} people</span>{/if}</div>
    {#if people && (!loading || people.items.length)}
      {#if !people.items.length}<p class="empty">No people found. Try another search or add someone.</p>{/if}
      <div class="grid people-grid">{#each people.items as person (person.id)}
        <PersonCard {person} {busy} onedit={() => openPerson(person)} ondelete={() => void remove('person', person.id, person.name)} />
      {/each}</div>
    {/if}
  {:else}
    <div class="section-heading"><h2>Explore all</h2></div>
    {#if !query.trim()}<p class="empty">Search movies, cast, and creators in one place.</p>
    {:else if results}
      {#if !results.movies.length && !results.people.length && !loading}<p class="empty">No matches found.</p>{/if}
      <div class="search-results"><h3>Movies <span>{results.movies.length}</span></h3>
        {#each results.movies as movie (movie.id)}<div class="result-row"><div class="result-thumb" style="--hue: {hue(movie.title)}">{#if movie.artworkUrl}<img src={artworkSrc(movie.artworkUrl)} alt="" />{:else}<Icon name="film" size={20} />{/if}</div><div class="result-text"><strong>{movie.title}</strong><p>{movie.releaseYear} · {movie.genres.join(', ') || 'Film'}</p></div><button onclick={() => { const title = movie.title; switchTab('movies'); query = title; void load(); }}>View</button></div>{/each}
        <h3>People <span>{results.people.length}</span></h3>
        {#each results.people as person (person.id)}<div class="result-row"><div class="result-thumb round" style="--hue: {hue(person.name)}">{initials(person.name)}</div><div class="result-text"><strong>{person.name}</strong><p>{person.filmography.length} film credits</p></div><button onclick={() => { const name = person.name; switchTab('people'); query = name; void load(); }}>View</button></div>{/each}
      </div>
    {/if}
  {/if}
  {#if tab !== 'search'}
    {@const listing = tab === 'movies' ? movies : people}
    {#if listing && listing.totalPages > 1}
      <div class="pagination"><button disabled={loading || page === 0} onclick={() => { page--; void load(); }}><Icon name="prev" size={16} /> Previous</button><span>Page {page + 1} of {listing.totalPages}</span><button disabled={loading || page + 1 >= listing.totalPages} onclick={() => { page++; void load(); }}>Next <Icon name="next" size={16} /></button></div>
    {/if}
  {/if}
</main>

{#if modal}
  <div class="backdrop"><dialog open class="dialog" aria-modal="true" aria-label={modal === 'movie' ? 'Movie editor' : 'Person editor'}>
    <div class="dialog-head"><h2>{modal === 'movie' ? editingMovie ? 'Edit movie' : 'Add a movie' : editingPerson ? 'Edit person' : 'Add a person'}</h2><button class="close" aria-label="Close" disabled={busy} onclick={() => { modal = null; error = ''; }}><Icon name="close" size={20} /></button></div>
    {#if error}<p class="alert failure" role="alert">{error}</p>{/if}
    {#if modal === 'movie'}
      <form onsubmit={(event) => { event.preventDefault(); void submitMovie(); }}>
        <label>Title <input required maxlength="200" bind:value={movieForm.title} /></label>
        <div class="two-col"><label>Release year <input required type="number" min="1888" max={new Date().getFullYear() + 10} bind:value={movieForm.releaseYear} /></label><label>Runtime (minutes) <input type="number" min="0" max="1000" bind:value={movieForm.runtimeMinutes} /></label></div>
        <label>Synopsis <textarea maxlength="4000" rows="3" bind:value={movieForm.synopsis}></textarea></label>
        <label>Genres <input placeholder="Drama, Adventure" bind:value={genreText} /><small>Separate genres with commas</small></label>
        <div class="form-section"><div class="form-section-title"><h3>Cast & creators</h3><button type="button" onclick={addCredit}>+ Add credit</button></div>
          {#if movieForm.credits.length}
            <label>Find a person <input placeholder="Filter available people…" bind:value={pickerQuery} oninput={() => void loadPicker(true)} /></label>
            {#each movieForm.credits as credit, index}
              <div class="credit-editor"><select aria-label="Person" bind:value={credit.personId} required>
                <option value="">Select person</option>
                {#if credit.personId && !pickerPeople.some(p => p.id === credit.personId)}<option value={credit.personId}>{chosenPerson(credit.personId, credit)}</option>{/if}
                {#each pickerPeople as person (person.id)}<option value={person.id}>{person.name}</option>{/each}
              </select><select aria-label="Role" bind:value={credit.role}>{#each roles as role}<option value={role}>{role}</option>{/each}</select>
              {#if credit.role === 'ACTOR'}<input aria-label="Character name" placeholder="Character (optional)" maxlength="200" bind:value={credit.characterName} />{/if}
              <button type="button" class="danger" aria-label="Remove credit" onclick={() => removeCredit(index)}>×</button></div>
            {/each}
            {#if pickerHasMore}<button type="button" disabled={pickerBusy} onclick={() => { pickerPage++; void loadPicker(); }}>Load more people</button>{/if}
            {#if !pickerPeople.length}<p class="hint">No people found. Add them in Cast & creators first.</p>{/if}
          {/if}
        </div>
        <div class="form-section"><h3>Artwork</h3>{#if editingMovie?.artworkUrl}<img class="art-preview" src={artwork(editingMovie.artworkUrl)} alt="Current artwork" />{/if}
          <label>Upload image <input type="file" accept="image/jpeg,image/png,image/webp" onchange={(event) => file = event.currentTarget.files?.[0] ?? null} /><small>JPEG, PNG or WebP · maximum 5 MB</small></label>
          {#if uploadPercent !== null}<div role="status">Uploading artwork: {uploadPercent}% <progress value={uploadPercent} max="100"></progress></div>{/if}
        </div>
        <div class="dialog-actions"><button type="button" disabled={busy} onclick={() => { modal = null; error = ''; }}>Cancel</button><button class="primary" type="submit" disabled={busy}>{busy ? 'Saving…' : 'Save movie'}</button></div>
      </form>
    {:else}
      <form onsubmit={(event) => { event.preventDefault(); void submitPerson(); }}>
        <label>Name <input required maxlength="200" bind:value={personForm.name} /></label>
        <label>Birth date <input type="date" max={new Date().toISOString().slice(0, 10)} bind:value={personForm.birthDate} /></label>
        <label>Biography <textarea maxlength="4000" rows="5" bind:value={personForm.bio}></textarea></label>
        <div class="dialog-actions"><button type="button" disabled={busy} onclick={() => { modal = null; error = ''; }}>Cancel</button><button class="primary" type="submit" disabled={busy}>{busy ? 'Saving…' : 'Save person'}</button></div>
      </form>
    {/if}
  </dialog></div>
{/if}

















