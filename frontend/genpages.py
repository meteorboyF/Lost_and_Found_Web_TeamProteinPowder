#!/usr/bin/env python3
"""Generate the static pages from one shared chrome template.

Authoring them through a single template is the only way the masthead and
footer stay identical across pages without a server-side templating engine.

    python3 frontend/genpages.py
"""
import pathlib

STATIC = pathlib.Path(__file__).resolve().parent.parent / "src/main/resources/static"

# The hero objects are inlined rather than referenced externally: Chrome does
# not resolve gradient url(#...) fills inside a cross-document <use>, so an
# external sprite would render the art unfilled. The SVG on disk stays the
# source of truth; it is embedded at generation time.
THINGS = (STATIC / "assets/img/things.svg").read_text(encoding="utf-8")
THINGS = THINGS[THINGS.index("<svg"):]  # strip xml prolog + comment header

HEAD = """<!doctype html>
<html lang="en">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>{title}</title>
<meta name="description" content="{description}">

<!-- Applied before first paint so a dark-theme visitor never sees a light flash. -->
<script>
  (function () {{
    try {{
      var t = localStorage.getItem('lf.theme');
      if (t === 'dark' || (!t && matchMedia('(prefers-color-scheme: dark)').matches)) {{
        document.documentElement.setAttribute('data-theme', 'dark');
      }}
    }} catch (e) {{}}
  }})();
</script>

<link rel="preload" href="/assets/fonts/inter-400.woff2" as="font" type="font/woff2" crossorigin>
<link rel="preload" href="/assets/fonts/inter-600.woff2" as="font" type="font/woff2" crossorigin>
<link rel="stylesheet" href="/css/app.css">
<link rel="icon" href="/assets/favicon.svg" type="image/svg+xml">
</head>

<body class="min-h-screen flex flex-col bg-page">

<a class="skip-link" href="#main">Skip to content</a>

<header class="sticky top-0 z-40 border-b border-line bg-page/80 backdrop-blur-xl">
  <div class="mx-auto flex h-16 max-w-7xl items-center gap-3 px-4 sm:px-6 lg:px-8">

    <a href="/" class="flex items-center gap-2.5 font-bold tracking-tight text-heading">
      <span class="grid h-9 w-9 place-items-center rounded-xl bg-brand text-white shadow-[var(--shadow-brand)]">
        <svg class="icon h-5 w-5" aria-hidden="true"><use href="/assets/icons.svg#i-pin"></use></svg>
      </span>
      <span class="text-[1.0625rem]">Lost &amp; Found</span>
    </a>

{primary_nav}
    <div class="ml-auto flex items-center gap-2">
      <div data-auth-nav class="flex items-center"></div>

      <span class="relative" data-alerts>
        <button type="button" data-alerts-toggle aria-expanded="false" aria-haspopup="true"
                aria-controls="alerts-panel" aria-label="Notifications" hidden
                class="grid h-9 w-9 place-items-center rounded-lg text-muted transition hover:bg-sunken hover:text-heading">
          <svg class="icon h-[1.15rem] w-[1.15rem]" aria-hidden="true"><use href="/assets/icons.svg#i-bell"></use></svg>
          <span class="absolute -right-0.5 -top-0.5 grid h-4 min-w-4 place-items-center rounded-full bg-lost px-1 text-[0.625rem] font-bold text-white dark:text-[#11121a]"
                data-alerts-badge hidden>0</span>
        </button>
        <span id="alerts-panel" data-alerts-panel hidden
              class="absolute right-0 top-full z-50 mt-2 block w-[min(22rem,calc(100vw-2rem))] overflow-hidden rounded-xl border border-line bg-surface shadow-[var(--shadow-pop)]"></span>
      </span>

      <button type="button" data-theme-toggle
              class="grid h-9 w-9 place-items-center rounded-lg text-muted transition hover:bg-sunken hover:text-heading"
              aria-label="Switch to dark theme" aria-pressed="false">
        <svg class="icon h-[1.15rem] w-[1.15rem]" aria-hidden="true" data-theme-icon="light"><use href="/assets/icons.svg#i-sun"></use></svg>
        <svg class="icon h-[1.15rem] w-[1.15rem]" aria-hidden="true" data-theme-icon="dark" hidden><use href="/assets/icons.svg#i-moon"></use></svg>
      </button>

{nav_toggle}    </div>
  </div>
</header>

<main id="main" class="flex-1">
"""

FOOT = """</main>

<footer class="mt-20 border-t border-line bg-surface">
  <div class="mx-auto max-w-7xl px-4 py-10 sm:px-6 lg:px-8">
    <div class="flex flex-wrap items-center justify-between gap-6">
      <div class="flex items-center gap-2.5">
        <span class="grid h-8 w-8 place-items-center rounded-lg bg-brand text-white">
          <svg class="icon h-4 w-4" aria-hidden="true"><use href="/assets/icons.svg#i-pin"></use></svg>
        </span>
        <div>
          <p class="text-sm font-semibold text-heading">Lost &amp; Found</p>
          <p class="text-xs text-muted">Team Protein Powder</p>
        </div>
      </div>
      <nav class="flex flex-wrap gap-x-6 gap-y-2 text-sm text-muted" aria-label="Footer">
        <a href="/browse.html" class="inline-block py-1 transition hover:text-heading">Browse</a>
        <a href="/report.html" class="inline-block py-1 transition hover:text-heading">Post an item</a>
        <a href="/dashboard.html" class="inline-block py-1 transition hover:text-heading">My items</a>
        <a href="/map.html" class="inline-block py-1 transition hover:text-heading">Campus map</a>
        <a href="/gallery.html" class="inline-block py-1 transition hover:text-heading">Reunions</a>
        <a href="/help.html" class="inline-block py-1 transition hover:text-heading">Claim &amp; collection guide</a>
      </nav>
      <p class="text-xs text-faint">Items are held for 90 days before archiving.</p>
    </div>
  </div>
</footer>

<div class="pointer-events-none fixed bottom-4 right-4 z-50 flex w-[min(24rem,calc(100vw-2rem))] flex-col-reverse gap-2"
     data-toast-region role="status" aria-live="polite"></div>

<script src="/js/app.js"></script>
<script src="/js/ui.js"></script>
<script src="/js/alerts.js"></script>
{scripts}
</body>
</html>
"""

