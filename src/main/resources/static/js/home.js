/**
 * home.js — landing page.
 *
 * Confirms the API is reachable and reports honestly either way: a failed
 * request and an empty board look completely different to the visitor.
 */
(function () {
  'use strict';

  var LF = window.LF;

  function setApiStatus(state, label) {
    var dot = document.querySelector('[data-api-dot]');
    var text = document.querySelector('[data-api-label]');
    if (!dot || !text) return;

    dot.className = 'inline-block h-2 w-2 rounded-full ' +
      (state === 'up' ? 'bg-found' : state === 'down' ? 'bg-lost' : 'bg-faint');
    text.textContent = label;
  }

  function skeletonGrid(count) {
    var cells = '';
    for (var i = 0; i < count; i++) {
      cells +=
        '<div class="overflow-hidden rounded-sm border border-line bg-surface">' +
        '<div class="aspect-[4/3] animate-pulse bg-sunken"></div>' +
        '<div class="space-y-3 p-4">' +
        '<div class="h-5 w-3/4 animate-pulse rounded-xs bg-sunken"></div>' +
        '<div class="h-3 w-1/2 animate-pulse rounded-xs bg-sunken"></div>' +
        '</div></div>';
    }
    return '<div class="grid gap-5 sm:grid-cols-2 lg:grid-cols-3">' + cells + '</div>';
  }

  function emptyState(message) {
    return (
      '<div class="grid justify-items-center gap-3 rounded-sm border border-dashed border-line-strong px-6 py-16 text-center">' +
      '<svg class="icon h-10 w-10 text-faint" aria-hidden="true"><use href="/assets/icons.svg#i-inbox"></use></svg>' +
      '<p class="font-display text-xl">Nothing on the board yet</p>' +
      '<p class="max-w-sm text-sm text-muted">' + LF.escapeHtml(message) + '</p>' +
      '<a href="/report.html?kind=found" class="mt-2 rounded-sm bg-primary px-5 py-2.5 text-sm font-bold text-surface hover:bg-found hover:text-white">' +
      'Post the first item</a>' +
      '</div>'
    );
  }

  function errorState(message) {
    return (
      '<div role="alert" class="grid justify-items-center gap-3 rounded-sm border border-lost bg-lost-wash px-6 py-16 text-center text-on-wash">' +
      '<svg class="icon h-10 w-10 text-lost" aria-hidden="true"><use href="/assets/icons.svg#i-alert"></use></svg>' +
      '<p class="font-display text-xl">Could not load the board</p>' +
      '<p class="max-w-md text-sm opacity-85">' + LF.escapeHtml(message) + '</p>' +
      '<button type="button" data-retry class="mt-2 rounded-sm border border-line-strong px-5 py-2.5 text-sm font-bold hover:border-primary">' +
      'Try again</button>' +
      '</div>'
    );
  }

  function load() {
    var host = document.querySelector('[data-recent]');
    if (!host) return;

    setApiStatus('checking', 'Checking the registry…');
    host.innerHTML = skeletonGrid(3);

    LF.api
      .health()
      .then(function (info) {
        setApiStatus('up', 'Registry online · ' + info.service);
        /* The items endpoint arrives with the next feature; until then the
           board is genuinely empty rather than broken. */
        host.innerHTML = emptyState(host.dataset.emptyMessage || '');
      })
      .catch(function (err) {
        setApiStatus('down', 'Registry unreachable');
        host.innerHTML = errorState(err.message);
        var retry = host.querySelector('[data-retry]');
        if (retry) retry.addEventListener('click', load);
      });
  }

  document.addEventListener('lf:ready', load);
})();
