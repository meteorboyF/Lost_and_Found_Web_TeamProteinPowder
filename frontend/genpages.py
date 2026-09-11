#!/usr/bin/env python3
"""Generate the static pages from one shared chrome template.

Authoring them through a single template is the only way the masthead stays
identical across pages without a server-side templating engine.
"""
import pathlib

STATIC = pathlib.Path("/home/meteorboyf/Lost_and_Found/src/main/resources/static")

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

<link rel="preload" href="/assets/fonts/serif-display-400.woff2" as="font" type="font/woff2" crossorigin>
<link rel="preload" href="/assets/fonts/sans-display-400.woff2" as="font" type="font/woff2" crossorigin>
<link rel="stylesheet" href="/css/app.css">
<link rel="icon" href="/assets/favicon.svg" type="image/svg+xml">
</head>

<body class="min-h-screen flex flex-col">

<a class="skip-link" href="#main">Skip to content</a>

<header class="sticky top-0 z-40 border-b border-line bg-page/90 backdrop-blur-sm">
  <div class="mx-auto flex h-16 max-w-7xl items-center gap-4 px-4 sm:px-6 lg:px-8">

    <a href="/" class="flex items-baseline gap-2 font-display text-lg tracking-tight whitespace-nowrap">
      Lost <span class="italic text-found">&amp;</span> Found
      <span class="hidden border-l border-line-strong pl-2 font-sans u-caps text-muted sm:inline">Registry</span>
    </a>

    <nav id="primary-nav" aria-label="Primary"
         class="fixed inset-x-0 top-16 z-30 flex-col gap-0 border-b border-line bg-page px-4 py-2 shadow-lg
                data-[open=false]:hidden lg:static lg:z-auto lg:flex lg:flex-row lg:gap-1 lg:border-0 lg:bg-transparent
                lg:px-0 lg:py-0 lg:shadow-none"
         data-open="false">
      <a href="/browse.html"{nav_browse}            class="rounded-sm px-3 py-3 text-secondary hover:bg-primary/5 hover:text-primary lg:py-2 aria-[current=page]:font-bold aria-[current=page]:text-primary">Browse</a>
      <a href="/report.html?kind=lost"{nav_lost}    class="rounded-sm px-3 py-3 text-secondary hover:bg-primary/5 hover:text-primary lg:py-2 aria-[current=page]:font-bold aria-[current=page]:text-primary">Report lost</a>
      <a href="/report.html?kind=found"{nav_found}  class="rounded-sm px-3 py-3 text-secondary hover:bg-primary/5 hover:text-primary lg:py-2 aria-[current=page]:font-bold aria-[current=page]:text-primary">Report found</a>
      <a href="/dashboard.html"{nav_dash}           class="rounded-sm px-3 py-3 text-secondary hover:bg-primary/5 hover:text-primary lg:py-2 aria-[current=page]:font-bold aria-[current=page]:text-primary">My items</a>
    </nav>

    <div class="ml-auto flex items-center gap-1">
      <button type="button" data-theme-toggle
              class="grid h-10 w-10 place-items-center rounded-sm text-secondary hover:bg-primary/5 hover:text-primary"
              aria-label="Switch to dark theme" aria-pressed="false">
        <svg class="icon" aria-hidden="true" data-theme-icon="light"><use href="/assets/icons.svg#i-sun"></use></svg>
        <svg class="icon" aria-hidden="true" data-theme-icon="dark" hidden><use href="/assets/icons.svg#i-moon"></use></svg>
      </button>

      <a href="/login.html"
         class="hidden rounded-sm border border-line-strong px-4 py-2 text-sm font-bold hover:border-primary sm:inline-block">
        Sign in
      </a>

      <button type="button" data-nav-toggle aria-controls="primary-nav" aria-expanded="false"
              aria-label="Open navigation"
              class="grid h-10 w-10 place-items-center rounded-sm text-secondary hover:bg-primary/5 hover:text-primary lg:hidden">
        <svg class="icon" aria-hidden="true"><use href="/assets/icons.svg#i-menu"></use></svg>
      </button>
    </div>
  </div>