PRIMARY_NAV = """    <nav id="primary-nav" aria-label="Primary" data-open="false">
      <a href="/browse.html"{nav_browse}>Browse</a>
      <a href="/report.html"{nav_report}>Post an item</a>
      <a href="/dashboard.html"{nav_dash}>My items</a>
      <a href="/map.html"{nav_map}>Campus map</a>
      <a href="/gallery.html"{nav_gallery}>Reunions</a>
    </nav>
"""

NAV_TOGGLE = """      <button type="button" data-nav-toggle aria-controls="primary-nav" aria-expanded="false"
              aria-label="Open navigation"
              class="grid h-9 w-9 place-items-center rounded-lg text-muted transition hover:bg-sunken hover:text-heading">
        <svg class="icon h-[1.15rem] w-[1.15rem]" aria-hidden="true"><use href="/assets/icons.svg#i-menu"></use></svg>
      </button>
"""

CURRENT = ' aria-current="page"'


def page(filename, title, description, body, scripts, active=None, public_nav=True, map_assets=False):
    """Render one page.

    public_nav=False drops the site navigation — used by the moderation
    workspace, which is a separate surface and should not offer the public
    board's links. The mobile toggle is dropped with it: a hamburger whose
    aria-controls target does not exist is a visible button that does nothing.
    """
    nav = {k: "" for k in ("nav_browse", "nav_report", "nav_dash", "nav_map", "nav_gallery")}
    if active:
        nav[active] = CURRENT

    chrome = {
        "primary_nav": PRIMARY_NAV.format(**nav) if public_nav else "",
        "nav_toggle": NAV_TOGGLE if public_nav else "",
    }

    html = HEAD.format(title=title, description=description, **chrome) + body + FOOT.format(scripts=scripts)
    if map_assets:
        html = html.replace('</head>', '<link rel="stylesheet" href="/assets/vendor/leaflet/leaflet.css">\n<link rel="stylesheet" href="/css/maps.css">\n</head>')
    (STATIC / filename).write_text(html, encoding="utf-8")
    print(f"wrote {filename} ({len(html):,} bytes)")


# ============================== landing ==============================

HOME_BODY = """
<!-- hero -->
<section class="hero-wash relative overflow-hidden border-b border-line">
  <div class="hero-dots pointer-events-none absolute inset-0" aria-hidden="true"></div>
  <div class="relative mx-auto max-w-7xl px-4 pb-16 pt-16 sm:px-6 lg:px-8 lg:pb-24 lg:pt-24">
    <div class="grid items-center gap-12 lg:grid-cols-2">

      <div>
        <span class="pill pill-lg bg-surface text-brand-text shadow-[var(--shadow-card)]">
          <svg class="icon h-4 w-4" aria-hidden="true"><use href="/assets/icons.svg#i-sparkle"></use></svg>
          Smart matching, automatically
        </span>

        <h1 class="display mt-6 text-[2.75rem] text-heading sm:text-6xl lg:text-[4rem]">
          Someone already<br>found it.
        </h1>

        <p class="mt-6 max-w-lg text-lg leading-relaxed text-body">
          Post what you lost and we scan every item handed in across campus —
          then tell you the moment something lines up.
        </p>

        <div class="mt-8 flex flex-wrap gap-3">
          <a href="/report.html?kind=lost" class="btn btn-lost btn-lg">
            I lost something
            <svg class="icon h-[1.15rem] w-[1.15rem]" aria-hidden="true"><use href="/assets/icons.svg#i-arrow-right"></use></svg>
          </a>
          <a href="/report.html?kind=found" class="btn btn-secondary btn-lg">I found something</a>
        </div>

        <div class="mt-10 flex flex-wrap items-center gap-x-8 gap-y-4">
          <div>
            <p class="text-3xl font-extrabold tabular-nums text-heading" data-stat="resolved">—</p>
            <p class="text-sm text-muted">Reunited with owners</p>
          </div>
          <div class="h-10 w-px bg-line"></div>
          <div>
            <p class="text-3xl font-extrabold tabular-nums text-heading" data-stat="open">—</p>
            <p class="text-sm text-muted">Waiting to be claimed</p>
          </div>
        </div>
      </div>

      <!-- The lost & found still life: common lost objects floating in a
           3D stage. Pointer tilt is added by home.js; decorative only. -->
      <div class="hero-stage relative mx-auto h-64 w-full max-w-md sm:h-80 lg:h-[26rem] lg:max-w-none"
           data-hero-stage aria-hidden="true">
{things}
        <div class="hero-tilt relative h-full w-full" data-hero-tilt>

          <div class="float-obj left-[38%] top-[26%] w-36 lg:w-44" data-depth="30"
               style="--dur:5.5s; --delay:.6s; --rot:3deg; --sway:3deg">
            <svg viewBox="0 0 120 120" class="w-full"><use href="#o-keys"></use></svg>
          </div>

          <div class="float-obj right-[4%] top-[2%] w-32 lg:w-40" data-depth="22"
               style="--dur:7s; --rot:-4deg; --sway:-3deg">
            <svg viewBox="0 0 120 120" class="w-full"><use href="#o-bag"></use></svg>
          </div>

          <div class="float-obj hidden left-[2%] top-[4%] w-24 sm:grid lg:w-28" data-depth="14"
               style="--dur:6.5s; --delay:.3s; --rot:6deg; --sway:2deg">
            <svg viewBox="0 0 120 120" class="w-full"><use href="#o-bottle"></use></svg>
          </div>

          <div class="float-obj left-[8%] top-[52%] w-24 lg:w-32" data-depth="18"
               style="--dur:6s; --delay:1.1s; --rot:-7deg; --sway:-2deg">
            <svg viewBox="0 0 120 120" class="w-full"><use href="#o-phone"></use></svg>
          </div>

          <div class="float-obj right-[8%] top-[54%] w-28 lg:w-36" data-depth="26"
               style="--dur:7.5s; --delay:.9s; --rot:5deg; --sway:3deg">
            <svg viewBox="0 0 120 120" class="w-full"><use href="#o-headphones"></use></svg>
          </div>

          <div class="float-obj hidden left-[35%] top-[68%] w-28 md:grid lg:w-36" data-depth="12"
               style="--dur:8s; --delay:1.5s; --rot:-3deg; --sway:2deg">
            <svg viewBox="0 0 120 120" class="w-full"><use href="#o-card"></use></svg>
          </div>

        </div>
      </div>
    </div>
  </div>
</section>

<!-- how it works -->
<section class="mx-auto max-w-7xl px-4 py-16 sm:px-6 lg:px-8 lg:py-20">
  <div class="mx-auto max-w-2xl text-center">
    <h2 class="text-3xl text-heading sm:text-4xl">Three steps, no paperwork</h2>
    <p class="mt-4 text-body">The whole thing takes less than a minute.</p>
  </div>

  <ol class="mt-12 grid gap-6 md:grid-cols-3">
    <li class="card p-6">
      <div class="grid h-11 w-11 place-items-center rounded-xl bg-brand-soft text-brand">
        <svg class="icon h-5 w-5" aria-hidden="true"><use href="/assets/icons.svg#i-camera"></use></svg>
      </div>
      <h3 class="mt-5 text-lg text-heading">Post it</h3>
      <p class="mt-2 text-sm leading-relaxed text-muted">
        A photo, a short description, and roughly where it happened.
      </p>
    </li>
    <li class="card p-6">
      <div class="grid h-11 w-11 place-items-center rounded-xl bg-found-soft text-found">
        <svg class="icon h-5 w-5" aria-hidden="true"><use href="/assets/icons.svg#i-sparkle"></use></svg>
      </div>
      <h3 class="mt-5 text-lg text-heading">We match it</h3>
      <p class="mt-2 text-sm leading-relaxed text-muted">
        Every post is scanned against the other side of the board. Strong overlaps
        surface as a suggested match.
      </p>
    </li>
    <li class="card p-6">
      <div class="grid h-11 w-11 place-items-center rounded-xl bg-amber-soft text-amber">
        <svg class="icon h-5 w-5" aria-hidden="true"><use href="/assets/icons.svg#i-message"></use></svg>
      </div>
      <h3 class="mt-5 text-lg text-heading">Claim it</h3>
      <p class="mt-2 text-sm leading-relaxed text-muted">
        Recognise something? Start a conversation and arrange the handover.
      </p>
    </li>
  </ol>
</section>

<!-- categories -->
<section class="border-y border-line bg-surface">
  <div class="mx-auto max-w-7xl px-4 py-16 sm:px-6 lg:px-8">
    <div class="flex flex-wrap items-end justify-between gap-4">
      <div>
        <h2 class="text-2xl text-heading sm:text-3xl">Browse by category</h2>
        <p class="mt-2 text-body">Jump straight to the kind of thing you are missing.</p>
      </div>
    </div>
    <div class="mt-8 grid grid-cols-2 gap-4 sm:grid-cols-4" data-category-tiles></div>
  </div>
</section>

<!-- recent -->
<section class="mx-auto max-w-7xl px-4 py-16 sm:px-6 lg:px-8">
  <div class="mb-8 flex flex-wrap items-end justify-between gap-4">
    <div>
      <h2 class="text-2xl text-heading sm:text-3xl">Just posted</h2>
      <p class="mt-2 text-body">The newest items on the board.</p>
    </div>
    <a href="/browse.html" class="btn btn-secondary btn-sm">
      See everything
      <svg class="icon h-4 w-4" aria-hidden="true"><use href="/assets/icons.svg#i-arrow-right"></use></svg>
    </a>
  </div>

  <div data-recent data-empty-message="Nothing has been posted yet. The board fills up fast once term starts."></div>

  <p class="mt-8 flex items-center justify-center gap-2 text-xs text-faint" role="status">
    <span class="inline-block h-1.5 w-1.5 rounded-full bg-faint" data-api-dot></span>
    <span data-api-label>Checking the registry…</span>
  </p>
</section>
"""

