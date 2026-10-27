<script lang="ts">
  import CharCount from './CharCount.svelte';

  let { error, hint, value, max }: { error?: string; hint?: string; value?: string; max?: number } = $props();
</script>

<span class="field-meta">
  {#if error}<small class="field-error" aria-live="polite">{error}</small>
  {:else if hint}<small>{hint}</small>
  {:else}<span></span>{/if}
  {#if value !== undefined && max !== undefined}<CharCount {value} {max} />{/if}
</span>
