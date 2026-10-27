<script lang="ts">
  import Icon from './Icon.svelte';

  let { message, onclose, duration = 4500 }: { message: string; onclose: () => void; duration?: number } = $props();

  // Restart the timer whenever a new message arrives.
  $effect(() => {
    void message;
    const timer = setTimeout(onclose, duration);
    return () => clearTimeout(timer);
  });
</script>

<div class="toast" role="status">
  <span>{message}</span>
  <button type="button" aria-label="Dismiss message" onclick={onclose}><Icon name="close" size={16} /></button>
</div>