# ============================== browse ==============================

BROWSE_BODY = """
<div class="mx-auto max-w-7xl px-4 py-10 sm:px-6 lg:px-8">

  <div class="mb-8">
    <h1 class="text-3xl text-heading sm:text-4xl">Browse the board</h1>
    <p class="mt-3 max-w-2xl text-body">
      Every item posted across campus. Filter down to the ones worth checking.
    </p>
  </div>

  <div class="lg:grid lg:grid-cols-[17rem_minmax(0,1fr)] lg:gap-10">

    <aside class="mb-6 lg:mb-0">
      <!-- On a phone the full filter stack would push every result below the
           fold, so it collapses behind this toggle. Desktop shows it always. -->
      <button type="button" data-filter-toggle aria-expanded="false" aria-controls="filter-panel"
              class="btn btn-secondary mb-4 w-full lg:hidden">
        <svg class="icon h-[1.15rem] w-[1.15rem]" aria-hidden="true"><use href="/assets/icons.svg#i-filter"></use></svg>
        Filters
        <span class="pill bg-brand text-white dark:text-[#11121a]" data-filter-count hidden>0</span>
      </button>
      <form data-filters id="filter-panel" class="hidden lg:block lg:sticky lg:top-24">

        <div class="relative mb-5 flex items-center">
          <label for="f-q" class="sr-only">Search items</label>
          <svg class="icon pointer-events-none absolute left-3.5 h-[1.15rem] w-[1.15rem] text-faint" aria-hidden="true">
            <use href="/assets/icons.svg#i-search"></use>
          </svg>
          <input type="search" id="f-q" maxlength="100" placeholder="Search the board" class="field pl-11">
        </div>

        <div class="card overflow-hidden">
          <fieldset class="border-b border-line p-4">
            <legend class="label mb-3">Side of the board</legend>
            <div class="flex flex-wrap gap-2" role="group">
              <button type="button" data-kind="" aria-pressed="true"
                      class="pill border border-line-strong text-body transition aria-[pressed=true]:border-brand aria-[pressed=true]:bg-brand aria-[pressed=true]:text-white">All</button>
              <button type="button" data-kind="LOST" aria-pressed="false"
                      class="pill border border-line-strong text-body transition aria-[pressed=true]:border-lost aria-[pressed=true]:bg-lost aria-[pressed=true]:text-white">Lost</button>
              <button type="button" data-kind="FOUND" aria-pressed="false"
                      class="pill border border-line-strong text-body transition aria-[pressed=true]:border-found aria-[pressed=true]:bg-found aria-[pressed=true]:text-white">Found</button>
            </div>
          </fieldset>

          <fieldset class="border-b border-line p-4">
            <legend class="label mb-3">Category</legend>
            <div class="grid gap-0.5" data-categories>
              <p class="text-sm text-faint">Loading…</p>
            </div>
          </fieldset>

          <fieldset class="p-4">
            <legend class="label mb-3">Status</legend>
            <div class="grid gap-0.5">
              <label class="flex cursor-pointer items-center gap-2.5 rounded-lg px-2 py-1.5 text-sm text-body transition hover:bg-sunken">
                <input type="radio" name="status" value="" checked class="h-4 w-4 accent-[var(--sc-brand)]">Any</label>
              <label class="flex cursor-pointer items-center gap-2.5 rounded-lg px-2 py-1.5 text-sm text-body transition hover:bg-sunken">
                <input type="radio" name="status" value="OPEN" class="h-4 w-4 accent-[var(--sc-brand)]">Open</label>
              <label class="flex cursor-pointer items-center gap-2.5 rounded-lg px-2 py-1.5 text-sm text-body transition hover:bg-sunken">
                <input type="radio" name="status" value="PENDING" class="h-4 w-4 accent-[var(--sc-brand)]">In progress</label>
              <label class="flex cursor-pointer items-center gap-2.5 rounded-lg px-2 py-1.5 text-sm text-body transition hover:bg-sunken">
                <input type="radio" name="status" value="RESOLVED" class="h-4 w-4 accent-[var(--sc-brand)]">Resolved</label>
            </div>
          </fieldset>
        </div>

        <button type="button" data-clear class="btn btn-ghost btn-sm mt-3 w-full">Clear all filters</button>
      </form>
    </aside>

    <div>
      <div class="mb-6 flex flex-wrap items-center justify-between gap-3">
        <p class="text-sm text-muted" role="status" data-count>Loading the board…</p>
        <div class="flex items-center gap-2">
          <label for="f-sort" class="text-sm text-muted">Sort</label>
          <select id="f-sort" class="field w-auto py-2 text-sm">
            <option value="recent">Most recent</option>
            <option value="oldest">Oldest first</option>
            <option value="title">Title A–Z</option>
          </select>
        </div>
      </div>

      <div data-results></div>
      <div data-pager></div>
    </div>
  </div>
</div>
"""

