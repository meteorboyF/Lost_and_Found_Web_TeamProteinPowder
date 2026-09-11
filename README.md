# Lost &amp; Found — Campus Registry

A shared board for posting lost and found items across campus. Post what you
lost, hand in what you found, and the application quietly scans the other side
of the board for likely matches.

Built by **Team Protein Powder**.

---

## Stack

| Layer     | Choice |
|-----------|--------|
| Backend   | Spring Boot 4.1 (Java 21), Spring MVC, Spring Data JPA, Bean Validation |
| Database  | H2, file-backed — no external database to install |
| Frontend  | Static HTML + vanilla JavaScript (ES2017), served by Spring Boot |
| Styling   | Tailwind CSS v4, compiled with the Tailwind CLI |

The frontend and backend share a single origin: Spring Boot serves the pages
out of `src/main/resources/static/`, and the pages call the REST API on the
same host. There is no CORS configuration because there is no cross-origin
request.

---

## Running it locally

You need a **JDK 21 or newer**. Node is only required if you intend to change
the styling — the compiled stylesheet is committed.

```bash
./run.sh
```

Then open <http://localhost:8080>.

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

### Inspecting the database

The H2 console is enabled in development at <http://localhost:8080/h2-console>.

- **JDBC URL** — `jdbc:h2:file:./db/lostfound;AUTO_SERVER=TRUE`
- **User** — `sa`, no password

The database file lives in `db/` and is gitignored, so every clone starts clean
and reseeds itself.

---

## Project layout

```
pom.xml                     Maven build
run.sh                      One-command local start
frontend/                   Tailwind source and toolchain (not shipped)
  src/input.css             Theme tokens, base layer, shared component classes
src/main/java/…/lostfound/
  web/                      REST controllers
src/main/resources/
  application.properties    Datasource, uploads, server config
  seed/                     JSON seed data loaded on first run
  static/                   Everything the browser receives
    index.html              Landing page
    css/app.css             Compiled Tailwind output (generated, committed)
    js/                     Page scripts, no bundler
    assets/                 Icon sprite, self-hosted fonts, images
```

---

## Design decisions

### The palette is two colours

Amber/ochre marks the **found** side of the board, crimson marks the **lost**
side. Everything else is a warm neutral ramp between near-black ink and warm
paper. Accent colour appears only on status, primary actions, and focus rings —
nothing else is coloured, so when something *is* coloured it means something.

Both themes are authored independently rather than one being an inversion of
the other: in dark mode surfaces get *lighter* with elevation, hairlines carry
more of the structure, and the accents lose saturation so they do not glow.
Every text-on-surface pair clears WCAG AA in both themes.

Semantic tokens (`--sc-surface`, `--sc-muted`, …) are re-bound per theme and
consumed through Tailwind's `@theme`, so utilities like `bg-surface` and
`text-muted` flip automatically without a second set of generated classes.

### Type is self-hosted

Noto Serif Display for headings, Noto Sans Display for UI, Noto Sans Mono for
anything machine-readable — reference codes, timestamps, counts. All subset to
WOFF2 and served from `assets/fonts/`, with no CDN request. The Bengali faces
carry a `unicode-range` so their outlines are only downloaded when Bengali text
actually appears on the page.

### Icons are hand-drawn

`assets/icons.svg` is a hand-authored sprite on a 24×24 grid, referenced with
`<use href="…#id">` and stroked with `currentColor`. No icon font, no icon
library.

### Four states, always

Every list view ships a default, a loading skeleton, an empty state, and an
error state. A failed request and an empty board are different things and are
never rendered the same way — the landing page shows a live registry-status
indicator for exactly this reason.

---

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