</header>

<main id="main" class="flex-1">
"""

FOOT = """</main>

<footer class="border-t border-line bg-sunken">
  <div class="mx-auto max-w-7xl px-4 py-10 sm:px-6 lg:px-8">
    <div class="flex flex-wrap items-center justify-between gap-4 text-2xs text-muted">
      <p>Lost &amp; Found Registry — Team Protein Powder</p>
      <p>Items are held for 90 days before being archived.</p>
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
    nav = {k: '' for k in ('nav_browse', 'nav_lost', 'nav_found', 'nav_dash')}
    if active:
        nav[active] = CURRENT
    html = HEAD.format(title=title, description=description, **nav)
    html += body
    html += FOOT.format(scripts=scripts)
    (STATIC / filename).write_text(html, encoding="utf-8")
    print(f"wrote {filename} ({len(html)} bytes)")


# ===================== browse =====================

BROWSE_BODY = """
<div class="mx-auto max-w-7xl px-4 py-10 sm:px-6 lg:px-8">

  <div class="mb-8">
    <p class="u-caps text-muted">The board</p>
    <h1 class="mt-3 text-3xl sm:text-4xl">Browse everything</h1>
    <p class="mt-3 max-w-2xl text-secondary">
      Every item posted across campus. Filter down to the ones worth checking.
    </p>
  </div>

  <div class="lg:grid lg:grid-cols-[16rem_minmax(0,1fr)] lg:gap-10">

    <!-- ---------- filters ---------- -->
    <aside class="mb-8 lg:mb-0">
      <form data-filters class="lg:sticky lg:top-24">
        <div class="mb-5">
          <label for="f-q" class="sr-only">Search items</label>
          <div class="relative flex items-center">
            <svg class="icon pointer-events-none absolute left-3 text-muted" aria-hidden="true">
              <use href="/assets/icons.svg#i-search"></use>
            </svg>
            <input type="search" id="f-q" name="q" placeholder="Search the board"
                   class="w-full rounded-xs border border-line-strong bg-surface py-3 pl-11 pr-3
                          placeholder:text-muted focus:border-found">
          </div>
        </div>

        <fieldset class="mb-5 border-b border-line pb-5">
          <legend class="mb-3 u-caps text-muted">Side of the board</legend>
          <div class="flex flex-wrap gap-2" role="group">
            <button type="button" data-kind="" aria-pressed="true"
                    class="rounded-full border border-line-strong px-3 py-1.5 text-2xs font-bold transition
                           aria-[pressed=true]:border-primary aria-[pressed=true]:bg-primary aria-[pressed=true]:text-surface">All</button>
            <button type="button" data-kind="LOST" aria-pressed="false"
                    class="rounded-full border border-line-strong px-3 py-1.5 text-2xs font-bold transition
                           aria-[pressed=true]:border-primary aria-[pressed=true]:bg-primary aria-[pressed=true]:text-surface">Lost</button>
            <button type="button" data-kind="FOUND" aria-pressed="false"
                    class="rounded-full border border-line-strong px-3 py-1.5 text-2xs font-bold transition
                           aria-[pressed=true]:border-primary aria-[pressed=true]:bg-primary aria-[pressed=true]:text-surface">Found</button>
          </div>
        </fieldset>

        <fieldset class="mb-5 border-b border-line pb-5">
          <legend class="mb-3 u-caps text-muted">Category</legend>
          <div class="grid gap-1" data-categories>
            <p class="text-2xs text-muted">Loading…</p>
          </div>
        </fieldset>

        <fieldset class="mb-5 border-b border-line pb-5">
          <legend class="mb-3 u-caps text-muted">Status</legend>
          <div class="grid gap-1">
            <label class="flex cursor-pointer items-center gap-2.5 py-1 text-sm">
              <input type="radio" name="status" value="" checked class="h-4 w-4 accent-[var(--sc-found)]">Any</label>
            <label class="flex cursor-pointer items-center gap-2.5 py-1 text-sm">
              <input type="radio" name="status" value="OPEN" class="h-4 w-4 accent-[var(--sc-found)]">Open</label>
            <label class="flex cursor-pointer items-center gap-2.5 py-1 text-sm">
              <input type="radio" name="status" value="PENDING" class="h-4 w-4 accent-[var(--sc-found)]">Claim in progress</label>
            <label class="flex cursor-pointer items-center gap-2.5 py-1 text-sm">
              <input type="radio" name="status" value="RESOLVED" class="h-4 w-4 accent-[var(--sc-found)]">Resolved</label>
          </div>
        </fieldset>

        <button type="button" data-clear
                class="text-sm underline underline-offset-4 hover:text-found">Clear all filters</button>
      </form>
    </aside>

    <!-- ---------- results ---------- -->
    <div>
      <div class="mb-6 flex flex-wrap items-center justify-between gap-3 border-b border-line pb-3">
        <p class="text-2xs text-muted" role="status" data-count>Loading the board…</p>
        <div class="flex items-center gap-2">
          <label for="f-sort" class="u-caps text-muted">Sort</label>
          <select id="f-sort" name="sort"
                  class="rounded-xs border border-line-strong bg-surface px-3 py-2 text-sm focus:border-found">
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

# ===================== report =====================

REPORT_BODY = """
<div class="mx-auto max-w-3xl px-4 py-10 sm:px-6 lg:px-8">

  <div class="mb-8">
    <p class="u-caps text-muted" data-eyebrow>Report</p>
    <h1 class="mt-3 text-3xl sm:text-4xl" data-heading>Post an item</h1>
    <p class="mt-3 text-secondary" data-subheading>
      The more specific you are, the better the matching works.
    </p>
  </div>

  <!-- Which side of the board -->
  <fieldset class="mb-8 rounded-sm border border-line bg-surface p-5">
    <legend class="px-2 u-caps text-muted">What happened</legend>
    <div class="grid gap-3 sm:grid-cols-2">
      <label class="flex cursor-pointer items-start gap-3 rounded-sm border border-line p-4 transition
                    hover:bg-primary/5 has-[:checked]:border-lost has-[:checked]:bg-lost-wash has-[:checked]:text-on-wash">
        <input type="radio" name="kind" value="LOST" class="mt-1 h-4 w-4 accent-[var(--sc-lost)]">
        <span>
          <span class="block font-bold">I lost something</span>
          <span class="block text-sm text-muted">We will watch the found side for you.</span>
        </span>
      </label>
      <label class="flex cursor-pointer items-start gap-3 rounded-sm border border-line p-4 transition
                    hover:bg-primary/5 has-[:checked]:border-found has-[:checked]:bg-found-wash has-[:checked]:text-on-wash">
        <input type="radio" name="kind" value="FOUND" class="mt-1 h-4 w-4 accent-[var(--sc-found)]">
        <span>
          <span class="block font-bold">I found something</span>
          <span class="block text-sm text-muted">Post it so the owner can find you.</span>
        </span>
      </label>
    </div>
    <p class="mt-2 min-h-5 text-2xs text-lost" data-error="kind"></p>
  </fieldset>

  <form data-report novalidate class="grid gap-6">

    <div class="grid gap-2">
      <label for="title" class="flex items-baseline justify-between u-caps text-secondary">
        Title <span class="font-mono text-2xs font-normal normal-case tracking-normal text-muted"><span data-count-for="title">0</span>/120</span>
      </label>
      <input id="title" name="title" maxlength="120" placeholder="Black wireless earbuds"
             class="w-full rounded-xs border border-line-strong bg-surface p-3 placeholder:text-muted focus:border-found
                    aria-[invalid=true]:border-lost aria-[invalid=true]:border-2"
             aria-describedby="title-error">
      <p class="min-h-5 text-2xs text-lost" id="title-error" data-error="title"></p>
    </div>

    <div class="grid gap-4 sm:grid-cols-2">
      <div class="grid gap-2">
        <label for="category" class="u-caps text-secondary">Category</label>
        <select id="category" name="category" aria-describedby="category-error"
                class="w-full rounded-xs border border-line-strong bg-surface p-3 focus:border-found
                       aria-[invalid=true]:border-lost aria-[invalid=true]:border-2">
          <option value="">Choose one…</option>
        </select>
        <p class="min-h-5 text-2xs text-lost" id="category-error" data-error="category"></p>
      </div>

      <div class="grid gap-2">
        <label for="colour" class="u-caps text-secondary">Colour <span class="font-normal normal-case tracking-normal text-muted">optional</span></label>
        <input id="colour" name="colour" maxlength="40" placeholder="Black"
               class="w-full rounded-xs border border-line-strong bg-surface p-3 placeholder:text-muted focus:border-found">
        <p class="min-h-5 text-2xs text-lost" data-error="colour"></p>
      </div>
    </div>

    <div class="grid gap-2">
      <label for="description" class="u-caps text-secondary">Description</label>
      <textarea id="description" name="description" rows="4" maxlength="2000"
                placeholder="Anything that would help someone recognise it — brand, marks, what was inside."
                aria-describedby="description-error"
                class="w-full resize-y rounded-xs border border-line-strong bg-surface p-3 placeholder:text-muted
                       focus:border-found aria-[invalid=true]:border-lost aria-[invalid=true]:border-2"></textarea>
      <p class="min-h-5 text-2xs text-lost" id="description-error" data-error="description"></p>
    </div>

    <div class="grid gap-4 sm:grid-cols-2">
      <div class="grid gap-2">
        <label for="location" class="u-caps text-secondary">Where</label>
        <input id="location" name="location" maxlength="160" placeholder="Central Library, level 2"
               aria-describedby="location-error"
               class="w-full rounded-xs border border-line-strong bg-surface p-3 placeholder:text-muted
                      focus:border-found aria-[invalid=true]:border-lost aria-[invalid=true]:border-2">
        <p class="min-h-5 text-2xs text-lost" id="location-error" data-error="location"></p>
      </div>

      <div class="grid gap-2">
        <label for="happenedOn" class="u-caps text-secondary">When <span class="font-normal normal-case tracking-normal text-muted">optional</span></label>
        <input id="happenedOn" name="happenedOn" type="date" aria-describedby="happenedOn-error"
               class="w-full rounded-xs border border-line-strong bg-surface p-3 focus:border-found
                      aria-[invalid=true]:border-lost aria-[invalid=true]:border-2">
        <p class="min-h-5 text-2xs text-lost" id="happenedOn-error" data-error="happenedOn"></p>
      </div>
    </div>

    <!-- photo -->
    <div class="grid gap-2">
      <span class="u-caps text-secondary">Photograph <span class="font-normal normal-case tracking-normal text-muted">optional</span></span>
      <label class="grid cursor-pointer justify-items-center gap-2 rounded-sm border-2 border-dashed border-line-strong
                    bg-sunken px-6 py-10 text-center transition hover:border-found hover:bg-found-wash hover:text-on-wash
                    has-[:focus-visible]:border-found" data-dropzone>
        <svg class="icon h-8 w-8 text-muted" aria-hidden="true"><use href="/assets/icons.svg#i-upload"></use></svg>
        <span class="font-bold">Add a photo</span>
        <span class="text-2xs text-muted">JPEG, PNG, WebP, or GIF · up to 5 MB</span>
        <input type="file" name="photo" accept="image/jpeg,image/png,image/webp,image/gif" class="sr-only">
      </label>
      <div class="hidden" data-preview>
        <div class="flex items-center gap-4 rounded-sm border border-line bg-surface p-3">
          <img alt="" class="h-20 w-20 rounded-xs object-cover" data-preview-img>
          <div class="min-w-0 flex-1">
            <p class="truncate text-sm font-bold" data-preview-name></p>
            <p class="font-mono text-2xs text-muted" data-preview-size></p>
          </div>
          <button type="button" data-preview-remove
                  class="rounded-xs border border-line-strong px-3 py-1.5 text-2xs font-bold hover:border-lost hover:text-lost">
            Remove</button>
        </div>
      </div>
      <p class="min-h-5 text-2xs text-lost" data-error="photo"></p>
    </div>

    <!-- contact -->
    <fieldset class="grid gap-4 rounded-sm border border-line bg-surface p-5 sm:grid-cols-2">
      <legend class="px-2 u-caps text-muted">How we reach you</legend>
      <div class="grid gap-2">
        <label for="reporterName" class="u-caps text-secondary">Your name</label>
        <input id="reporterName" name="reporterName" maxlength="80" placeholder="Fardin Jahangir"
               aria-describedby="reporterName-error"
               class="w-full rounded-xs border border-line-strong bg-surface p-3 placeholder:text-muted
                      focus:border-found aria-[invalid=true]:border-lost aria-[invalid=true]:border-2">
        <p class="min-h-5 text-2xs text-lost" id="reporterName-error" data-error="reporterName"></p>
      </div>
      <div class="grid gap-2">
        <label for="reporterEmail" class="u-caps text-secondary">Email</label>
        <input id="reporterEmail" name="reporterEmail" type="email" maxlength="160"
               placeholder="you@university.edu" aria-describedby="reporterEmail-error"
               class="w-full rounded-xs border border-line-strong bg-surface p-3 placeholder:text-muted
                      focus:border-found aria-[invalid=true]:border-lost aria-[invalid=true]:border-2">
        <p class="min-h-5 text-2xs text-lost" id="reporterEmail-error" data-error="reporterEmail"></p>
      </div>
      <p class="text-2xs text-muted sm:col-span-2">
        Your email is never shown on the board. It is only used to notify you about matches and claims.
      </p>
    </fieldset>

    <div class="flex flex-wrap items-center gap-3 border-t border-line pt-6">
      <button type="submit" data-submit
              class="inline-flex items-center gap-2 rounded-sm bg-primary px-6 py-3.5 font-bold text-surface
                     transition hover:bg-found hover:text-white disabled:opacity-50">
        Post to the board
      </button>
      <a href="/browse.html" class="rounded-sm px-4 py-3.5 font-bold text-secondary hover:text-primary">Cancel</a>
    </div>
  </form>

  <!-- success panel, swapped in after a successful post -->
  <div class="hidden" data-success></div>
</div>
"""

# ===================== item detail =====================

ITEM_BODY = """
<div class="mx-auto max-w-5xl px-4 py-10 sm:px-6 lg:px-8">
  <nav aria-label="Breadcrumb" class="mb-6 flex flex-wrap items-center gap-2 text-2xs text-muted">
    <a href="/" class="hover:text-primary">Home</a>
    <span aria-hidden="true">/</span>
    <a href="/browse.html" class="hover:text-primary">Browse</a>
    <span aria-hidden="true">/</span>
    <span data-crumb class="font-mono">…</span>
  </nav>

  <div data-item></div>
</div>
"""

page("browse.html",
     "Browse the board — Lost &amp; Found",
     "Search and filter every lost and found item posted across campus.",
     BROWSE_BODY, '<script src="/js/browse.js"></script>', active="nav_browse")

page("report.html",
     "Post an item — Lost &amp; Found",
     "Report something you lost or hand in something you found.",
     REPORT_BODY, '<script src="/js/report.js"></script>')

page("item.html",
     "Item — Lost &amp; Found",
     "Details for one item on the Lost and Found board.",
     ITEM_BODY, '<script src="/js/item.js"></script>')
