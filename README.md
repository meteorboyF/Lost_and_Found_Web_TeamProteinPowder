# Lost &amp; Found — Campus Registry

A shared board for posting lost and found items across campus. Post what you
lost, hand in what you found, and the application quietly scans the other side
of the board for likely matches.

Built by **Team Protein Powder**.

New to the project? Start with [Getting started](GETTING_STARTED.md) for the
tech stack, quick setup, project structure and test commands.

---

## Stack

| Layer     | Choice |
|-----------|--------|
| Backend   | Spring Boot 4.1 (Java 21), Spring MVC, Spring Data JPA, Bean Validation |
| Database  | MySQL 8 (H2 in-memory for the test suite only) |
| Frontend  | Static HTML + vanilla JavaScript (ES2017), served by Spring Boot |
| Styling   | Tailwind CSS v4, compiled with the Tailwind CLI |

The frontend and backend share a single origin: Spring Boot serves the pages
out of `src/main/resources/static/`, and the pages call the REST API on the
same host. There is no CORS configuration because there is no cross-origin
request.

---

## Features

**For students**

| | |
|---|---|
| **Post an item** | Signed-in, approved users post lost or found items with a public photo and an optional private evidence photo. |
| **Browse the board** | Keyword search across title, description, location and colour, plus filters for side of the board, category and status. Sort and paging. Filter state lives in the URL, so a filtered board is a shareable link. |
| **Claim an item** | Answer a security question and submit private identifying details or an evidence image. Participants and authorized campus staff can review it. |
| **Automatic questions** | Category-based prompts are generated locally. Posters can edit the question and set a secret answer, stored as a salted hash. |
| **Private images** | Poster images are protected until handover completes. Claimant evidence images are limited to the two participants and authorized staff. |
| **Comment publicly** | An open thread on every listing, so several people can help identify something. |
| **Smart matching** | Every post is scored against the opposite side of the board and strong overlaps surface as suggestions — with the reasons why. |
| **Match alerts** | A bell that tells you when something resembling your post turns up, without repeating itself. |
| **My items** | Account-based posts and conversations on any device, including incoming claims. |
| **Campus map** | Interactive UIU map with report pins, suggested search areas, filters and walking directions. |
| **Reunions** | A wall of items that made it home. |
| **Dark mode** | Authored independently of light, not an inversion. Follows the system by default, and remembers your choice. |

**For moderators** — `/admin.html`

Board health at a glance, every post with its reporter, and a comment queue
where a comment can be hidden and restored rather than destroyed.

### Status lifecycle

```
OPEN ──first claim──> PENDING ──both confirm handover──> RESOLVED
  ^                      │
  └──all claims closed───┘
```

An item returns to `OPEN` only when nothing is outstanding, so a single
declined claim cannot strand it at `PENDING`.

## Running it locally

You need a **JDK 21 or newer**. Node is only required if you intend to change
the styling — the compiled stylesheet is committed.

```bash
./run.sh
```

Then open <http://localhost:8080>.

Posting and chat require an approved account. On a fresh database, configure
`APP_BOOTSTRAP_ADMIN_EMAIL` and `APP_BOOTSTRAP_ADMIN_PASSWORD` (at least 12
characters) before startup to create an administrator. Sign in using that email
and password, then approve student registrations in `/admin.html`. Bootstrap
does not reset or promote existing accounts; remove its password from the
environment after the account is created.

For a local demonstration only, explicitly set `APP_DEMO_ENABLED=true` before
starting. This creates `admin` / `admin123`, `student` / `student123`, and a
pending student. Demo credentials and the legacy shared moderation key are
disabled by default. Disabling demo creation does not remove accounts already
in an existing database: change or remove their published passwords before deployment.

`run.sh` picks a real JDK, recompiles the stylesheet if `frontend/node_modules`
is present, and starts the application. To run the pieces by hand instead:

```bash
./mvnw spring-boot:run
```

### Changing the styling

Tailwind scans the HTML and JS under `src/main/resources/static/` and writes a
single stylesheet to `src/main/resources/static/css/app.css`.

```bash
cd frontend
npm install
npm run watch
```

