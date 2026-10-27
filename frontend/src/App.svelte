<script lang="ts">
  import { onMount } from 'svelte';
  import {
    deleteMovie,
    deletePerson,
    getMovies,
    getPeople,
    listMovies,
    listPeople,
    roles,
    search,
    type Movie,
    type Page,
    type Person,
    type Role,
  } from './lib/api/catalog';
  import { errorMessage, roleLabel } from './lib/format';
  import Header, { type Tab } from './lib/components/Header.svelte';
  import Icon from './lib/components/Icon.svelte';
  import MovieCard from './lib/components/MovieCard.svelte';
  import MovieDetails from './lib/components/MovieDetails.svelte';
  import MovieEditor from './lib/components/MovieEditor.svelte';
  import PersonCard from './lib/components/PersonCard.svelte';
  import PersonDetails from './lib/components/PersonDetails.svelte';
  import PersonEditor from './lib/components/PersonEditor.svelte';
  import SearchResults from './lib/components/SearchResults.svelte';
  import { readSaved, readSavedPeople, toggleSaved, writeSaved, writeSavedPeople } from './lib/state/saved';
  import { applyTheme, nextTheme, readTheme, writeTheme, type Theme } from './lib/state/theme';

  // Listing state
  let tab = $state<Tab>('movies');
  let query = $state('');
  let roleFilter = $state<Role | ''>('');
  let page = $state(0);
  let movies = $state<Page<Movie> | null>(null);
  let people = $state<Page<Person> | null>(null);
  let results = $state<{ movies: Movie[]; people: Person[] } | null>(null);
  let savedMovies = $state<Movie[] | null>(null);
  let savedPeople = $state<Person[] | null>(null);
  let loading = $state(false);
  let busy = $state(false);
  let error = $state('');
  let notice = $state('');
  let requestId = 0; // guards against out-of-order responses
  let timer: ReturnType<typeof setTimeout>;

  // Preferences
  let savedIds = $state<string[]>(readSaved());
  let savedPeopleIds = $state<string[]>(readSavedPeople());
  const savedTotal = $derived(savedIds.length + savedPeopleIds.length);
  const initialTheme: Theme = readTheme();
  applyTheme(initialTheme);
  let theme = $state<Theme>(initialTheme);

  // Dialogs
  let editor = $state<'movie' | 'person' | null>(null);
  let editorMovie = $state<Movie | null>(null);
  let editorPerson = $state<Person | null>(null);
  let viewMovie = $state<Movie | null>(null);
  let viewPerson = $state<Person | null>(null);

  async function load() {
    const current = ++requestId;
    loading = true;
    error = '';
    try {
      if (tab === 'movies') {
        const data = await listMovies(query, page);
        if (current === requestId) movies = data;
      } else if (tab === 'people') {
        const data = await listPeople(query, page, roleFilter || null);
        if (current === requestId) people = data;
      } else if (tab === 'saved') {
        const [movieData, peopleData] = await Promise.all([getMovies(savedIds), getPeople(savedPeopleIds)]);
        if (current === requestId) {
          savedMovies = movieData;
          savedPeople = peopleData;
          // drop anything that was deleted in the meantime
          if (movieData.length !== savedIds.length) {
            savedIds = movieData.map((m) => m.id);
            writeSaved(savedIds);
          }
          if (peopleData.length !== savedPeopleIds.length) {
            savedPeopleIds = peopleData.map((p) => p.id);
            writeSavedPeople(savedPeopleIds);
          }
        }
      } else {
        const data = query.trim() ? await search(query.trim()) : { movies: [], people: [] };
        if (current === requestId) results = data;
      }
    } catch (e) {
      if (current === requestId) error = errorMessage(e);
    } finally {
      if (current === requestId) loading = false;
    }
  }
  onMount(() => {
    void load();
    // The hero stats show both totals, so fetch the list we are not displaying too.
    if (tab !== 'people')
      void listPeople('', 0, null).then(
        (data) => (people ??= data),
        () => {},
      );
    return () => clearTimeout(timer);
  });

  function switchTab(next: Tab) {
    tab = next;
    query = '';
    page = 0;
    error = '';
    notice = '';
    void load();
  }
  function updateQuery() {
    page = 0;
    clearTimeout(timer);
    timer = setTimeout(() => void load(), 300);
  }
  function jumpTo(next: 'movies' | 'people', term: string) {
    switchTab(next);
    query = term;
    void load();
  }

  function toggleTheme() {
    theme = nextTheme(theme);
    applyTheme(theme);
    writeTheme(theme);
  }
  function goHome() {
    switchTab('movies');
    window.scrollTo({ top: 0 });
  }
  function toggleSavePerson(person: Person) {
    savedPeopleIds = toggleSaved(savedPeopleIds, person.id);
    writeSavedPeople(savedPeopleIds);
    if (tab === 'saved' && !savedPeopleIds.includes(person.id))
      savedPeople = (savedPeople ?? []).filter((p) => p.id !== person.id);
  }
  function toggleSave(movie: Movie) {
    savedIds = toggleSaved(savedIds, movie.id);
    writeSaved(savedIds);
    if (tab === 'saved' && !savedIds.includes(movie.id))
      savedMovies = (savedMovies ?? []).filter((m) => m.id !== movie.id);
  }

  function closeDetail() {
    viewMovie = null;
    viewPerson = null;
  }
  function onKey(event: KeyboardEvent) {
    if (event.key === 'Escape' && (viewMovie || viewPerson)) closeDetail();
  }
  function openMovie(movie: Movie | null = null) {
    editorMovie = movie;
    editor = 'movie';
  }
  function openPerson(person: Person | null = null) {
    editorPerson = person;
    editor = 'person';
  }
  function saved(what: string) {
    editor = null;
    notice = `${what} saved successfully.`;
    void load();
  }

  async function remove(kind: 'movie' | 'person', id: string, name: string) {
    const extra = kind === 'person' ? ' and will remove their movie credits' : '';
    if (!confirm(`Delete ${name}? This cannot be undone${extra}.`)) return;
    busy = true;
    error = '';
    notice = '';
    try {
      const deleted = kind === 'movie' ? await deleteMovie(id) : await deletePerson(id);
      if (!deleted) throw new Error(`${name} no longer exists. Refresh the list.`);
      notice = `${name} deleted.`;
      if (kind === 'movie' && savedIds.includes(id)) {
        savedIds = savedIds.filter((s) => s !== id);
        writeSaved(savedIds);
      }
      if (kind === 'person' && savedPeopleIds.includes(id)) {
        savedPeopleIds = savedPeopleIds.filter((s) => s !== id);
        writeSavedPeople(savedPeopleIds);
      }
      if (page > 0 && (kind === 'movie' ? movies?.items.length : people?.items.length) === 1) page--;
      await load();
    } catch (e) {
      error = errorMessage(e);
    } finally {
      busy = false;
    }
  }
