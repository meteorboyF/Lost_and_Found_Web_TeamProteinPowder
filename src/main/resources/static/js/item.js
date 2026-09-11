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
      '<div class="aspect-[4/3] animate-pulse rounded-sm bg-sunken"></div>' +
      '<div class="space-y-4">' +
      '<div class="h-4 w-24 animate-pulse rounded-xs bg-sunken"></div>' +
      '<div class="h-10 w-3/4 animate-pulse rounded-xs bg-sunken"></div>' +
      '<div class="h-4 w-full animate-pulse rounded-xs bg-sunken"></div>' +
      '<div class="h-4 w-5/6 animate-pulse rounded-xs bg-sunken"></div>' +
      '</div></div>'
    );
  }

  function render(item) {
    document.title = item.title + ' — Lost & Found';
    var crumb = document.querySelector('[data-crumb]');
    if (crumb) crumb.textContent = item.reference;

    var media = item.photoUrl
      ? '<img src="' + e(item.photoUrl) + '" alt="Photograph of ' + e(item.title) + '" ' +
        'class="h-full w-full object-cover">'
      : '<div class="grid h-full w-full place-items-center gap-2 bg-sunken text-muted">' +
        '<svg class="icon h-10 w-10" aria-hidden="true"><use href="/assets/icons.svg#i-image"></use></svg>' +
        '<span class="text-2xs">No photograph was provided</span></div>';

    host.innerHTML =
      '<div class="grid gap-8 lg:grid-cols-2 lg:gap-12">' +

        '<div class="overflow-hidden rounded-sm border border-line bg-sunken">' +
          '<div class="aspect-[4/3]">' + media + '</div>' +
        '</div>' +

        '<div>' +
          '<div class="flex flex-wrap items-center gap-3">' +
            LF.kindTag(item.kind) + LF.statusChip(item.status, 'lg') +
          '</div>' +

          '<h1 class="mt-4 text-3xl sm:text-4xl">' + e(item.title) + '</h1>' +

          '<p class="mt-3 font-mono text-2xs text-muted">' + e(item.reference) + '</p>' +

          '<p class="mt-6 whitespace-pre-line leading-relaxed text-secondary">' +
            e(item.description) + '</p>' +

          '<dl class="mt-8 grid grid-cols-[7rem_1fr] gap-x-6 gap-y-3 border-t border-line pt-6 text-sm">' +
            row('Category', item.categoryLabel) +
            (item.colour ? row('Colour', item.colour) : '') +
            row('Where', item.location) +
            (item.happenedOn ? row('When', LF.formatDate(item.happenedOn)) : '') +
            row('Posted', LF.timeAgo(item.createdAt)) +
            row('Posted by', item.reporterName) +
          '</dl>' +

          '<div class="mt-8 flex flex-wrap gap-3 border-t border-line pt-6">' +
            (item.status === 'RESOLVED'
              ? '<p class="text-sm text-muted">This item is back with its owner. Nothing more to do here.</p>'
              : '<button type="button" data-claim ' +
                'class="inline-flex items-center gap-2 rounded-sm bg-primary px-6 py-3.5 font-bold text-surface ' +
                'transition hover:bg-found hover:text-white">' +
                (item.kind === 'FOUND' ? 'This is mine' : 'I think I found this') +
                '</button>') +
            '<a href="/browse.html" class="rounded-sm border border-line-strong px-6 py-3.5 font-bold hover:border-primary">' +
              'Back to the board</a>' +
          '</div>' +

          '<p class="mt-4 text-2xs text-muted">' +
            'Contact details are never shown on the board. Claiming opens a conversation through the registry.' +
          '</p>' +
        '</div>' +
      '</div>';

    var claim = host.querySelector('[data-claim]');
    if (claim) {
      claim.addEventListener('click', function () {
        /* The claim conversation is the next feature. Saying so is better than
           a dead button that silently does nothing. */
        LF.toast.info('Claims open in the next release. For now, note the reference ' + item.reference + '.', {
          title: 'Not wired up yet'
        });
      });
    }
  }

  function row(label, value) {
    return '<dt class="u-caps text-muted">' + e(label) + '</dt><dd>' + e(value) + '</dd>';
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