# ============================== report ==============================

REPORT_BODY = """
<div class="mx-auto max-w-3xl px-4 py-10 sm:px-6 lg:px-8">

  <div class="mb-8" data-intro>
    <h1 class="text-3xl text-heading sm:text-4xl" data-heading>Post an item</h1>
    <p class="mt-3 text-body" data-subheading>
      The more specific you are, the better the matching works.
    </p>
  </div>

  <fieldset class="mb-6" data-kind-block>
    <legend class="label mb-3">What happened?</legend>
    <div class="grid gap-3 sm:grid-cols-2">
      <label class="card cursor-pointer p-4 transition has-[:checked]:border-lost has-[:checked]:bg-lost-soft has-[:checked]:shadow-[var(--shadow-lift)] hover:border-line-strong">
        <span class="flex items-start gap-3">
          <input type="radio" name="kind" value="LOST" class="mt-1 h-4 w-4 accent-[var(--sc-lost)]">
          <span>
            <span class="flex items-center gap-2 font-semibold text-heading">
              <svg class="icon h-4 w-4 text-lost" aria-hidden="true"><use href="/assets/icons.svg#i-search"></use></svg>
              I lost something
            </span>
            <span class="mt-1 block text-sm text-muted">We will watch the found side for you.</span>
          </span>
        </span>
      </label>
      <label class="card cursor-pointer p-4 transition has-[:checked]:border-found has-[:checked]:bg-found-soft has-[:checked]:shadow-[var(--shadow-lift)] hover:border-line-strong">
        <span class="flex items-start gap-3">
          <input type="radio" name="kind" value="FOUND" class="mt-1 h-4 w-4 accent-[var(--sc-found)]">
          <span>
            <span class="flex items-center gap-2 font-semibold text-heading">
              <svg class="icon h-4 w-4 text-found" aria-hidden="true"><use href="/assets/icons.svg#i-hand"></use></svg>
              I found something
            </span>
            <span class="mt-1 block text-sm text-muted">Post it so the owner can find you.</span>
          </span>
        </span>
      </label>
    </div>
    <p class="mt-2 min-h-5 text-sm text-lost" data-error="kind"></p>
  </fieldset>

  <form data-report novalidate class="card divide-y divide-line">

    <div class="grid gap-5 p-5 sm:p-6">
      <div class="grid gap-1.5">
        <label for="title" class="label flex items-baseline justify-between">
          What is it?
          <span class="font-mono text-xs font-normal text-faint"><span data-count-for="title">0</span>/120</span>
        </label>
        <input id="title" name="title" maxlength="120" placeholder="Black wireless earbuds"
               class="field" aria-describedby="title-error">
        <p class="min-h-5 text-sm text-lost" id="title-error" data-error="title"></p>
      </div>

      <div class="grid gap-5 sm:grid-cols-2">
        <div class="grid gap-1.5">
          <label for="category" class="label">Category</label>
          <select id="category" name="category" class="field" aria-describedby="category-error">
            <option value="">Choose one…</option>
          </select>
          <p class="min-h-5 text-sm text-lost" id="category-error" data-error="category"></p>
        </div>
        <div class="grid gap-1.5">
          <label for="colour" class="label">Colour <span class="font-normal text-faint">optional</span></label>
          <input id="colour" name="colour" maxlength="40" placeholder="Black" class="field">
          <p class="min-h-5 text-sm text-lost" data-error="colour"></p>
        </div>
      </div>

      <div class="grid gap-1.5">
        <label for="description" class="label">Describe it</label>
        <textarea id="description" name="description" rows="4" maxlength="2000"
                  placeholder="Anything that would help someone recognise it — brand, marks, what was inside."
                  class="field resize-y" aria-describedby="description-error"></textarea>
        <p class="min-h-5 text-sm text-lost" id="description-error" data-error="description"></p>
      </div>

      <div class="grid gap-5 sm:grid-cols-2">
        <div class="grid gap-1.5">
          <label for="location" class="label">Where?</label>
          <input id="location" name="location" maxlength="160" placeholder="Central Library, level 2"
                 class="field" aria-describedby="location-error">
          <p class="min-h-5 text-sm text-lost" id="location-error" data-error="location"></p>
        </div>
        <div class="grid gap-1.5">
          <label for="happenedOn" class="label">When? <span class="font-normal text-faint">optional</span></label>
          <input id="happenedOn" name="happenedOn" type="date" class="field" aria-describedby="happenedOn-error">
          <p class="min-h-5 text-sm text-lost" id="happenedOn-error" data-error="happenedOn"></p>
        </div>
      </div>
    </div>

    <div class="p-5 sm:p-6">
      <h2 class="text-xl text-heading">Pin the reported spot</h2>
      <p class="mt-2 mb-4 text-sm text-muted">Choose where you last saw the item, or where you found it. Click to place a pin, then drag it to the right spot. A pin is optional if you are unsure.</p>
      <div data-location-picker></div>
    </div>

    <div class="p-5 sm:p-6">
      <span class="label">Public photograph <span class="font-normal text-faint">optional; visible to everyone</span></span>
      <label class="mt-3 flex cursor-pointer flex-col items-center gap-2 rounded-xl border-2 border-dashed border-line-strong
                    bg-sunken px-6 py-10 text-center transition hover:border-brand hover:bg-brand-soft
                    has-[:focus-visible]:border-brand" data-dropzone>
        <span class="grid h-11 w-11 place-items-center rounded-xl bg-surface text-brand shadow-[var(--shadow-card)]">
          <svg class="icon h-5 w-5" aria-hidden="true"><use href="/assets/icons.svg#i-upload"></use></svg>
        </span>
        <span class="mt-1 font-semibold text-heading">Add a photo</span>
        <span class="text-xs text-muted">JPEG, PNG, WebP or GIF · up to 5 MB</span>
        <input type="file" name="photo" accept="image/jpeg,image/png,image/webp,image/gif" class="sr-only">
      </label>

      <div class="mt-3 hidden" data-preview>
        <div class="flex items-center gap-4 rounded-xl border border-line bg-sunken p-3">
          <img alt="" class="h-16 w-16 rounded-lg object-cover" data-preview-img>
          <div class="min-w-0 flex-1">
            <p class="truncate text-sm font-semibold text-heading" data-preview-name></p>
            <p class="font-mono text-xs text-muted" data-preview-size></p>
          </div>
          <button type="button" data-preview-remove class="btn btn-ghost btn-sm text-lost">Remove</button>
        </div>
      </div>
      <p class="min-h-5 text-sm text-lost" data-error="photo"></p>
      <label for="privatePhoto" class="label">Private photograph (optional)</label>
      <p class="mt-1 text-sm text-muted">Keep identifying details here. Only you and administrators can view it until handover is completed.</p>
      <input id="privatePhoto" type="file" name="privatePhoto" accept="image/jpeg,image/png,image/webp,image/gif" class="field mt-3">
      <div class="mt-3 hidden" data-private-preview><img alt="Private photo preview" class="h-16 w-16 rounded-lg object-cover" data-private-preview-img></div>
      <p class="min-h-5 text-sm text-lost" data-error="privatePhoto"></p>
    </div>

    <div class="grid gap-3 p-5 sm:p-6">
      <label class="flex items-start gap-3 text-sm text-body"><input type="checkbox" name="deskReviewRequired" class="mt-1"><span>Require campus-desk review for this item<br><span class="text-xs text-muted">Electronics and jewellery always require staff review and an in-person student-ID check. Select this for other valuable items.</span></span></label>
      <h2 class="text-xl text-heading">Security question for chat</h2>
      <p class="text-sm text-muted">A question is generated from the category. Edit it if needed, then set a secret answer that is absent from the public photo and description. Choose a detail the owner or finder can know.</p>
      <label for="securityQuestion" class="label">Question</label>
      <input id="securityQuestion" name="securityQuestion" maxlength="300" class="field" aria-describedby="securityQuestion-error">
      <button type="button" data-generate-question class="btn btn-secondary">Generate question</button>
      <p id="securityQuestion-error" class="min-h-5 text-sm text-lost" data-error="securityQuestion"></p>
      <label for="securityAnswer" class="label">Secret answer</label>
      <input id="securityAnswer" name="securityAnswer" type="password" minlength="3" maxlength="200" autocomplete="new-password" class="field" aria-describedby="securityAnswer-error">
      <p id="securityAnswer-error" class="min-h-5 text-sm text-lost" data-error="securityAnswer"></p>
      <p class="text-xs text-muted">Answers ignore letter case and extra spaces. They are stored as salted hashes.</p>
    </div>

    <div class="grid gap-5 p-5 sm:grid-cols-2 sm:p-6">
      <div class="sm:col-span-2">
        <span class="label">How we reach you</span>
        <p class="mt-1 text-sm text-muted">
          Your signed-in account is used for your posts and conversations. Your email is never shown on the board.
        </p>
      </div>
      <div class="grid gap-1.5">
        <label for="reporterName" class="label">Your name</label>
        <input id="reporterName" name="reporterName" maxlength="80" placeholder="Fardin Jahangir"
               class="field" aria-describedby="reporterName-error">
        <p class="min-h-5 text-sm text-lost" id="reporterName-error" data-error="reporterName"></p>
      </div>
      <div class="grid gap-1.5">
        <label for="reporterEmail" class="label">Email</label>
        <input id="reporterEmail" name="reporterEmail" type="email" maxlength="160"
               placeholder="you@university.edu" class="field" aria-describedby="reporterEmail-error">
        <p class="min-h-5 text-sm text-lost" id="reporterEmail-error" data-error="reporterEmail"></p>
      </div>
    </div>

    <div class="flex flex-wrap items-center gap-3 bg-sunken p-5 sm:p-6">
      <button type="submit" data-submit class="btn btn-primary btn-lg">Post to the board</button>
      <a href="/browse.html" class="btn btn-ghost">Cancel</a>
    </div>
  </form>

  <div class="hidden" data-success></div>
</div>
"""