</script>

<svelte:window onkeydown={onKey} />

<svelte:head>
  <title>MovieHub — Your film collection</title>
  <meta name="description" content="Browse and manage movies, artwork, cast and creators." />
</svelte:head>

<Header {tab} savedCount={savedTotal} {theme} onswitch={switchTab} ontheme={toggleTheme} onhome={goHome} />
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
      <div class="stat"><strong>{savedTotal}</strong><span>Saved</span></div>
    </div>
  </section>

  {#if tab !== 'saved'}
    <div class="toolbar">
      <div class="searchbox">
        <Icon name="search" size={19} />
        <input
          aria-label="Search"
          bind:value={query}
          oninput={updateQuery}
          placeholder={tab === 'movies'
            ? 'Search movies, genres, cast…'
            : tab === 'people'
              ? 'Search cast & creators…'
              : 'Search everything…'}
        />
      </div>
      {#if tab === 'people'}
        <select
          aria-label="Filter by role"
          bind:value={roleFilter}
          onchange={() => {
            page = 0;
            void load();
          }}
        >
          <option value="">All roles</option>
          {#each roles as role (role)}<option value={role}>{roleLabel(role)}</option>{/each}
        </select>
      {/if}
      {#if tab === 'movies' || tab === 'people'}
        <button class="primary add-btn" disabled={busy} onclick={() => (tab === 'movies' ? openMovie() : openPerson())}>
          <Icon name="plus" size={18} /><span>Add {tab === 'movies' ? 'Movie' : 'Person'}</span>
        </button>
      {/if}
    </div>
  {/if}

  {#if notice}<p class="alert success" role="status">{notice}</p>{/if}
  {#if error}<div class="alert failure" role="alert">
      <span>{error}</span> <button onclick={() => void load()}>Retry</button>
    </div>{/if}
  {#if loading}
    <p class="sr-only" role="status">Loading {tab === 'search' ? 'results' : tab}…</p>
    <div class="grid skeletons" aria-hidden="true">
      {#each [1, 2, 3, 4] as n (n)}<div class="skeleton"></div>{/each}
    </div>
  {/if}

  {#if tab === 'movies'}
    <div class="section-heading">
      <h2>Movies</h2>
      {#if movies}<span>{movies.total} titles</span>{/if}
    </div>
    {#if movies && (!loading || movies.items.length)}
      {#if !movies.items.length}<p class="empty">No movies found. Try another search or add your first movie.</p>{/if}
      <div class="grid">
        {#each movies.items as movie (movie.id)}
          <MovieCard
            {movie}
            {busy}
            saved={savedIds.includes(movie.id)}
            ontoggle={() => toggleSave(movie)}
            onopen={() => (viewMovie = movie)}
            onedit={() => openMovie(movie)}
            ondelete={() => void remove('movie', movie.id, movie.title)}
          />
        {/each}
      </div>
    {/if}
  {:else if tab === 'saved'}
    <div class="section-heading">
      <h2>Saved</h2>
      <span>{savedTotal} saved</span>
    </div>
    {#if !savedTotal}
      <p class="empty">Nothing saved yet. Tap the bookmark on any movie or person to keep it here for later.</p>
    {:else}
      {#if savedMovies?.length}
        <h3 class="saved-heading">Movies <span class="hint">({savedMovies.length})</span></h3>
        <div class="grid">
          {#each savedMovies as movie (movie.id)}
            <MovieCard
              {movie}
              {busy}
              saved={true}
              ontoggle={() => toggleSave(movie)}
              onopen={() => (viewMovie = movie)}
              onedit={() => openMovie(movie)}
              ondelete={() => void remove('movie', movie.id, movie.title)}
            />
          {/each}
        </div>
      {/if}
      {#if savedPeople?.length}
        <h3 class="saved-heading">Cast & creators <span class="hint">({savedPeople.length})</span></h3>
        <div class="grid people-grid">
          {#each savedPeople as person (person.id)}
            <PersonCard
              {person}
              {busy}
              saved={true}
              ontoggle={() => toggleSavePerson(person)}
              onopen={() => (viewPerson = person)}
              onedit={() => openPerson(person)}
              ondelete={() => void remove('person', person.id, person.name)}
            />
          {/each}
        </div>
      {/if}
    {/if}
  {:else if tab === 'people'}
    <div class="section-heading">
      <h2>Cast & creators</h2>
      {#if people}<span>{people.total} people</span>{/if}
    </div>
    {#if people && (!loading || people.items.length)}
      {#if !people.items.length}<p class="empty">No people found. Try another search or add someone.</p>{/if}
      <div class="grid people-grid">
        {#each people.items as person (person.id)}
          <PersonCard
            {person}
            {busy}
            saved={savedPeopleIds.includes(person.id)}
            ontoggle={() => toggleSavePerson(person)}
            onopen={() => (viewPerson = person)}
            onedit={() => openPerson(person)}
            ondelete={() => void remove('person', person.id, person.name)}
          />
        {/each}
      </div>
    {/if}
  {:else}
    <div class="section-heading"><h2>Explore all</h2></div>
    {#if !query.trim()}
      <p class="empty">Search movies, cast, and creators in one place.</p>
    {:else if results}
      <SearchResults
        {results}
        {loading}
        onviewmovie={(title) => jumpTo('movies', title)}
        onviewperson={(name) => jumpTo('people', name)}
      />
    {/if}
  {/if}

  {#if tab === 'movies' || tab === 'people'}
    {@const listing = tab === 'movies' ? movies : people}
    {#if listing && listing.totalPages > 1}
      <div class="pagination">
        <button
          disabled={loading || page === 0}
          onclick={() => {
            page--;
            void load();
          }}><Icon name="prev" size={16} /> Previous</button
        >
        <span>Page {page + 1} of {listing.totalPages}</span>
        <button
          disabled={loading || page + 1 >= listing.totalPages}
          onclick={() => {
            page++;
            void load();
          }}>Next <Icon name="next" size={16} /></button
        >
      </div>
    {/if}
  {/if}
</main>

{#if editor === 'movie'}
  <MovieEditor movie={editorMovie} onsaved={() => saved('Movie')} onclose={() => (editor = null)} />
{:else if editor === 'person'}
  <PersonEditor person={editorPerson} onsaved={() => saved('Person')} onclose={() => (editor = null)} />
{/if}

{#if viewMovie || viewPerson}
  <div
    class="backdrop"
    role="presentation"
    onclick={(event) => {
      if (event.target === event.currentTarget) closeDetail();
    }}
  >
    <dialog open class="dialog detail" aria-modal="true" aria-label={viewMovie ? 'Movie details' : 'Person details'}>
      {#if viewMovie}
        {@const shown = viewMovie}
        <MovieDetails
          movie={shown}
          saved={savedIds.includes(shown.id)}
          onclose={closeDetail}
          ontoggle={() => toggleSave(shown)}
          onedit={() => {
            closeDetail();
            openMovie(shown);
          }}
        />
      {:else if viewPerson}
        {@const shown = viewPerson}
        <PersonDetails
          person={shown}
          saved={savedPeopleIds.includes(shown.id)}
          ontoggle={() => toggleSavePerson(shown)}
          onclose={closeDetail}
          onedit={() => {
            closeDetail();
            openPerson(shown);
          }}
        />
      {/if}
    </dialog>
  </div>
{/if}
