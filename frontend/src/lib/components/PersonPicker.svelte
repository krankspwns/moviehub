<script lang="ts">
  import { listPeople, type Person } from '../api/catalog';
  import { errorMessage } from '../format';

  let {
    personId,
    name = '',
    label = 'Person',
    onpick,
  }: {
    personId: string;
    name?: string;
    label?: string;
    onpick: (person: { id: string; name: string }) => void;
  } = $props();

  const listId = `person-list-${Math.random().toString(36).slice(2, 9)}`;

  let text = $state('');
  let editing = $state(false); // true while the user is typing a search instead of showing the chosen name
  let open = $state(false);
  let results = $state<Person[]>([]);
  let hasMore = $state(false);
  let loading = $state(false);
  let failure = $state('');
  let active = $state(-1);
  let requestId = 0; // guards against out-of-order responses
  let timer: ReturnType<typeof setTimeout>;

  const shown = $derived(editing ? text : name);
  const query = $derived(editing ? text.trim() : '');

  async function search(term: string) {
    const current = ++requestId;
    loading = true;
    failure = '';
    try {
      const page = await listPeople(term, 0);
      if (current !== requestId) return;
      results = page.items;
      hasMore = page.page + 1 < page.totalPages;
      active = results.length ? 0 : -1;
    } catch (e) {
      if (current === requestId) failure = errorMessage(e);
    } finally {
      if (current === requestId) loading = false;
    }
  }

  function openList() {
    open = true;
    void search(query);
  }
  function onInput(event: Event & { currentTarget: HTMLInputElement }) {
    text = event.currentTarget.value;
    editing = true;
    open = true;
    clearTimeout(timer);
    timer = setTimeout(() => void search(text.trim()), 250);
  }
  function pick(person: Person) {
    onpick({ id: person.id, name: person.name });
    close();
  }
  function close() {
    open = false;
    editing = false;
    text = '';
    clearTimeout(timer);
  }
  function onKeydown(event: KeyboardEvent) {
    if (event.key === 'ArrowDown' || event.key === 'ArrowUp') {
      event.preventDefault();
      if (!open) return openList();
      if (!results.length) return;
      const step = event.key === 'ArrowDown' ? 1 : -1;
      active = (active + step + results.length) % results.length;
    } else if (event.key === 'Enter' && open && active >= 0 && results[active]) {
      event.preventDefault();
      pick(results[active]);
    } else if (event.key === 'Escape' && open) {
      // Close only the list, not the whole dialog.
      event.stopPropagation();
      close();
    }
  }
</script>

<div class="picker">
  <input
    type="text"
    role="combobox"
    class="picker-input"
    aria-label={label}
    aria-expanded={open}
    aria-controls={listId}
    aria-autocomplete="list"
    aria-activedescendant={open && active >= 0 ? `${listId}-${active}` : undefined}
    autocomplete="off"
    placeholder="Search for a person…"
    value={shown}
    onfocus={openList}
    oninput={onInput}
    onkeydown={onKeydown}
    onblur={close}
  />
  {#if open}
    <ul class="picker-list" id={listId} role="listbox" aria-label="{label} results">
      {#if loading && !results.length}
        <li class="picker-note" role="presentation">Searching…</li>
      {:else if failure}
        <li class="picker-note error" role="presentation">{failure}</li>
      {:else if !results.length}
        <li class="picker-note" role="presentation">
          {query
            ? `Nobody matches “${query}”. Add them in Cast & creators first.`
            : 'No people yet. Add them in Cast & creators first.'}
        </li>
      {:else}
        {#each results as person, index (person.id)}
          <!-- mousedown (not click) so the choice lands before the input loses focus -->
          <li
            id="{listId}-{index}"
            role="option"
            aria-selected={person.id === personId}
            class:active={index === active}
            onmousedown={(event) => {
              event.preventDefault();
              pick(person);
            }}
          >
            {person.name}
          </li>
        {/each}
        {#if hasMore}<li class="picker-note" role="presentation">Keep typing to narrow the list…</li>{/if}
      {/if}
    </ul>
  {/if}
</div>
