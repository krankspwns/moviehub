<script lang="ts">
  let {
    title,
    message,
    confirmLabel = 'Delete',
    busy = false,
    onconfirm,
    oncancel,
  }: {
    title: string;
    message: string;
    confirmLabel?: string;
    busy?: boolean;
    onconfirm: () => void;
    oncancel: () => void;
  } = $props();

  let cancelButton: HTMLButtonElement | undefined = $state();
  // Focus the safe option first so Enter never deletes by accident.
  $effect(() => cancelButton?.focus());
</script>

<svelte:window
  onkeydown={(event) => {
    if (event.key === 'Escape' && !busy) oncancel();
  }}
/>

<div
  class="backdrop confirm-backdrop"
  role="presentation"
  onclick={(event) => {
    if (event.target === event.currentTarget && !busy) oncancel();
  }}
>
  <dialog open class="dialog confirm" aria-modal="true" aria-labelledby="confirm-title" aria-describedby="confirm-text">
    <h2 id="confirm-title">{title}</h2>
    <p id="confirm-text">{message}</p>
    <div class="dialog-actions">
      <button type="button" bind:this={cancelButton} disabled={busy} onclick={oncancel}>Cancel</button>
      <button type="button" class="primary danger-btn" disabled={busy} onclick={onconfirm}>
        {busy ? 'Deleting…' : confirmLabel}
      </button>
    </div>
  </dialog>
</div>
