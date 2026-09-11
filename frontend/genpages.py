#!/usr/bin/env python3
"""Generate the static pages from one shared chrome template.

Authoring them through a single template is the only way the masthead and
footer stay identical across pages without a server-side templating engine.

    python3 frontend/genpages.py
"""
import pathlib

STATIC = pathlib.Path(__file__).resolve().parent.parent / "src/main/resources/static"

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

    <nav id="primary-nav" aria-label="Primary"
         class="fixed inset-x-0 top-16 z-30 flex-col gap-1 border-b border-line bg-page p-4 shadow-lg
                data-[open=false]:hidden lg:static lg:ml-4 lg:flex lg:flex-row lg:border-0 lg:bg-transparent
                lg:p-0 lg:shadow-none"
         data-open="false">
      <a href="/browse.html"{nav_browse} class="rounded-lg px-3 py-2.5 text-sm font-medium text-body transition hover:bg-sunken hover:text-heading lg:py-2 aria-[current=page]:bg-brand-soft aria-[current=page]:text-brand-text aria-[current=page]:font-semibold">Browse</a>
      <a href="/report.html?kind=lost"{nav_lost} class="rounded-lg px-3 py-2.5 text-sm font-medium text-body transition hover:bg-sunken hover:text-heading lg:py-2 aria-[current=page]:bg-brand-soft aria-[current=page]:text-brand-text aria-[current=page]:font-semibold">Report lost</a>
      <a href="/report.html?kind=found"{nav_found} class="rounded-lg px-3 py-2.5 text-sm font-medium text-body transition hover:bg-sunken hover:text-heading lg:py-2 aria-[current=page]:bg-brand-soft aria-[current=page]:text-brand-text aria-[current=page]:font-semibold">Report found</a>
      <a href="/dashboard.html"{nav_dash} class="rounded-lg px-3 py-2.5 text-sm font-medium text-body transition hover:bg-sunken hover:text-heading lg:py-2 aria-[current=page]:bg-brand-soft aria-[current=page]:text-brand-text aria-[current=page]:font-semibold">My items</a>
    </nav>

    <div class="ml-auto flex items-center gap-2">
      <button type="button" data-theme-toggle
              class="grid h-9 w-9 place-items-center rounded-lg text-muted transition hover:bg-sunken hover:text-heading"
              aria-label="Switch to dark theme" aria-pressed="false">
        <svg class="icon h-[1.15rem] w-[1.15rem]" aria-hidden="true" data-theme-icon="light"><use href="/assets/icons.svg#i-sun"></use></svg>
        <svg class="icon h-[1.15rem] w-[1.15rem]" aria-hidden="true" data-theme-icon="dark" hidden><use href="/assets/icons.svg#i-moon"></use></svg>
      </button>

      <a href="/report.html" class="btn btn-primary btn-sm hidden sm:inline-flex">
        <svg class="icon h-4 w-4" aria-hidden="true"><use href="/assets/icons.svg#i-plus"></use></svg>
        Post an item
      </a>

      <button type="button" data-nav-toggle aria-controls="primary-nav" aria-expanded="false"
              aria-label="Open navigation"
              class="grid h-9 w-9 place-items-center rounded-lg text-muted transition hover:bg-sunken hover:text-heading lg:hidden">
        <svg class="icon h-[1.15rem] w-[1.15rem]" aria-hidden="true"><use href="/assets/icons.svg#i-menu"></use></svg>
      </button>
    </div>
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
        <a href="/browse.html" class="transition hover:text-heading">Browse</a>
        <a href="/report.html" class="transition hover:text-heading">Post an item</a>
        <a href="/dashboard.html" class="transition hover:text-heading">My items</a>
      </nav>
      <p class="text-xs text-faint">Items are held for 90 days before archiving.</p>
    </div>
  </div>
</footer>

<div class="pointer-events-none fixed bottom-4 right-4 z-50 flex w-[min(24rem,calc(100vw-2rem))] flex-col-reverse gap-2"
     data-toast-region role="status" aria-live="polite"></div>

<script src="/js/app.js"></script>
<script src="/js/ui.js"></script>
{scripts}
</body>
</html>
"""

CURRENT = ' aria-current="page"'


def page(filename, title, description, body, scripts, active=None):
    nav = {k: "" for k in ("nav_browse", "nav_lost", "nav_found", "nav_dash")}
    if active:
        nav[active] = CURRENT
    html = HEAD.format(title=title, description=description, **nav) + body + FOOT.format(scripts=scripts)
    (STATIC / filename).write_text(html, encoding="utf-8")
    print(f"wrote {filename} ({len(html):,} bytes)")


# ============================== landing ==============================

HOME_BODY = """
<!-- hero -->
<section class="hero-wash relative overflow-hidden border-b border-line">
  <div class="mx-auto max-w-7xl px-4 pb-16 pt-16 sm:px-6 lg:px-8 lg:pb-24 lg:pt-24">
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

      <!-- Preview cards. Real recent items, angled into a small stack so the
           hero shows the product rather than describing it. -->
      <div class="relative hidden lg:block" data-hero-cards>
        <div class="h-[26rem]"></div>
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

    <aside class="mb-8 lg:mb-0">
      <form data-filters class="lg:sticky lg:top-24">

        <div class="relative mb-5 flex items-center">
          <label for="f-q" class="sr-only">Search items</label>
          <svg class="icon pointer-events-none absolute left-3.5 h-[1.15rem] w-[1.15rem] text-faint" aria-hidden="true">
            <use href="/assets/icons.svg#i-search"></use>
          </svg>
          <input type="search" id="f-q" placeholder="Search the board" class="field pl-11">
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
      <span class="label">Photograph <span class="font-normal text-faint">optional, but it helps a lot</span></span>
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
    </div>

    <div class="grid gap-5 p-5 sm:grid-cols-2 sm:p-6">
      <div class="sm:col-span-2">
        <span class="label">How we reach you</span>
        <p class="mt-1 text-sm text-muted">
          Your email is never shown on the board — only used for matches and claims.
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
    <a href="/" class="transition hover:text-heading">Home</a>
    <svg class="icon h-3.5 w-3.5 text-faint" aria-hidden="true"><use href="/assets/icons.svg#i-chevron-right"></use></svg>
    <a href="/browse.html" class="transition hover:text-heading">Browse</a>
    <svg class="icon h-3.5 w-3.5 text-faint" aria-hidden="true"><use href="/assets/icons.svg#i-chevron-right"></use></svg>
    <span data-crumb class="font-mono text-xs">…</span>
  </nav>

  <div data-item></div>
</div>
"""

page("index.html", "Lost &amp; Found — Campus Registry",
     "Post what you lost, hand in what you found, and get matched automatically.",
     HOME_BODY, '<script src="/js/home.js"></script>')

page("browse.html", "Browse the board — Lost &amp; Found",
     "Search and filter every lost and found item posted across campus.",
     BROWSE_BODY, '<script src="/js/browse.js"></script>', active="nav_browse")

page("report.html", "Post an item — Lost &amp; Found",
     "Report something you lost or hand in something you found.",
     REPORT_BODY, '<script src="/js/report.js"></script>')

page("item.html", "Item — Lost &amp; Found",
     "Details for one item on the Lost and Found board.",
     ITEM_BODY, '<script src="/js/item.js"></script>')
