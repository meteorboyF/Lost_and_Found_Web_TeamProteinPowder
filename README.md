# Lost & Found

Lost & Found is a privacy-aware university property registry built by TeamProteinPowder. It gives students one place to report missing property, record found items, verify ownership, arrange collection, and follow an item through the campus custody process.

The application is a static prototype: raw HTML, CSS, and JavaScript with no framework, package manager, CDN, or build step. JSON fixtures supply demonstration records and browser storage preserves user actions between reloads.

## Features

### Student registry

- Editorial landing page with live registry totals
- Searchable, sortable, faceted browse grid
- Public item records with privacy masking and custody history
- Three-step lost and found reporting flows
- Duplicate detection against existing found records
- Ownership challenge, student-ID fallback, and pickup scheduling
- Printable pickup reference with QR-style token
- Personal dashboard, saved searches, alerts, and notification settings
- Anonymized registry messaging
- Campus cluster map and loss heatmap
- Good Samaritan recognition board
- Campus-email authentication screen
- Persistent light/dark theme and EN/বাংলা interface labels

### Registry administration

- Fast and bulk intake with image-obscuring controls and bin assignment
- Responsive inventory table with bulk actions and item details
- Side-by-side claim review and confidence scoring
- Weighted match suggestions
- Student-ID OCR routing queue
- Dispute adjudication
- Disposal and donation batches with printable certificates
- Public-content moderation queue
- Animated inline-SVG analytics
- Full-screen rotating kiosk display

## Run locally

No installation or build is required. From the repository root, start any static server:

```sh
python3 -m http.server 8145
```

Open <http://localhost:8145/>. The component reference is at <http://localhost:8145/pages/styleguide.html>, and the admin workspace begins at <http://localhost:8145/pages/admin/intake.html>.

Opening `index.html` directly still renders the interface, theme, and navigation. Browsers block JSON `fetch()` calls over `file://`, so fixture-backed lists show their supported error state until the project is served over HTTP.

## Folder structure

```text
assets/          Self-hosted WOFF2 fonts, SVG sprite, item illustrations
css/             Reset, tokens, base, layout, components, and page styles
data/            Items, users, claims, buildings, notifications, translations
js/              Shared API/store/runtime, components, and page controllers
pages/           Student routes and the component style guide
pages/admin/     Registry operations routes
index.html       Public landing page
```

## Design decisions

The visual system uses warm paper and near-black ink with two restrained signals: ochre for found/positive actions and crimson for lost/urgent states. High-contrast serif display type gives the registry an archival voice, while a self-hosted grotesque handles interface text. Hairline borders, compact radii, and a twelve-column grid keep the system institutional rather than template-like. Both light and dark themes have independently authored surface and text values.

Found-item photos deliberately obscure a distinguishing area. The corresponding identifying mark is kept out of public cards and item pages and is used only as an ownership challenge. A claimant gets three attempts before the flow routes them to staff-reviewed ID verification. Public descriptions should never contain names, student numbers, serial numbers, or the concealed answer.

## Accessibility and resilience

- Semantic page landmarks and heading structure
- Keyboard-operable controls, dialogs, tabs, and navigation
- Visible focus indicators and trapped modal focus
- Live result, notification, and validation feedback
- Text-plus-shape lifecycle status treatments
- Responsive layouts tested at 360, 768, 1024, and 1440 CSS pixels
- Tables collapse to labelled cards below the desktop breakpoint
- Reduced-motion and reduced-data preferences respected
- Dedicated print layouts for pickup slips and donation certificates
- Fixture failures and `file://` restrictions render explicit error states

Current versions of Chrome, Firefox, Safari, and Edge are supported. The layout uses progressive enhancements such as `:has()`, `backdrop-filter`, and `IntersectionObserver`; essential content and actions remain available when those enhancements are absent.

## Team

- Team member — role
- Team member — role
- Team member — role
- Team member — role

## Development notes

Keep CSS files below 400 lines, avoid external runtime dependencies, and reference icons from `assets/icons.svg`. User-facing changes should preserve the verification-privacy boundary and be checked in both themes at mobile and desktop widths.
