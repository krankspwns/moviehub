<script lang="ts">
  import { onMount, untrack } from 'svelte';
  import {
    imageProblem,
    listPeople,
    roles,
    saveMovie,
    uploadArtwork,
    type CreditInput,
    type Movie,
    type MovieInput,
    type Person,
  } from '../api/catalog';
  import { artworkSrc, errorMessage } from '../format';
  import { LIMITS, movieFieldErrors, parseGenres, validateMovie } from '../validate';
  import FieldHint from './FieldHint.svelte';
  import Icon from './Icon.svelte';

  let { movie, onsaved, onclose }: { movie: Movie | null; onsaved: () => void; onclose: () => void } = $props();

  function blank(): MovieInput {
    return {
      title: '',
      synopsis: '',
      releaseYear: new Date().getFullYear(),
      runtimeMinutes: 0,
      genres: [],
      credits: [],
    };
  }

  const initial = untrack(() => movie);
  let editing = $state<Movie | null>(initial);
  let form = $state<MovieInput>(
    initial
      ? {
          title: initial.title,
          synopsis: initial.synopsis,
          releaseYear: initial.releaseYear,
          runtimeMinutes: initial.runtimeMinutes,
          genres: [...initial.genres],
          credits: initial.credits.map(({ personId, role, characterName }) => ({ personId, role, characterName })),
        }
      : blank(),
  );
  let genreText = $state(initial?.genres.join(', ') ?? '');
  const genreList = $derived(parseGenres(genreText));
  const errors = $derived(movieFieldErrors(form, genreList));
  let file = $state<File | null>(null);
  const imageError = $derived(imageProblem(file));
  let uploadPercent = $state<number | null>(null);
  let busy = $state(false);
  let error = $state('');

  // Person picker for credits (paged, with a stale-response guard).
  let pickerQuery = $state('');
  let pickerPeople = $state<Person[]>([]);
  let pickerPage = $state(0);
  let pickerHasMore = $state(false);
  let pickerBusy = $state(false);
  let pickerRequestId = 0;
  const knownPeople = $state<Record<string, string>>({});
  for (const credit of initial?.credits ?? []) knownPeople[credit.personId] = credit.personName;

  async function loadPicker(reset = false) {
    if (reset) {
      pickerPage = 0;
      pickerPeople = [];
      pickerHasMore = false;
    }
    const current = ++pickerRequestId;
    pickerBusy = true;
    try {
      const data = await listPeople(pickerQuery, pickerPage);
      if (current !== pickerRequestId) return;
      pickerPeople = reset ? data.items : [...pickerPeople, ...data.items];
      for (const person of data.items) knownPeople[person.id] = person.name;
      pickerHasMore = pickerPage + 1 < data.totalPages;
    } catch (e) {
      if (current === pickerRequestId) error = errorMessage(e);
    } finally {
      if (current === pickerRequestId) pickerBusy = false;
    }
  }
  onMount(() => {
    void loadPicker(true);
  });

  function addCredit() {
    form.credits = [...form.credits, { personId: '', role: 'ACTOR', characterName: '' }];
  }
  function removeCredit(index: number) {
    form.credits = form.credits.filter((_, i) => i !== index);
  }
  function chosenPerson(id: string, credit: CreditInput): string {
    return knownPeople[id] || editing?.credits.find((c) => c.personId === id)?.personName || credit.personId;
  }
  function close() {
    if (!busy) onclose();
  }

  async function submit() {
    error = '';
    const genres = genreList;
    const problem = validateMovie(form, genres) ?? imageProblem(file);
    if (problem) {
      error = problem;
      return;
    }
    busy = true;
    try {
      const saved = await saveMovie(
        {
          ...form,
          title: form.title.trim(),
          genres,
          credits: form.credits.map((c) => ({ ...c, characterName: c.role === 'ACTOR' ? c.characterName : '' })),
        },
        editing?.id,
      );
      editing = saved; // Keep the ID if the upload fails, so a retry updates instead of duplicating.
      if (file) {
        uploadPercent = 0;
        await uploadArtwork(saved.id, file, (n) => (uploadPercent = n));
        file = null;
      }
      onsaved();
    } catch (e) {
      error = errorMessage(e);
    } finally {
      busy = false;
      uploadPercent = null;
    }
  }
</script>

<svelte:window
  onkeydown={(event) => {
    if (event.key === 'Escape') close();
  }}
/>

