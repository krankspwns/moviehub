<!-- Brand mark; mirrors public/favicon.svg. Gradient id is unique per component to avoid collisions. -->
<script lang="ts">
  let { size = 34 }: { size?: number } = $props();
</script>

<svg class="logo" width={size} height={size} viewBox="0 0 64 64" aria-hidden="true" focusable="false">
  <defs>
    <linearGradient id="logo-gradient" x1="6" y1="4" x2="58" y2="60" gradientUnits="userSpaceOnUse">
      <stop offset="0" stop-color="#8b5cf6" />
      <stop offset="1" stop-color="#ec4899" />
    </linearGradient>
  </defs>
  <rect width="64" height="64" rx="15" fill="url(#logo-gradient)" />
  <g fill="#fff" fill-opacity=".38">
    <rect x="6" y="9" width="6" height="7" rx="1.5" /><rect x="6" y="21" width="6" height="7" rx="1.5" />
    <rect x="6" y="36" width="6" height="7" rx="1.5" /><rect x="6" y="48" width="6" height="7" rx="1.5" />
    <rect x="52" y="9" width="6" height="7" rx="1.5" /><rect x="52" y="21" width="6" height="7" rx="1.5" />
    <rect x="52" y="36" width="6" height="7" rx="1.5" /><rect x="52" y="48" width="6" height="7" rx="1.5" />
  </g>
  <path d="M25 19.5v25a1.8 1.8 0 0 0 2.75 1.53l20-12.5a1.8 1.8 0 0 0 0-3.06l-20-12.5A1.8 1.8 0 0 0 25 19.5Z" fill="#fff" />
</svg>