`npm run build` produces the minified production stylesheet. Commit the result
— it is checked in deliberately so a teammate with only a JDK can clone and run
the project without installing Node.

### The database

The application uses **MySQL 8**. `./run.sh` takes care of it automatically:
if `DB_URL` is not set, it starts a dockerised MySQL on port **3307** (your
own MySQL on 3306 is never touched) with data persisted in the
`lostfound-mysql-dev` volume.

To use a native MySQL server instead, create the database and user once:

```bash
sudo mysql -e "CREATE DATABASE IF NOT EXISTS lostfound CHARACTER SET utf8mb4;
               CREATE USER IF NOT EXISTS 'lostfound'@'localhost' IDENTIFIED BY 'lostfound';
               GRANT ALL PRIVILEGES ON lostfound.* TO 'lostfound'@'localhost';"
```

then point the app at it:

```bash
DB_URL="jdbc:mysql://127.0.0.1:3306/lostfound" ./run.sh
```

`DB_USERNAME` and `DB_PASSWORD` override the credentials (both default to
`lostfound`). An empty database seeds itself on first boot. The test suite
runs on an in-memory H2 in MySQL mode, so tests and CI need no database
server at all.

---

## Project layout

```
pom.xml                     Maven build
run.sh                      One-command local start
frontend/                   Tailwind source and toolchain (not shipped)
  src/input.css             Theme tokens, base layer, shared component classes
  genpages.py               Generates every page from one chrome template
src/main/java/…/lostfound/
  domain/                   Item, Claim, ClaimMessage, Comment + enums
  repo/                     Spring Data repositories
  service/                  ItemService, ClaimService, MatchService,
                            StorageService, SeedLoader
  web/                      REST controllers, DTOs, exception handling
  config/                   Static resource handling for uploads
src/main/resources/
  application.properties    Datasource, uploads, server config
  seed/                     JSON seed data loaded on first run
  static/                   Everything the browser receives
    index.html              Landing page
    css/app.css             Compiled Tailwind output (generated, committed)
    js/                     app.js (chrome, API, toasts), ui.js (shared
                            rendering), one script per page. No bundler.
    assets/                 Icon sprite, self-hosted fonts, images
```

---

## API

All JSON, all on the same origin as the pages.

| Method | Path | Purpose |
|---|---|---|
| `GET` | `/api/health` | Liveness, used to tell "API down" from "board empty" |
| `GET` | `/api/items` | Browse. `kind` `status` `category` `q` `sort` `page` `size` |
| `GET` | `/api/items/{ref}` | One item |
| `POST` | `/api/items` | Create (approved session; multipart `item` JSON + optional public `photo` and `privatePhoto`) |
| `GET` | `/api/items/mine` | The signed-in account's posts |
| `GET` | `/api/items/questions?category=KEYS` | Generate a category-specific security question |
| `GET` | `/api/items/{ref}/private-photo` | Protected image; poster, administrator, or claimant after completed handover |
| `GET` | `/api/items/{ref}/matches` | Scored matches from the other side of the board |
| `GET` | `/api/items/stats` | Board-wide counts |
| `GET` | `/api/items/categories` | Category list, so the frontend never hard-codes the enum |
| `GET` `POST` | `/api/items/{ref}/comments` | Public comment thread |
| `POST` | `/api/items/{ref}/claims` | Open a claim |
| `GET` | `/api/claims/{ref}` | One conversation; participant session required |
| `GET` | `/api/claims` | Signed-in account's conversations |
| `POST` | `/api/claims/{ref}/verify` | Poster reviews private evidence; also unlocks legacy chat |
| `POST` | `/api/claims/{ref}/messages` | Reply |
| `POST` | `/api/claims/{ref}/accept` | Approve pickup after evidence/staff checks; does not mark returned |
| `POST` | `/api/claims/{ref}/handover` | Each participant confirms; both are required for return |
| `POST` | `/api/claims/{ref}/decline` · `/withdraw` | Close a claim without clearing unresolved disputes |
| `POST` / `GET` | `/api/claims/{ref}/evidence` | Upload/read a protected claimant evidence image |
| `POST` | `/api/claims/{ref}/flag` · `/desk-review` | Report fraud and freeze handovers, or request staff review |
| `GET` | `/api/claims/{ref}/audit` | Participant-only claim history |
| `GET` | `/api/desk/claims` · `/claims/{ref}` · `/audit` | Staff-only review queue, details and audit log |
| `POST` | `/api/desk/claims/{ref}/review` | Staff clears after ID check or rejects with notes |
| `GET` | `/api/admin/*` | Moderation. Requires an administrator session or explicitly configured legacy key |

