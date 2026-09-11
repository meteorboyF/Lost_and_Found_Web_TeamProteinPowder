/**
 * item.js — one item in detail.
 */
(function () {
  'use strict';

  var LF = window.LF;
  var e = LF.escapeHtml;
  var host = document.querySelector('[data-item]');

  function skeleton() {
    return (
      '<div class="grid gap-8 lg:grid-cols-2">' +
      '<div class="aspect-[4/3] animate-pulse rounded-2xl bg-sunken"></div>' +
      '<div class="space-y-4 py-2">' +
      '<div class="h-6 w-28 animate-pulse rounded-full bg-sunken"></div>' +
      '<div class="h-9 w-3/4 animate-pulse rounded-lg bg-sunken"></div>' +
      '<div class="h-4 w-full animate-pulse rounded bg-sunken"></div>' +
      '<div class="h-4 w-5/6 animate-pulse rounded bg-sunken"></div>' +
      '</div></div>'
    );
  }

  function media(item) {
    var cat = LF.category(item.category);
    if (item.photoUrl) {
      return '<img src="' + e(item.photoUrl) + '" alt="Photograph of ' + e(item.title) + '" ' +
        'class="h-full w-full object-cover">';
    }
    return (
      '<div class="flex h-full w-full flex-col items-center justify-center gap-3 bg-gradient-to-br ' +
      cat.grad + '">' +
      '<svg class="icon h-16 w-16 ' + cat.solid + ' opacity-70" aria-hidden="true">' +
      '<use href="/assets/icons.svg#' + cat.icon + '"></use></svg>' +
      '<span class="text-sm text-muted">No photograph provided</span></div>'
    );
  }

  function row(label, value, icon) {
    return (
      '<div class="flex items-start gap-3 py-3">' +
      '<svg class="icon mt-0.5 h-[1.15rem] w-[1.15rem] text-faint" aria-hidden="true">' +
      '<use href="/assets/icons.svg#' + icon + '"></use></svg>' +
      '<div class="min-w-0">' +
      '<p class="text-xs text-faint">' + e(label) + '</p>' +
      '<p class="text-sm font-medium text-heading">' + e(value) + '</p>' +
      '</div></div>'
    );
  }

  function render(item) {
    document.title = item.title + ' — Lost & Found';
    var crumb = document.querySelector('[data-crumb]');
    if (crumb) crumb.textContent = item.reference;

    var resolved = item.status === 'RESOLVED';

    host.innerHTML =
      '<div class="grid gap-8 lg:grid-cols-[minmax(0,1fr)_22rem] lg:gap-10">' +

        '<div>' +
          '<div class="overflow-hidden rounded-2xl border border-line bg-surface shadow-[var(--shadow-card)]">' +
            '<div class="aspect-[4/3]">' + media(item) + '</div>' +
          '</div>' +

          '<div class="mt-6 flex flex-wrap items-center gap-2">' +
            LF.kindPill(item.kind, true) +
            LF.statusPill(item.status, true) +
            LF.categoryPill(item) +
          '</div>' +

          '<h1 class="mt-4 text-3xl text-heading sm:text-4xl">' + e(item.title) + '</h1>' +

          '<p class="mt-5 whitespace-pre-line leading-relaxed text-body">' + e(item.description) + '</p>' +
        '</div>' +

        '<aside class="lg:pt-2">' +
          '<div class="card divide-y divide-line">' +
            '<div class="px-5 py-2">' +
              row('Where', item.location, 'i-pin') +
              (item.happenedOn ? row('When', LF.formatDate(item.happenedOn), 'i-calendar') : '') +
              (item.colour ? row('Colour', item.colour, 'i-tag') : '') +
              row('Posted', LF.timeAgo(item.createdAt), 'i-clock') +
              row('Posted by', item.reporterName, 'i-user') +
              row('Reference', item.reference, 'i-qr') +
            '</div>' +

            '<div class="p-5">' +
              (resolved
                ? '<div class="rounded-xl bg-found-soft p-4 text-center">' +
                  '<svg class="icon mx-auto h-6 w-6 text-found" aria-hidden="true"><use href="/assets/icons.svg#i-check"></use></svg>' +
                  '<p class="mt-2 text-sm font-semibold text-found-text">Back with its owner</p>' +
                  '<p class="mt-1 text-xs text-found-text/80">Nothing more to do here.</p></div>'
                : '<button type="button" data-claim class="btn btn-primary w-full">' +
                  '<svg class="icon h-[1.15rem] w-[1.15rem]" aria-hidden="true"><use href="/assets/icons.svg#i-hand"></use></svg>' +
                  (item.kind === 'FOUND' ? 'This is mine' : 'I think I found this') +
                  '</button>') +
              '<a href="/browse.html" class="btn btn-secondary mt-2 w-full">Back to the board</a>' +
              '<p class="mt-4 text-xs leading-relaxed text-faint">' +
                'Contact details are never shown on the board. Claiming opens a conversation through the registry.' +
              '</p>' +
            '</div>' +
          '</div>' +
        '</aside>' +
      '</div>';

    var claim = host.querySelector('[data-claim]');
    if (claim) {
      claim.addEventListener('click', function () {
        LF.toast.info('Claims arrive in the next release. For now, note the reference ' + item.reference + '.', {
          title: 'Not wired up yet'
        });
      });
    }
  }

  function notFound(reference) {
    host.innerHTML = LF.emptyState({
      icon: 'i-search',
      title: 'No item with that reference',
      message: reference
        ? 'Nothing on the board matches ' + reference + '. It may have been removed, or the link may be mistyped.'
        : 'This link is missing a reference code.',
      actionHref: '/browse.html',
      actionLabel: 'Browse the board'
    });
  }

  function load() {
    var reference = LF.qs('ref');
    if (!reference) {
      notFound(null);
      return;
    }

    host.innerHTML = skeleton();

    LF.api
      .get('/api/items/' + encodeURIComponent(reference))
      .then(render)
      .catch(function (err) {
        if (err.status === 404) {
          notFound(reference);
          return;
        }
        host.innerHTML = LF.errorState(err.message);
        var retry = host.querySelector('[data-retry]');
        if (retry) retry.addEventListener('click', load);
      });
  }

  document.addEventListener('lf:ready', load);
})();
