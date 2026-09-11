/**
 * ui.js — shared rendering used by more than one page: the masthead, status
 * chips, item cards, and the four list states.
 *
 * Keeping these here means browse, the landing page, and the dashboard cannot
 * drift into three slightly different item cards.
 */
(function () {
  'use strict';

  var LF = window.LF;
  var e = LF.escapeHtml;

  /* =====================================================================
     Status chip.
     Colour is never the only signal: each status also carries its own glyph
     and its literal label, so the three states stay distinguishable in
     greyscale and for a colour-blind reader.
     ===================================================================== */

  var STATUS = {
    OPEN: {
      label: 'Open',
      icon: 'i-st-reported',
      cls: 'border-line-strong text-muted'
    },
    PENDING: {
      label: 'Claim in progress',
      icon: 'i-st-pending',
      cls: 'border-found bg-found-wash text-on-wash'
    },
    RESOLVED: {
      label: 'Resolved',
      icon: 'i-st-returned',
      cls: 'border-primary bg-primary text-surface'
    }
  };

  LF.statusChip = function (status, size) {
    var s = STATUS[status] || STATUS.OPEN;
    var pad = size === 'lg' ? 'px-3 py-1.5 text-sm' : 'px-2 py-1 text-2xs';
    return (
      '<span class="inline-flex items-center gap-1.5 rounded-xs border font-bold tracking-wide ' +
      pad + ' ' + s.cls + '">' +
      '<svg class="icon h-3.5 w-3.5" aria-hidden="true"><use href="/assets/icons.svg#' + s.icon + '"></use></svg>' +
      e(s.label) + '</span>'
    );
  };

  /** LOST / FOUND marker. A different silhouette from a status chip on purpose. */
  LF.kindTag = function (kind) {
    var lost = kind === 'LOST';
    return (
      '<span class="inline-flex items-center border-l-[3px] px-2 text-2xs font-bold uppercase tracking-[0.09em] ' +
      (lost ? 'border-lost text-lost' : 'border-found text-found') + '">' +
      (lost ? 'Lost' : 'Found') + '</span>'
    );
  };

  /* =====================================================================
     Item card
     ===================================================================== */

  LF.itemCard = function (item) {
    var href = '/item.html?ref=' + encodeURIComponent(item.reference);

    var media = item.photoUrl
      ? '<img src="' + e(item.photoUrl) + '" alt="" loading="lazy" ' +
        'class="h-full w-full object-cover transition duration-500 group-hover:scale-[1.03]">'
      : '<div class="grid h-full w-full place-items-center bg-sunken text-muted">' +
        '<svg class="icon h-8 w-8" aria-hidden="true"><use href="/assets/icons.svg#i-image"></use></svg></div>';

    return (
      '<article class="group relative flex flex-col overflow-hidden rounded-sm border border-line bg-surface transition hover:border-muted">' +
        '<div class="relative aspect-[4/3] overflow-hidden bg-sunken">' +
          media +
          '<div class="absolute inset-x-2 top-2 flex items-start justify-between gap-2">' +
            '<span class="rounded-xs bg-page/85 backdrop-blur-sm">' + LF.kindTag(item.kind) + '</span>' +
          '</div>' +
        '</div>' +
        '<div class="flex flex-1 flex-col gap-2 p-4">' +
          '<h3 class="font-display text-xl leading-snug">' +
            '<a href="' + href + '" class="after:absolute after:inset-0 hover:underline hover:underline-offset-2">' +
              e(item.title) + '</a>' +
          '</h3>' +
          '<p class="flex flex-wrap items-center gap-x-2 gap-y-1 text-2xs text-muted">' +
            '<span>' + e(item.categoryLabel || '') + '</span><span aria-hidden="true">·</span>' +
            '<span>' + e(item.location) + '</span>' +
          '</p>' +
          '<p class="line-clamp-2 text-sm text-secondary">' + e(item.description) + '</p>' +
        '</div>' +
        '<div class="flex items-center justify-between gap-3 border-t border-line px-4 py-3">' +
          LF.statusChip(item.status) +
          '<span class="font-mono text-2xs text-muted">' + e(LF.timeAgo(item.createdAt)) + '</span>' +
        '</div>' +
      '</article>'
    );
  };

  LF.itemGrid = function (items) {
    return (
      '<div class="grid gap-5 sm:grid-cols-2 lg:grid-cols-3">' +
      items.map(LF.itemCard).join('') +
      '</div>'
    );
  };

  /* =====================================================================
     The four list states
     ===================================================================== */

  LF.skeletonGrid = function (count) {
    var cell =
      '<div class="overflow-hidden rounded-sm border border-line bg-surface">' +
      '<div class="aspect-[4/3] animate-pulse bg-sunken"></div>' +
      '<div class="space-y-3 p-4">' +
      '<div class="h-5 w-3/4 animate-pulse rounded-xs bg-sunken"></div>' +
      '<div class="h-3 w-1/2 animate-pulse rounded-xs bg-sunken"></div>' +
      '<div class="h-3 w-full animate-pulse rounded-xs bg-sunken"></div>' +
      '</div></div>';
    return '<div class="grid gap-5 sm:grid-cols-2 lg:grid-cols-3">' +
      new Array(count || 6).fill(cell).join('') + '</div>';
  };

  LF.emptyState = function (opts) {
    opts = opts || {};
    return (
      '<div class="grid justify-items-center gap-3 rounded-sm border border-dashed border-line-strong px-6 py-16 text-center">' +
      '<svg class="icon h-10 w-10 text-faint" aria-hidden="true"><use href="/assets/icons.svg#' +
      (opts.icon || 'i-inbox') + '"></use></svg>' +
      '<p class="font-display text-xl">' + e(opts.title || 'Nothing here yet') + '</p>' +
      '<p class="max-w-sm text-sm text-muted">' + e(opts.message || '') + '</p>' +
      (opts.actionHref
        ? '<a href="' + e(opts.actionHref) + '" class="mt-2 rounded-sm bg-primary px-5 py-2.5 text-sm font-bold text-surface hover:bg-found hover:text-white">' +
          e(opts.actionLabel || 'Continue') + '</a>'
        : '') +
      (opts.actionAttr
        ? '<button type="button" ' + opts.actionAttr +
          ' class="mt-2 rounded-sm border border-line-strong px-5 py-2.5 text-sm font-bold hover:border-primary">' +
          e(opts.actionLabel || 'Continue') + '</button>'
        : '') +
      '</div>'
    );
  };

  LF.errorState = function (message, retryAttr) {
    return (
      '<div role="alert" class="grid justify-items-center gap-3 rounded-sm border border-lost bg-lost-wash px-6 py-16 text-center text-on-wash">' +
      '<svg class="icon h-10 w-10 text-lost" aria-hidden="true"><use href="/assets/icons.svg#i-alert"></use></svg>' +
      '<p class="font-display text-xl">Something went wrong</p>' +
      '<p class="max-w-md text-sm opacity-85">' + e(message) + '</p>' +
      '<button type="button" ' + (retryAttr || 'data-retry') +
      ' class="mt-2 rounded-sm border border-line-strong px-5 py-2.5 text-sm font-bold hover:border-primary">Try again</button>' +
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
    var btn = function (n, label, disabled, isCurrent) {
      if (disabled) {
        return '<span class="grid h-10 min-w-10 place-items-center px-2 font-mono text-2xs text-faint">' +
          label + '</span>';
      }
      return '<button type="button" data-page="' + n + '" ' +
        (isCurrent ? 'aria-current="page" ' : '') +
        'class="grid h-10 min-w-10 place-items-center rounded-sm border px-2 font-mono text-2xs transition ' +
        (isCurrent
          ? 'border-primary font-bold text-primary'
          : 'border-transparent text-secondary hover:bg-primary/5 hover:text-primary') +
        '">' + label + '</button>';
    };

    /* A sliding window, so 40 pages do not render 40 buttons. */
    var parts = [btn(current - 1, '‹', page.first, false)];
    var from = Math.max(0, Math.min(current - 2, total - 5));
    var to = Math.min(total, from + 5);
    if (from > 0) parts.push(btn(0, '1', false, false), '<span class="px-1 text-faint">…</span>');
    for (var i = from; i < to; i++) parts.push(btn(i, String(i + 1), false, i === current));
    if (to < total) {
      parts.push('<span class="px-1 text-faint">…</span>', btn(total - 1, String(total), false, false));
    }
    parts.push(btn(current + 1, '›', page.last, false));

    return '<nav class="flex flex-wrap items-center justify-center gap-1 py-8" aria-label="Result pages">' +
      parts.join('') + '</nav>';
  };
})();