# ============================== item ==============================

ITEM_BODY = """
<div class="mx-auto max-w-5xl px-4 py-8 sm:px-6 lg:px-8">
  <nav aria-label="Breadcrumb" class="mb-6 flex flex-wrap items-center gap-2 text-sm text-muted">
    <a href="/" class="inline-block py-1 transition hover:text-heading">Home</a>
    <svg class="icon h-3.5 w-3.5 text-faint" aria-hidden="true"><use href="/assets/icons.svg#i-chevron-right"></use></svg>
    <a href="/browse.html" class="inline-block py-1 transition hover:text-heading">Browse</a>
    <svg class="icon h-3.5 w-3.5 text-faint" aria-hidden="true"><use href="/assets/icons.svg#i-chevron-right"></use></svg>
    <span data-crumb class="font-mono text-xs">…</span>
  </nav>

  <div data-item></div>
</div>
"""


# ============================== claim ==============================

CLAIM_BODY = """
<div class="mx-auto max-w-5xl px-4 py-8 sm:px-6 lg:px-8">
  <nav aria-label="Breadcrumb" class="mb-6 flex flex-wrap items-center gap-2 text-sm text-muted">
    <a href="/dashboard.html" class="inline-block py-1 transition hover:text-heading">My items</a>
    <svg class="icon h-3.5 w-3.5 text-faint" aria-hidden="true"><use href="/assets/icons.svg#i-chevron-right"></use></svg>
    <span data-crumb class="font-mono text-xs">…</span>
  </nav>

  <div data-claim></div>
</div>
"""


