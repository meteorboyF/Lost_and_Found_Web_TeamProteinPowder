/**
 * gallery.js — successful reunions.
 *
 * The point is trust: a board full of open cases looks like a place things
 * disappear into. A wall of resolved ones shows it works.
 */
(function () {
  'use strict';

  var LF = window.LF;
  var e = LF.escapeHtml;

  function card(item) {
    var cat = LF.category(item.category);
    var media = item.photoUrl
      ? '<img src="' + e(item.photoUrl) + '" alt="" loading="lazy" class="h-full w-full object-cover">'
      : '<div class="grid h-full w-full place-items-center bg-gradient-to-br ' + cat.grad + '">' +
        '<svg class="icon h-10 w-10 ' + cat.solid + ' opacity-70" aria-hidden="true">' +
        '<use href="/assets/icons.svg#' + cat.icon + '"></use></svg></div>';

    return (
      '<a href="/item.html?ref=' + encodeURIComponent(item.reference) + '" ' +
         'class="card card-interactive group relative block overflow-hidden">' +
        '<div class="relative aspect-square overflow-hidden">' + media +
          '<div class="absolute inset-x-0 bottom-0 bg-gradient-to-t from-black/70 to-transparent p-3">' +
            '<p class="truncate text-sm font-semibold text-white">' + e(item.title) + '</p>' +
            '<p class="truncate text-xs text-white/80">' + e(item.location) + '</p>' +
          '</div>' +
          '<span class="pill absolute right-2 top-2 bg-found text-white dark:text-[#11121a]">' +
            '<svg class="icon h-3.5 w-3.5" aria-hidden="true"><use href="/assets/icons.svg#i-check"></use></svg>' +
            'Reunited</span>' +
        '</div>' +
      '</a>'
    );
  }

  function load() {
    var host = document.querySelector('[data-gallery]');
    host.innerHTML = LF.skeletonGrid(6);

    Promise.all([
      LF.api.get('/api/items?status=RESOLVED&size=60&sort=recent'),
      LF.api.get('/api/items/stats')
    ])
      .then(function (results) {
        var page = results[0];
        var stats = results[1];

        var rate = stats.total ? Math.round((stats.resolved / stats.total) * 100) : 0;
        var counter = document.querySelector('[data-gallery-count]');
        if (counter) {
          counter.textContent = stats.resolved;
        }
        var rateEl = document.querySelector('[data-gallery-rate]');
        if (rateEl) rateEl.textContent = rate + '%';

        if (!page.content.length) {
          host.innerHTML = LF.emptyState({
            icon: 'i-award',
            title: 'No reunions yet',
            message: 'When the first item makes it back to its owner, it appears here.',
            actionHref: '/browse.html',
            actionLabel: 'Browse the board'
          });
          return;
        }

        host.innerHTML =
          '<div class="grid grid-cols-2 gap-4 sm:grid-cols-3 lg:grid-cols-4">' +
          page.content.map(card).join('') + '</div>';
      })
      .catch(function (err) {
        host.innerHTML = LF.errorState(err.message);
        var retry = host.querySelector('[data-retry]');
        if (retry) retry.addEventListener('click', load);
      });
  }

  document.addEventListener('lf:ready', load);
})();
