/**
 * ui.js — shared rendering: status pills, category pills, item cards, and the
 * four list states. Browse, the landing page, and the dashboard all draw from
 * here so they cannot drift into three different item cards.
 */
(function () {
  'use strict';

  var LF = window.LF;
  var e = LF.escapeHtml;

  /* =====================================================================
     Category styling. Each category gets its own hue and glyph, which is
     what makes a wall of cards scannable — you spot "keys" by colour before
     you have read a single word.
     ===================================================================== */

  var CATEGORY = {
    ELECTRONICS: { icon: 'i-device', tint: 'text-violet-600 bg-violet-50 dark:text-violet-300 dark:bg-violet-950/60', grad: 'from-violet-400/25 to-indigo-400/25 dark:from-violet-500/12 dark:to-indigo-500/12', solid: 'text-violet-500' },
    ID_CARDS:    { icon: 'i-id-card', tint: 'text-sky-600 bg-sky-50 dark:text-sky-300 dark:bg-sky-950/60',          grad: 'from-sky-400/25 to-cyan-400/25 dark:from-sky-500/12 dark:to-cyan-500/12',      solid: 'text-sky-500' },
    KEYS:        { icon: 'i-key',     tint: 'text-amber-600 bg-amber-50 dark:text-amber-300 dark:bg-amber-950/60',  grad: 'from-amber-400/25 to-orange-400/25 dark:from-amber-500/12 dark:to-orange-500/12',  solid: 'text-amber-500' },
    BAGS:        { icon: 'i-box',     tint: 'text-teal-600 bg-teal-50 dark:text-teal-300 dark:bg-teal-950/60',      grad: 'from-teal-400/25 to-emerald-400/25 dark:from-teal-500/12 dark:to-emerald-500/12',  solid: 'text-teal-500' },
    CLOTHING:    { icon: 'i-shirt',   tint: 'text-rose-600 bg-rose-50 dark:text-rose-300 dark:bg-rose-950/60',      grad: 'from-rose-400/25 to-pink-400/25 dark:from-rose-500/12 dark:to-pink-500/12',     solid: 'text-rose-500' },
    BOOKS:       { icon: 'i-book',    tint: 'text-lime-700 bg-lime-50 dark:text-lime-300 dark:bg-lime-950/60',      grad: 'from-lime-400/25 to-green-400/25 dark:from-lime-500/12 dark:to-green-500/12',    solid: 'text-lime-600' },
    JEWELLERY:   { icon: 'i-gem',     tint: 'text-fuchsia-600 bg-fuchsia-50 dark:text-fuchsia-300 dark:bg-fuchsia-950/60', grad: 'from-fuchsia-400/25 to-purple-400/25 dark:from-fuchsia-500/12 dark:to-purple-500/12', solid: 'text-fuchsia-500' },
    OTHER:       { icon: 'i-tag',     tint: 'text-slate-600 bg-slate-100 dark:text-slate-300 dark:bg-slate-800',    grad: 'from-slate-400/25 to-gray-400/25 dark:from-slate-500/12 dark:to-gray-500/12',    solid: 'text-slate-500' }
  };

  LF.category = function (key) {
    return CATEGORY[key] || CATEGORY.OTHER;
  };

  LF.categoryPill = function (item) {
    var c = LF.category(item.category);
    return (
      '<span class="pill ' + c.tint + '">' +
      '<svg class="icon h-3.5 w-3.5" aria-hidden="true"><use href="/assets/icons.svg#' + c.icon + '"></use></svg>' +
      e(item.categoryLabel || '') + '</span>'
    );
  };

  /* =====================================================================
     Status pill. Colour is never the only signal — each state also carries
     its own glyph and its literal label.
     ===================================================================== */

  var STATUS = {
    OPEN:     { label: 'Open',      icon: 'i-st-reported', cls: 'bg-slate-100 text-slate-700 dark:bg-slate-800 dark:text-slate-300' },
    PENDING:  { label: 'In progress', icon: 'i-st-pending', cls: 'bg-amber-soft text-amber-text' },
    RESOLVED: { label: 'Resolved',  icon: 'i-check',       cls: 'bg-found-soft text-found-text' }
  };

  LF.statusPill = function (status, big) {
    var s = STATUS[status] || STATUS.OPEN;
    return (
      '<span class="pill ' + (big ? 'pill-lg ' : '') + s.cls + '">' +
      '<svg class="icon h-3.5 w-3.5" aria-hidden="true"><use href="/assets/icons.svg#' + s.icon + '"></use></svg>' +
      e(s.label) + '</span>'
    );
  };

  /** LOST / FOUND marker — the single most important thing on a card. */
  LF.kindPill = function (kind, big) {
    var lost = kind === 'LOST';
    return (
      '<span class="pill ' + (big ? 'pill-lg ' : '') +
      (lost ? 'bg-lost text-white' : 'bg-found text-white') +
      /* Dark mode lightens the accents, so white-on-accent collapses to ~2:1.
         Dark ink on the same chip measures 6.9:1 (lost) and 9.7:1 (found). */
      ' dark:text-[#11121a]' +
      ' shadow-sm">' +
      '<svg class="icon h-3.5 w-3.5" aria-hidden="true"><use href="/assets/icons.svg#' +
      (lost ? 'i-search' : 'i-hand') + '"></use></svg>' +
      (lost ? 'Lost' : 'Found') + '</span>'
    );
  };

  /* =====================================================================
     Item card
     ===================================================================== */

  /**
   * Cards without a photograph get a tinted gradient panel carrying the
   * category glyph, rather than an empty grey box. A missing photo should
   * still look deliberate.
   */
  function media(item) {
    var c = LF.category(item.category);

    if (item.photoUrl) {
      return '<img src="' + e(item.photoUrl) + '" alt="" loading="lazy" ' +
        'class="h-full w-full object-cover transition duration-500 group-hover:scale-105">';
    }

    return (
      '<div class="grid h-full w-full place-items-center bg-gradient-to-br ' + c.grad + '">' +
      '<svg class="icon h-12 w-12 ' + c.solid + ' opacity-70" aria-hidden="true">' +
      '<use href="/assets/icons.svg#' + c.icon + '"></use></svg></div>'
    );
  }

  LF.itemCard = function (item) {
    var href = '/item.html?ref=' + encodeURIComponent(item.reference);

    return (
      '<article class="card card-interactive group relative flex flex-col overflow-hidden">' +
        '<div class="relative aspect-[4/3] overflow-hidden bg-sunken">' +
          media(item) +
          '<div class="absolute left-3 top-3">' + LF.kindPill(item.kind) + '</div>' +
          (item.status !== 'OPEN'
            ? '<div class="absolute right-3 top-3">' + LF.statusPill(item.status) + '</div>'
            : '') +
        '</div>' +

        '<div class="flex flex-1 flex-col gap-2.5 p-4">' +
          '<div class="flex items-start justify-between gap-2">' +
            LF.categoryPill(item) +
          '</div>' +

          '<h3 class="text-base font-semibold leading-snug text-heading">' +
            '<a href="' + href + '" class="after:absolute after:inset-0 group-hover:text-brand transition-colors">' +
              e(item.title) + '</a>' +
          '</h3>' +

          '<p class="line-clamp-2 text-sm leading-relaxed text-muted">' + e(item.description) + '</p>' +

          '<div class="mt-auto flex items-center gap-1.5 pt-1 text-xs text-faint">' +
            '<svg class="icon h-3.5 w-3.5" aria-hidden="true"><use href="/assets/icons.svg#i-pin"></use></svg>' +
            '<span class="truncate">' + e(item.location) + '</span>' +
          '</div>' +
        '</div>' +

        '<div class="flex items-center justify-between gap-3 border-t border-line px-4 py-2.5">' +
          '<span class="font-mono text-xs text-faint">' + e(item.reference) + '</span>' +
          '<span class="text-xs text-muted">' + e(LF.timeAgo(item.createdAt)) + '</span>' +
        '</div>' +
      '</article>'
    );
  };

  LF.itemGrid = function (items) {
    return (
      '<div class="grid gap-5 sm:grid-cols-2 xl:grid-cols-3">' +
      items.map(LF.itemCard).join('') +
      '</div>'
    );
  };

  /* =====================================================================
     States
     ===================================================================== */

  LF.skeletonGrid = function (count) {
    var cell =
      '<div class="card overflow-hidden">' +
      '<div class="aspect-[4/3] animate-pulse bg-sunken"></div>' +
      '<div class="space-y-3 p-4">' +
      '<div class="h-5 w-24 animate-pulse rounded-full bg-sunken"></div>' +
      '<div class="h-4 w-3/4 animate-pulse rounded bg-sunken"></div>' +
      '<div class="h-3 w-full animate-pulse rounded bg-sunken"></div>' +
      '<div class="h-3 w-1/2 animate-pulse rounded bg-sunken"></div>' +
      '</div></div>';
    return '<div class="grid gap-5 sm:grid-cols-2 xl:grid-cols-3">' +
      new Array(count || 6).fill(cell).join('') + '</div>';
  };

  LF.emptyState = function (opts) {
    opts = opts || {};
    return (
      '<div class="flex flex-col items-center gap-3 rounded-2xl border border-dashed border-line-strong bg-surface px-6 py-16 text-center">' +
      '<div class="grid h-14 w-14 place-items-center rounded-2xl bg-brand-soft text-brand">' +
      '<svg class="icon h-7 w-7" aria-hidden="true"><use href="/assets/icons.svg#' +
      (opts.icon || 'i-inbox') + '"></use></svg></div>' +
      '<p class="mt-1 text-lg font-semibold text-heading">' + e(opts.title || 'Nothing here yet') + '</p>' +
      '<p class="max-w-sm text-sm text-muted">' + e(opts.message || '') + '</p>' +
      (opts.actionHref
        ? '<a href="' + e(opts.actionHref) + '" class="btn btn-primary mt-3">' + e(opts.actionLabel || 'Continue') + '</a>'
        : '') +
      (opts.actionAttr
        ? '<button type="button" ' + opts.actionAttr + ' class="btn btn-secondary mt-3">' +
          e(opts.actionLabel || 'Continue') + '</button>'
        : '') +
      '</div>'
    );
  };

  LF.errorState = function (message, retryAttr) {
    return (
      '<div role="alert" class="flex flex-col items-center gap-3 rounded-2xl border border-lost/30 bg-lost-soft px-6 py-16 text-center">' +
      '<div class="grid h-14 w-14 place-items-center rounded-2xl bg-lost/10 text-lost">' +
      '<svg class="icon h-7 w-7" aria-hidden="true"><use href="/assets/icons.svg#i-alert"></use></svg></div>' +
      '<p class="mt-1 text-lg font-semibold text-lost-text">Something went wrong</p>' +
      '<p class="max-w-md text-sm text-lost-text/80">' + e(message) + '</p>' +
      '<button type="button" ' + (retryAttr || 'data-retry') + ' class="btn btn-secondary mt-3">Try again</button>' +
      '</div>'
    );
  };

  /* =====================================================================
     Pagination
     ===================================================================== */

  LF.pager = function (page) {
    if (!page || page.totalPages <= 1) return '';

    var current = page.page;
    var total = page.totalPages;

    function btn(n, label, disabled, isCurrent) {
      if (disabled) {
        return '<span class="grid h-10 min-w-10 place-items-center px-2 text-sm text-faint">' + label + '</span>';
      }
      return '<button type="button" data-page="' + n + '" ' +
        (isCurrent ? 'aria-current="page" ' : '') +
        'class="grid h-10 min-w-10 place-items-center rounded-lg px-3 text-sm font-semibold transition ' +
        (isCurrent
          ? 'bg-brand text-white shadow-sm'
          : 'text-body hover:bg-sunken hover:text-heading') +
        '">' + label + '</button>';
    }

    /* Sliding window, so forty pages do not render forty buttons. */
    var parts = [btn(current - 1, '‹', page.first, false)];
    var from = Math.max(0, Math.min(current - 2, total - 5));
    var to = Math.min(total, from + 5);
    if (from > 0) parts.push(btn(0, '1', false, false), '<span class="px-1 text-faint">…</span>');
    for (var i = from; i < to; i++) parts.push(btn(i, String(i + 1), false, i === current));
    if (to < total) {
      parts.push('<span class="px-1 text-faint">…</span>', btn(total - 1, String(total), false, false));
    }
    parts.push(btn(current + 1, '›', page.last, false));

    return '<nav class="flex flex-wrap items-center justify-center gap-1 py-10" aria-label="Result pages">' +
      parts.join('') + '</nav>';
  };
})();
