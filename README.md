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