Validation failures return a `fields` map of input name to message, so a form
can put each message beside the input that caused it.

### Moderation key

`app.admin.key` is empty by default. Administrator accounts are preferred. Set
`APP_ADMIN_KEY` only if the legacy key flow is needed; the server checks every
moderation endpoint. Never use the former published `campus-admin-2026` key.

## Design decisions

### One brand colour, two signal colours

Indigo is the product's voice — primary actions, focus rings, active states.
Two signal colours carry the only distinction that really matters on this
board: **rose for lost**, **emerald for found**. Category tags add a third
layer of colour, which is what makes a wall of cards scannable — you spot
"keys" by its amber tint before you have read a single word.

Both themes are authored independently rather than one being an inversion of
the other. In dark mode surfaces get *lighter* as they elevate, the soft tints
are darkened instead of lightened, and text colours are re-picked per theme.

**Every colour pair is measured, not eyeballed.** All 38 foreground/surface
combinations across both themes clear WCAG AA; the worst case is 4.63:1 in
light and 5.89:1 in dark. Two failures were caught this way and fixed:
emerald at `#059669` gave only 3.77:1 against white pill text, and in dark
mode white-on-accent collapsed to 1.9–3.0:1 because the accents lighten. The
first darkened to `#047857`, the second switched to dark ink on the chip.

Semantic tokens (`--sc-surface`, `--sc-muted`, …) are re-bound per theme and
consumed through Tailwind's `@theme`, so utilities like `bg-surface` and
`text-muted` flip automatically without a second set of generated classes.

### Type is self-hosted

Inter, subset to Latin plus typographic punctuation and served from
`assets/fonts/` — no CDN request, no third-party connection. The Bengali face
carries a `unicode-range` so its outlines are only downloaded when Bengali
text actually appears.

### Missing photos are designed, not broken

An item without a photograph gets a tinted gradient panel carrying its
category glyph. A grey box with a faint icon reads as a broken image; a
coloured panel reads as a deliberate placeholder.

### Icons are hand-drawn

`assets/icons.svg` is a hand-authored sprite on a 24×24 grid, referenced with
`<use href="…#id">` and stroked with `currentColor`. No icon font, no library.

### Four states, always

Every list view ships a default, a loading skeleton, an empty state, and an
error state. A failed request and an empty board are different things and are
never rendered the same way.

### Pages come from one template

There is no server-side templating engine, so `frontend/genpages.py` generates
every page from a single shared chrome template. Edit the template and re-run
it — never edit the masthead in four files by hand.

### Image privacy and verified chat

The report form accepts one public photograph and one private photograph,
each up to 5 MB (JPEG, PNG, GIF or WebP). The server checks file contents and
stores private photographs in `uploads/private/`, outside public routing.
Private responses are marked `no-store`; public responses never contain a
private filename or security answer. Existing public images remain public.

A category generates a suggested question. The poster edits it if needed and
provides an answer absent from public information. Matching ignores case,
Unicode compatibility differences, and repeated whitespace. Answers use the
same salted PBKDF2 implementation as account passwords. Five incorrect answers
lock further checks for that account for 15 minutes, including across sessions
and application restarts. No claim is created for an incorrect answer.

Legacy posts without an answer still accept ownership proof, but their chat
stays locked until the poster reviews it. Existing conversations also require
this review if they predate verification. Unlinked legacy guest content is not
automatically assigned to accounts by email; operators must verify ownership
before migrating it. Bundled registry posts are assigned to an administrator.

Each chat endpoint checks the current account against the stored participant
IDs. Sender roles come from the session. Only the poster accepts, declines or
verifies a claim; only the claimant withdraws it. Chat refreshes every five
seconds while visible, preserves drafts, and becomes read-only when closed.
Public comments remain available, including guest comments; new private
messages must go through a verified claim. Existing private comments remain
restricted to their linked author, poster and moderators.