# ============================== dashboard ==============================

DASH_BODY = """
<div class="mx-auto max-w-7xl px-4 py-10 sm:px-6 lg:px-8">

  <div class="mb-8 flex flex-wrap items-end justify-between gap-4">
    <div>
      <h1 class="text-3xl text-heading sm:text-4xl" data-greeting>My items</h1>
      <p class="mt-3 max-w-2xl text-body">
        Everything your account has posted or claimed, on any device.
      </p>
    </div>
    <button type="button" data-forget class="btn btn-ghost btn-sm text-lost">Clear local suggestions</button>
  </div>

  <div class="mb-6 rounded-xl border border-line bg-brand-soft p-4 text-sm leading-relaxed text-brand-text">
    <svg class="icon mb-1 h-[1.15rem] w-[1.15rem]" aria-hidden="true"><use href="/assets/icons.svg#i-info"></use></svg>
    <p>
      Your posts and private conversations belong to your signed-in account.
      Claimants answer an ownership question before chat opens. A reference code
      identifies a conversation; access is limited to its two participants.
    </p>
  </div>

  <div class="mb-6 border-b border-line">
    <div class="flex gap-1 overflow-x-auto" role="tablist" aria-label="My activity">
      <button role="tab" type="button" data-tab="posts" aria-selected="true"
              class="relative -mb-px whitespace-nowrap border-b-2 border-transparent px-4 py-3 text-sm font-medium text-muted transition hover:text-heading aria-[selected=true]:border-brand aria-[selected=true]:font-semibold aria-[selected=true]:text-brand">
        My posts <span class="ml-1 font-mono text-xs text-faint" data-count="posts">0</span>
      </button>
      <button role="tab" type="button" data-tab="claims" aria-selected="false"
              class="relative -mb-px whitespace-nowrap border-b-2 border-transparent px-4 py-3 text-sm font-medium text-muted transition hover:text-heading aria-[selected=true]:border-brand aria-[selected=true]:font-semibold aria-[selected=true]:text-brand">
        My claims <span class="ml-1 font-mono text-xs text-faint" data-count="claims">0</span>
      </button>
      <button role="tab" type="button" data-tab="incoming" aria-selected="false"
              class="relative -mb-px flex items-center gap-2 whitespace-nowrap border-b-2 border-transparent px-4 py-3 text-sm font-medium text-muted transition hover:text-heading aria-[selected=true]:border-brand aria-[selected=true]:font-semibold aria-[selected=true]:text-brand">
        Claims on my posts <span class="font-mono text-xs text-faint" data-count="incoming">0</span>
        <span class="grid h-5 min-w-5 place-items-center rounded-full bg-lost px-1.5 text-xs font-bold text-white dark:text-[#11121a]"
              data-incoming-badge hidden>0</span>
      </button>
    </div>
  </div>

  <div data-dash></div>
</div>
"""


# ============================== gallery ==============================

GALLERY_BODY = """
<div class="mx-auto max-w-7xl px-4 py-10 sm:px-6 lg:px-8">
  <div class="mb-8 text-center">
    <span class="pill pill-lg bg-found-soft text-found-text">
      <svg class="icon h-4 w-4" aria-hidden="true"><use href="/assets/icons.svg#i-award"></use></svg>
      Reunions
    </span>
    <h1 class="mt-4 text-3xl text-heading sm:text-4xl">Back where they belong</h1>
    <p class="mx-auto mt-3 max-w-xl text-body">
      Every item here made it home. <strong class="font-semibold text-heading" data-gallery-count>—</strong>
      so far, <strong class="font-semibold text-heading" data-gallery-rate>—</strong> of everything posted.
    </p>
  </div>

  <div data-gallery></div>
</div>
"""

# ============================== map ==============================

