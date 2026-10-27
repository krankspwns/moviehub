<script lang="ts">
  import { untrack } from 'svelte';
  import { imageProblem, savePerson, uploadAvatar, type Person, type PersonInput } from '../api/catalog';
  import { artworkSrc, errorMessage, hue, initials } from '../format';
  import { LIMITS, personFieldErrors, validatePerson } from '../validate';
  import FieldHint from './FieldHint.svelte';
  import Icon from './Icon.svelte';

  let { person, onsaved, onclose }: { person: Person | null; onsaved: () => void; onclose: () => void } = $props();

  const initial = untrack(() => person);
  let editing = $state<Person | null>(initial);
  let form = $state<PersonInput>(
    initial
      ? { name: initial.name, bio: initial.bio, birthDate: initial.birthDate ?? '' }
      : { name: '', bio: '', birthDate: '' },
  );
  const errors = $derived(personFieldErrors(form));
  let avatarFile = $state<File | null>(null);
  const imageError = $derived(imageProblem(avatarFile));
  let avatarPreview = $state('');
  let uploadPercent = $state<number | null>(null);
  let busy = $state(false);
  let error = $state('');

  // Release the object URL of the previous preview (and the last one when the dialog closes).
  $effect(() => {
    const url = avatarPreview;
    return () => {
      if (url) URL.revokeObjectURL(url);
    };
  });

  function pickAvatar(next: File | null) {
    avatarFile = next;
    avatarPreview = next ? URL.createObjectURL(next) : '';
  }
  function close() {
    if (!busy) onclose();
  }

  async function submit() {
    error = '';
    const problem = validatePerson(form) ?? imageProblem(avatarFile);
    if (problem) {
      error = problem;
      return;
    }
    busy = true;
    try {
      const saved = await savePerson({ ...form, name: form.name.trim() }, editing?.id);
      editing = saved; // Keep the ID if the upload fails, so a retry updates instead of duplicating.
      if (avatarFile) {
        uploadPercent = 0;
        await uploadAvatar(saved.id, avatarFile, (n) => (uploadPercent = n));
        pickAvatar(null);
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
  <dialog open class="dialog" aria-modal="true" aria-label="Person editor">
    <div class="dialog-head">
      <h2>{editing ? 'Edit person' : 'Add a person'}</h2>
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
      <div class="avatar-field">
        <div class="avatar large" style="--hue: {hue(form.name || 'new')}">
          {#if avatarPreview}<img src={avatarPreview} alt="Selected preview" />
          {:else if editing?.avatarUrl}<img src={artworkSrc(editing.avatarUrl)} alt="Current avatar" />
          {:else}<span aria-hidden="true">{initials(form.name)}</span>{/if}
        </div>
        <label
          >Photo
          <input
            type="file"
            accept="image/jpeg,image/png,image/webp"
            onchange={(event) => pickAvatar(event.currentTarget.files?.[0] ?? null)}
          />
          <FieldHint error={imageError ?? undefined} hint="JPEG, PNG or WebP · maximum 5 MB" />
        </label>
      </div>
      {#if uploadPercent !== null}<div role="status">
          Uploading photo: {uploadPercent}% <progress value={uploadPercent} max="100"></progress>
        </div>{/if}
      <label
        >Name <input required aria-invalid={!!errors.name} bind:value={form.name} /><FieldHint
          error={errors.name}
          value={form.name}
          max={LIMITS.name}
        /></label
      >
      <label
        >Birth date <input
          type="date"
          max={new Date().toISOString().slice(0, 10)}
          aria-invalid={!!errors.birthDate}
          bind:value={form.birthDate}
        /><FieldHint error={errors.birthDate} /></label
      >
      <label
        >Biography <textarea rows="5" aria-invalid={!!errors.bio} bind:value={form.bio}></textarea><FieldHint
          error={errors.bio}
          value={form.bio}
          max={LIMITS.bio}
        /></label
      >
      <div class="dialog-actions">
        <button type="button" disabled={busy} onclick={close}>Cancel</button>
        <button class="primary" type="submit" disabled={busy}>{busy ? 'Saving…' : 'Save person'}</button>
      </div>
    </form>
  </dialog>
</div>