Match alerts remain browser-side rechecks; there is no email delivery service.
Uploaded files need persistent storage in production. Docker Compose mounts
the entire uploads directory, including its private subdirectory; the existing
Render free-tier blueprint does not provide a persistent upload disk.

### UIU campus map

The campus map uses a real OpenStreetMap basemap centered on United International
University at `23.7978829, 90.4497100`, from the supplied campus Maps link.
Leaflet 1.9.4 and its license are vendored under `static/assets/vendor/leaflet`;
map imagery needs an internet connection.

When posting, click the reported spot or drag the pin, choose a suggested search
radius (10–500 metres), and describe the floor, room or nearby landmark. Pins are
optional; text-only older reports are never assigned guessed coordinates. Posters
can add, adjust or remove their pin from their item page. The server validates
coordinate pairs and restricts these edits to the approved report owner.

The map supports lost/found filters, search, returned items, shared report links,
and Google Maps walking directions to the saved coordinates. “My location” is
opt-in: the explorer uses it only in browser memory for approximate straight-line
distances and directions, not live tracking. On the report form, “Use my location”
sets the public report pin; check it before publishing. Search circles are
poster estimates, not indoor routing or guaranteed GPS accuracy. Share searches
and sightings through the public “Clues & community updates” thread, keeping
secret verification details in the verified private chat.

To populate a running local server with six pinned UIU reports,
run `node scripts/add-map-reports.mjs` with Node 22+. It uses the local student
account, skips reports already present, and never modifies existing reports.
The security answer for these initial reports is `blue star`.
Optional `SEED_BASE_URL`, `SEED_USERNAME` and `SEED_PASSWORD` environment variables
override the local URL and credentials. Non-local servers are rejected.

### Safe handover

New claims use `OPEN → APPROVED → ACCEPTED`: approval reserves pickup, while
the item remains pending until both distinct participants confirm the physical
handover. The poster must explicitly review private evidence before approval.
Electronics, jewellery and explicitly marked valuables require staff review
and an in-person student-ID-check attestation. Fraud reports freeze every
handover for that item until all disputes are resolved; withdrawal cannot
dismiss a dispute. Staff cannot review their own claim or report.

Open `/help.html` for ownership and collection guidance. Add local reports and
claims with `node scripts/add-claim-reports.mjs`; open `/desk.html` as staff
for reviews. See [the claim and collection guide](docs/CLAIM_AND_COLLECTION_GUIDE.md)
for testing accounts, test commands, database upgrades and security boundaries.

### Verification

Run `./mvnw test` (or `./mvnw.cmd test` on Windows) with Java 21+. Integration
tests use H2 with open-in-view disabled and exercise private image permissions,
chat authorization, spoofed roles, answer lockouts, legacy proof review,
invalid uploads, cross-origin writes, private-comment bypass prevention, exact
coordinate round trips, owner-only location edits and invalid map coordinates.
The suite includes 21 tests, including private claimant evidence, staff gates,
fraud freezes, audit access, two-party handover and concurrent actions. The
`npm run test:fraud` browser suite in `frontend/` exercises the visible workflow
against an isolated local test server (see the claim and collection guide).
`npm run test:content` checks public pages and responsive layouts without changing reports.
Rebuild the committed stylesheet with `npm run build` in `frontend/` after
changing HTML or JavaScript utility classes.

See [the project review](docs/PROJECT_REVIEW.md) for findings and remaining limits.

## Team

| Name | Role | Contact |
|------|------|---------|
| _TBD_ | _TBD_ | _TBD_ |
| _TBD_ | _TBD_ | _TBD_ |
| _TBD_ | _TBD_ | _TBD_ |
| _TBD_ | _TBD_ | _TBD_ |

---

## Browser support

Current Chrome, Firefox, Safari, and Edge. The application uses CSS custom
properties, `fetch`, CSS grid, and `:has()`.

---

## History

An earlier build of this project was a static, framework-free prototype using
hand-written CSS. It is preserved on the
[`v1-static`](../../tree/v1-static) branch.