MAP_BODY = """
<div class="campus-explorer mx-auto max-w-7xl px-4 py-8 sm:px-6 lg:px-8">
  <div class="map-page-heading">
    <div>
      <p class="map-eyebrow">UNITED INTERNATIONAL UNIVERSITY</p>
      <h1 class="text-3xl text-heading sm:text-4xl">A place to start looking.</h1>
      <p class="mt-3 max-w-2xl text-muted">Follow a reported pin, explore the surrounding area, and share what you find. A small clue can bring something home.</p>
    </div>
    <a href="/report.html?kind=LOST" class="btn btn-primary"><svg class="icon" aria-hidden="true"><use href="/assets/icons.svg#i-pin"></use></svg>Report &amp; drop a pin</a>
  </div>
  <div class="map-summary" aria-label="Map overview">
    <span><i class="map-dot map-dot-lost"></i><strong data-map-lost>0</strong> lost</span>
    <span><i class="map-dot map-dot-found"></i><strong data-map-found>0</strong> found</span>
    <span><svg class="icon" aria-hidden="true"><use href="/assets/icons.svg#i-pin"></use></svg><strong data-map-pinned>0</strong> with a pin</span>
    <span class="map-summary-note">Reported locations · community clues</span>
  </div>
  <div class="map-workspace">
    <aside class="map-sidebar" aria-label="Reports and location details">
      <div class="map-sidebar-tools">
        <label for="map-search" class="sr-only">Search reports by item or location</label>
        <div class="map-search-wrap"><svg class="icon" aria-hidden="true"><use href="/assets/icons.svg#i-search"></use></svg><input id="map-search" class="field" type="search" placeholder="Search an item or location" data-map-search></div>
        <div class="map-kind-tabs" role="group" aria-label="Filter reports">
          <button type="button" data-map-kind="" aria-pressed="true">All</button>
          <button type="button" data-map-kind="LOST" aria-pressed="false">Lost</button>
          <button type="button" data-map-kind="FOUND" aria-pressed="false">Found</button>
        </div>
        <div class="map-options"><label><input type="checkbox" data-map-resolved> Include returned items</label><button type="button" data-map-refresh aria-label="Refresh reports">Refresh</button></div>
      </div>
      <div data-map-selection></div>
      <div class="map-list-heading"><h2 data-map-heading>Reports near campus</h2><span data-map-count>0</span></div>
      <div class="map-report-list" data-map-list><p class="map-list-empty">Loading reports…</p></div>
    </aside>
    <div class="map-stage">
      <div class="map-floating-tools"><button type="button" class="map-control" data-map-campus><svg class="icon" aria-hidden="true"><use href="/assets/icons.svg#i-pin"></use></svg>UIU campus</button><button type="button" class="map-control" data-map-fit>Show all pins</button><button type="button" class="map-control" data-map-locate>My location</button></div>
      <div class="geo-map campus-real-map" data-map aria-label="Interactive UIU map with reported lost and found locations"></div>
      <div class="map-compass" aria-hidden="true"><span>N</span><svg viewBox="0 0 24 24"><path d="m12 3 6 17-6-4-6 4Z" fill="currentColor"/></svg></div>
      <div class="map-bottom-note"><span class="map-note-icon">⌖</span><div><strong>Start at the reported spot</strong><p>Select a pin for directions and community clues.</p></div></div>
    </div>
  </div>
  <p class="map-service-status" data-map-status role="status" aria-live="polite"></p>
  <p class="map-footnote">Pins show where an item was reported lost or found. Shaded areas are the poster’s suggested search area. Your device location is used only when you choose “My location”. Map imagery: <a href="https://www.openstreetmap.org/copyright" target="_blank" rel="noopener noreferrer">OpenStreetMap contributors</a>.</p>
</div>
"""


# ============================== admin ==============================

ADMIN_BODY = """
<div class="mx-auto max-w-7xl px-4 py-10 sm:px-6 lg:px-8">

  <!-- gate -->
  <div data-admin-gate hidden>
    <div class="mx-auto max-w-md">
      <div class="card p-6">
        <span class="grid h-11 w-11 place-items-center rounded-xl bg-brand-soft text-brand">
          <svg class="icon h-5 w-5" aria-hidden="true"><use href="/assets/icons.svg#i-lock"></use></svg>
        </span>
        <h1 class="mt-4 text-2xl text-heading">Moderation</h1>
        <p class="mt-2 text-sm text-body">
          Sign in with an administrator account to manage posts, comments and student approvals.
        </p>
        <a href="/login.html?redirect=%2Fadmin.html" class="btn btn-primary mt-4 w-full">Sign in as administrator</a>
        <form class="mt-5 grid gap-1.5" data-admin-form novalidate>
          <label for="adminKey" class="label">Legacy moderation key (if configured)</label>
          <input id="adminKey" name="adminKey" type="password" class="field" autocomplete="current-password">
          <p class="min-h-5 text-sm text-lost" data-admin-error></p>
          <button type="submit" class="btn btn-primary mt-1">Open workspace</button>
        </form>
      </div>
      <p class="mt-4 text-center text-xs text-faint">
        Administrator access is checked on the server for every request.
        Shared-key access is disabled unless explicitly configured by the operator.
      </p>
    </div>
  </div>

  <!-- workspace -->
  <div data-admin-app hidden>
    <div class="mb-8 flex flex-wrap items-end justify-between gap-4">
      <div>
        <h1 class="text-3xl text-heading sm:text-4xl">Moderation</h1>
        <p class="mt-2 text-body">Board health, posts, and the comment queue.</p>
      </div>
      <div class="flex gap-3"><a href="/desk.html" class="btn btn-secondary btn-sm">Campus desk &amp; fraud review</a><button type="button" data-admin-signout class="btn btn-ghost btn-sm">Sign out</button></div>
    </div>

    <div class="mb-6 border-b border-line">
      <div class="flex gap-1 overflow-x-auto" role="tablist" aria-label="Moderation sections">
        <button role="tab" type="button" data-tab="overview" aria-selected="true"
                class="-mb-px whitespace-nowrap border-b-2 border-transparent px-4 py-3 text-sm font-medium text-muted transition hover:text-heading aria-[selected=true]:border-brand aria-[selected=true]:font-semibold aria-[selected=true]:text-brand">Overview</button>
        <button role="tab" type="button" data-tab="items" aria-selected="false"
                class="-mb-px whitespace-nowrap border-b-2 border-transparent px-4 py-3 text-sm font-medium text-muted transition hover:text-heading aria-[selected=true]:border-brand aria-[selected=true]:font-semibold aria-[selected=true]:text-brand">
          Posts <span class="ml-1 font-mono text-xs text-faint" data-admin-count="items">0</span></button>
        <button role="tab" type="button" data-tab="comments" aria-selected="false"
                class="-mb-px whitespace-nowrap border-b-2 border-transparent px-4 py-3 text-sm font-medium text-muted transition hover:text-heading aria-[selected=true]:border-brand aria-[selected=true]:font-semibold aria-[selected=true]:text-brand">
          Comments <span class="ml-1 font-mono text-xs text-faint" data-admin-count="comments">0</span></button>
        <button role="tab" type="button" data-tab="students" aria-selected="false"
                class="-mb-px whitespace-nowrap border-b-2 border-transparent px-4 py-3 text-sm font-medium text-muted transition hover:text-heading aria-[selected=true]:border-brand aria-[selected=true]:font-semibold aria-[selected=true]:text-brand">
          Student Approvals <span class="ml-1 rounded-full bg-brand-soft text-brand-text px-1.5 py-0.5 text-xs font-bold font-mono" data-admin-count="students">0</span></button>
      </div>
    </div>

    <div data-admin-panel></div>
  </div>
</div>
"""

