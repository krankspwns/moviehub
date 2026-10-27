<script lang="ts">
  import Icon, { type IconName } from './Icon.svelte';

  let {
    icon,
    label,
    onclick,
    disabled = false,
    variant = 'default',
    pressed,
  }: {
    icon: IconName;
    label: string;
    onclick: () => void;
    disabled?: boolean;
    variant?: 'default' | 'danger';
    /** Set for toggle buttons (e.g. save); omitted for plain actions. */
    pressed?: boolean;
  } = $props();
</script>

<button
  type="button"
  class="icon-btn {variant}"
  class:active={pressed}
  aria-label={label}
  title={label}
  aria-pressed={pressed}
  {disabled}
  {onclick}
>
  <Icon name={icon} size={17} />
</button>
