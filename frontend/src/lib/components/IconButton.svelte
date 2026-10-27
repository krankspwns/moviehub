<script lang="ts">
  import Icon, { type IconName } from './Icon.svelte';

  let { icon, label, onclick, disabled = false, variant = 'default' }: {
    icon: IconName;
    label: string;
    onclick: () => void;
    disabled?: boolean;
    variant?: 'default' | 'danger';
  } = $props();
</script>

<button type="button" class="icon-btn {variant}" aria-label={label} title={label} {disabled} {onclick}>
  <Icon name={icon} size={17} />
</button>