HOME_BODY = HOME_BODY.replace("{things}", THINGS)

page("index.html", "Lost &amp; Found — Campus Registry",
     "Post what you lost, hand in what you found, and get matched automatically.",
     HOME_BODY, '<script src="/js/home.js"></script>')

page("browse.html", "Browse the board — Lost &amp; Found",
     "Search and filter every lost and found item posted across campus.",
     BROWSE_BODY, '<script src="/js/browse.js"></script>', active="nav_browse")

page("report.html", "Post an item — Lost &amp; Found",
     "Report something you lost or hand in something you found.",
     REPORT_BODY, '<script src="/assets/vendor/leaflet/leaflet.js"></script><script src="/js/maps.js"></script><script src="/js/report.js"></script>', active="nav_report", map_assets=True)

page("item.html", "Item — Lost &amp; Found",
     "Details for one item on the Lost and Found board.",
     ITEM_BODY, '<script src="/assets/vendor/leaflet/leaflet.js"></script><script src="/js/maps.js"></script><script src="/js/safety.js"></script><script src="/js/item.js"></script>', map_assets=True)

page("claim.html", "Claim — Lost &amp; Found",
     "A conversation about one claimed item.",
     CLAIM_BODY, '<script src="/js/safety.js"></script><script src="/js/claim.js"></script>')

page("dashboard.html", "My items — Lost &amp; Found",
     "Everything you have posted or claimed.",
     DASH_BODY, '<script src="/js/dashboard.js"></script>', active="nav_dash")

page("gallery.html", "Reunions — Lost &amp; Found",
     "Items that made it back to their owners.",
     GALLERY_BODY, '<script src="/js/gallery.js"></script>', active="nav_gallery")

page("map.html", "Campus map — Lost &amp; Found",
     "Where items are lost and found across campus.",
     MAP_BODY, '<script src="/assets/vendor/leaflet/leaflet.js"></script><script src="/js/maps.js"></script><script src="/js/map.js"></script>', active="nav_map", map_assets=True)

page("admin.html", "Moderation — Lost &amp; Found",
     "Moderation tools for the campus Lost and Found board.",
     ADMIN_BODY, '<script src="/js/admin.js"></script>', public_nav=False)

page("desk.html", "Campus desk — Lost &amp; Found",
     "Private evidence review, disputed claims and handover safety.",
     '''<div class="mx-auto max-w-7xl px-4 py-8 sm:px-6 lg:px-8">
       <div class="flex flex-wrap items-center justify-between gap-4"><div><p class="text-sm text-brand-text">STAFF WORKSPACE</p><h1 class="mt-2 text-3xl text-heading">Campus desk &amp; fraud review</h1><p class="mt-2 text-muted">Review ownership evidence, check student ID in person, and resolve disputes before pickup.</p></div><a class="btn btn-secondary" href="/admin.html">Moderation</a></div>
       <p class="mt-4 text-xs text-muted">Check the claimant's student ID in person before clearing a review. Record the outcome, not full ID numbers or ID photos.</p>
       <div class="mt-6" data-desk></div></div>''',
     '<script src="/js/safety.js"></script><script src="/js/desk.js"></script>', public_nav=False)

page("help.html", "Claim &amp; collection guide — Lost &amp; Found",
     "How to verify ownership, arrange collection and report a suspicious claim.",
     '''<div class="mx-auto max-w-4xl px-4 py-8 sm:px-6 lg:px-8">
       <p class="text-sm text-brand-text">GETTING YOUR ITEM BACK</p>
       <h1 class="mt-2 text-3xl text-heading">Claim with confidence. Collect safely.</h1>
       <p class="mt-3 text-body">A little care helps an item reach its rightful owner. Keep identifying details private and confirm a return only after the exchange.</p>
       <div class="mt-6 flex flex-wrap gap-3"><a class="btn btn-primary" href="/browse.html">Find your item</a><a class="btn btn-secondary" href="/dashboard.html">Manage my claims</a></div>
       <div class="mt-8 grid gap-5">
         <section class="card p-6"><h2 class="text-xl text-heading">1. Provide private ownership evidence</h2>
           <p class="mt-3 text-body">Answer the report's security question and describe a detail only the owner would know. An older photo, a redacted receipt, a partial serial number or a concealed marking can help the finder verify your claim.</p>
           <p class="mt-3 text-sm text-muted">Evidence is visible only to the people handling your claim and authorized staff. Image uploads support JPEG, PNG, WebP and GIF up to 5 MB. Never share a device password, full student-ID number or ID photo.</p>
         </section>
         <section class="card p-6"><h2 class="text-xl text-heading">2. Review the claim and arrange pickup</h2>
           <p class="mt-3 text-body">The finder compares the private evidence before approving pickup. Approval means the item is reserved for collection, not that it has already been returned. Use the private chat to agree on a safe meeting place.</p>
           <p class="mt-3 text-sm text-muted">Electronics, jewellery and other items marked as valuable require campus-desk review. Staff check the evidence and the claimant's student ID in person before clearing the review.</p>
         </section>
         <section class="card p-6"><h2 class="text-xl text-heading">3. Confirm the actual handover</h2>
           <p class="mt-3 text-body">After the item changes hands, both the finder and claimant confirm the handover in their claim page. The item is marked returned only when both confirmations are recorded.</p>
         </section>
         <section class="card p-6"><h2 class="text-xl text-heading">Something doesn't add up?</h2>
           <p class="mt-3 text-body">Use “Report suspicious claim” on the claim page and explain your concern. Pickup is paused while staff investigate. Withdrawing a claim does not remove the report or its review history.</p>
           <p class="mt-3 text-sm text-muted">Do not hand over an item while its review is unresolved. Ownership checks reduce risk, but staff and finders should still carefully compare the evidence.</p>
         </section>
       </div>
     </div>''', '')

# Keep previously shared links working without maintaining a second guide.
page("prototype.html", "Claim &amp; collection guide — Lost &amp; Found",
     "How to claim and collect a lost item safely.",
     '<div class="mx-auto max-w-4xl px-4 py-8"><h1 class="text-3xl text-heading">Claim &amp; collection guide</h1><a class="btn btn-primary mt-6" href="/help.html">Read the guide</a></div>',
     '<script>location.replace("/help.html");</script>')