<div class="backdrop">
  <dialog open class="dialog" aria-modal="true" aria-label="Movie editor">
    <div class="dialog-head">
      <h2>{editing ? 'Edit movie' : 'Add a movie'}</h2>
      <button class="close" aria-label="Close" disabled={busy} onclick={close}><Icon name="close" size={20} /></button>
    </div>
    {#if error}<p class="alert failure" role="alert">{error}</p>{/if}
    <form
      novalidate
      onsubmit={(event) => {
        event.preventDefault();
        void submit();
      }}
    >
      <label
        >Title <input required aria-invalid={!!errors.title} bind:value={form.title} /><FieldHint
          error={errors.title}
          value={form.title}
          max={LIMITS.title}
        /></label
      >
      <div class="two-col">
        <label
          >Release year <input
            required
            type="number"
            min="1888"
            max={new Date().getFullYear() + 10}
            aria-invalid={!!errors.releaseYear}
            bind:value={form.releaseYear}
          /><FieldHint error={errors.releaseYear} /></label
        >
        <label
          >Runtime (minutes) <input
            type="number"
            min="0"
            max="1000"
            aria-invalid={!!errors.runtimeMinutes}
            bind:value={form.runtimeMinutes}
          /><FieldHint error={errors.runtimeMinutes} /></label
        >
      </div>
      <label
        >Synopsis <textarea rows="3" aria-invalid={!!errors.synopsis} bind:value={form.synopsis}></textarea><FieldHint
          error={errors.synopsis}
          value={form.synopsis}
          max={LIMITS.synopsis}
        /></label
      >
      <label
        >Genres <input placeholder="Drama, Adventure" aria-invalid={!!errors.genres} bind:value={genreText} /><FieldHint
          error={errors.genres}
          hint="Separate genres with commas ({genreList.length}/{LIMITS.maxGenres})"
        /></label
      >
      <div class="form-section">
        <div class="form-section-title">
          <h3>Cast & creators</h3>
          <button type="button" onclick={addCredit}>+ Add credit</button>
        </div>
        {#if form.credits.length}
          <label
            >Find a person <input
              placeholder="Filter available people…"
              bind:value={pickerQuery}
              oninput={() => void loadPicker(true)}
            /></label
          >
          {#each form.credits as credit, index (credit)}
            <div class="credit-editor">
              <select aria-label="Person" bind:value={credit.personId} required>
                <option value="">Select person</option>
                {#if credit.personId && !pickerPeople.some((p) => p.id === credit.personId)}
                  <option value={credit.personId}>{chosenPerson(credit.personId, credit)}</option>
                {/if}
                {#each pickerPeople as person (person.id)}<option value={person.id}>{person.name}</option>{/each}
              </select>
              <select aria-label="Role" bind:value={credit.role}
                >{#each roles as role (role)}<option value={role}>{role}</option>{/each}</select
              >
              {#if credit.role === 'ACTOR'}
                <input
                  aria-label="Character name"
                  placeholder="Character (optional)"
                  bind:value={credit.characterName}
                />
              {/if}
              <button type="button" class="danger" aria-label="Remove credit" onclick={() => removeCredit(index)}
                >×</button
              >
            </div>
          {/each}
          {#if errors.credits}<small class="field-error" aria-live="polite">{errors.credits}</small>{/if}
          {#if pickerHasMore}<button
              type="button"
              disabled={pickerBusy}
              onclick={() => {
                pickerPage++;
                void loadPicker();
              }}>Load more people</button
            >{/if}
          {#if !pickerPeople.length}<p class="hint">No people found. Add them in Cast & creators first.</p>{/if}
        {/if}
      </div>
      <div class="form-section">
        <h3>Artwork</h3>
        {#if editing?.artworkUrl}<img
            class="art-preview"
            src={artworkSrc(editing.artworkUrl)}
            alt="Current artwork"
          />{/if}
        <label
          >Upload image
          <input
            type="file"
            accept="image/jpeg,image/png,image/webp"
            onchange={(event) => (file = event.currentTarget.files?.[0] ?? null)}
          />
          <FieldHint error={imageError ?? undefined} hint="JPEG, PNG or WebP · maximum 5 MB" />
        </label>
        {#if uploadPercent !== null}<div role="status">
            Uploading artwork: {uploadPercent}% <progress value={uploadPercent} max="100"></progress>
          </div>{/if}
      </div>
      <div class="dialog-actions">
        <button type="button" disabled={busy} onclick={close}>Cancel</button>
        <button class="primary" type="submit" disabled={busy}>{busy ? 'Saving…' : 'Save movie'}</button>
      </div>
    </form>
  </dialog>
</div>
