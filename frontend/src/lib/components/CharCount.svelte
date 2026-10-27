<script lang="ts">
  let { value, max }: { value: string; max: number } = $props();
  const length = $derived(value.trim().length);
  const over = $derived(length > max);
</script>

<small class="counter" class:over aria-live="polite">
  {length}/{max}{#if over}<span class="sr-only"> characters, {length - max} over the limit</span>{/if}
</small>
