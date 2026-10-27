<script lang="ts" module>
  export type Tab = 'movies' | 'people' | 'saved' | 'search';
</script>

<script lang="ts">
  import type { Theme } from '../state/theme';
  import Icon from './Icon.svelte';
  import Logo from './Logo.svelte';

  let {
    tab,
    savedCount,
    theme,
    onswitch,
    ontheme,
  }: {
    tab: Tab;
    savedCount: number;
    theme: Theme;
    onswitch: (tab: Tab) => void;
    ontheme: () => void;
  } = $props();

  const savedLabel = $derived(savedCount > 99 ? '99+' : String(savedCount));
  const tabs: { id: Tab; label: string; icon: 'film' | 'users' | 'compass' | null }[] = [
    { id: 'movies', label: 'Movies', icon: 'film' },
    { id: 'people', label: 'Cast & creators', icon: 'users' },
    { id: 'saved', label: 'Saved', icon: null },
    { id: 'search', label: 'Explore all', icon: 'compass' },
  ];
</script>

<header class="topbar">
  <div class="wrap header-inner">
    <div class="brand"><Logo size={34} /> MovieHub</div>
    <nav class="tabs" aria-label="Catalog sections">
      {#each tabs as item (item.id)}
        <button
          class:active={tab === item.id}
          aria-current={tab === item.id ? 'page' : undefined}
          onclick={() => onswitch(item.id)}
        >
          {#if item.icon}
            <Icon name={item.icon} size={18} />
          {:else}
            <span class="tab-icon" class:has-items={savedCount > 0}>
              <Icon name="bookmark" size={26} />
              {#if savedCount}<b class="count" data-len={savedLabel.length} aria-label="{savedCount} saved"
                  >{savedLabel}</b
                >{/if}
            </span>
          {/if}
          <span>{item.label}</span>
        </button>
      {/each}
    </nav>
    <button
      type="button"
      class="theme-toggle"
      aria-label={theme === 'dark' ? 'Switch to light theme' : 'Switch to dark theme'}
      title={theme === 'dark' ? 'Light theme' : 'Dark theme'}
      onclick={ontheme}
    >
      <Icon name={theme === 'dark' ? 'sun' : 'moon'} size={19} />
    </button>
  </div>
</header>
