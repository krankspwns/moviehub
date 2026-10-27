# frontend/ (Svelte 5, TypeScript, Vite :5173)

| Path                     | Responsibility                                                                                                                                                                                     |
| ------------------------ | -------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `src/lib/api/catalog.ts` | ALL network code: typed GraphQL client (variables only, never string-interpolated input), error mapping, XHR artwork upload with progress                                                          |
| `src/lib/components/`    | `Icon`, `IconButton`, `Logo`, `MovieCard` (save/edit/delete icons), `PersonCard` (avatar, edit/delete), `MovieDetails` / `PersonDetails` (read-only popup opened by clicking a card title/overlay) |
| `src/lib/state/theme.ts` | Light/dark theme (`data-theme` on `<html>`, colours are CSS variables in `style.css`; `index.html` applies the saved theme before first paint)                                                     |
| `src/lib/state/saved.ts` | Saved-movies list persisted in localStorage (`readSaved`, `writeSaved`, `toggleSaved`)                                                                                                             |
| `src/lib/format.ts`      | Pure helpers: `artworkSrc`, `roleLabel`, `initials`, `hue`                                                                                                                                         |
| `src/style.css`          | Mobile-first responsive styles (breakpoints 640 / 1024 / 1440)                                                                                                                                     |
| `src/App.svelte`         | Current UI (tabs, lists, search, dialogs). Large; when adding substantial UI, extract components into `src/lib/components/` and shared state into `src/lib/state/` instead of growing this file    |
| `src/style.css`          | Global responsive styles                                                                                                                                                                           |
| `src/lib/__tests__/`     | Vitest + Testing Library (`catalog.test.ts` API, `App.test.ts` UI). Setup: `src/test-setup.ts`                                                                                                     |
| `vite.config.ts`         | Dev proxy `/graphql` and `/api` → `:8080`; test config (`resolve.conditions: ['browser']` is required for component tests)                                                                         |

Rules

- UI must show loading, empty, success and error states, and upload progress. Guard against stale responses (see `requestId` in `App.svelte`).
- Keep types in `catalog.ts` in sync with `gateway-service/.../schema.graphqls`.
- Match the existing Svelte style (`onclick=` attributes, `bind:`); do not mix in new patterns without migrating.
- `typescript` stays at `~5.9` (svelte-check compatibility).
- Verify: `npm run verify` (Prettier check, ESLint, svelte-check, tests, build). Auto-format with `npm run format`. Runes are mandatory; keep `App.svelte` as orchestration only and put form/dialog state in components.
