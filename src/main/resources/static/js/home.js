/**
 * home.js — landing page: live counts, category tiles, the hero card stack,
 * and the newest posts.
 */
(function () {
  'use strict';

  var LF = window.LF;
  var e = LF.escapeHtml;

  function setApiStatus(state, label) {
    var dot = document.querySelector('[data-api-dot]');
    var text = document.querySelector('[data-api-label]');
    if (!dot || !text) return;
    dot.className = 'inline-block h-1.5 w-1.5 rounded-full ' +
      (state === 'up' ? 'bg-found' : state === 'down' ? 'bg-lost' : 'bg-faint');
    text.textContent = label;
  }

  /** Counts up to the real figure — live feel without inventing a number. */
  function countTo(el, target) {
    var reduced = window.matchMedia &&
      window.matchMedia('(prefers-reduced-motion: reduce)').matches;
    if (reduced || target <= 0) {
      el.textContent = String(target);
      return;
    }
    var start = performance.now();
    (function frame(now) {
      var t = Math.min(1, ((now || start) - start) / 700);
      el.textContent = String(Math.round(target * (1 - Math.pow(1 - t, 3))));
      if (t < 1) requestAnimationFrame(frame);
    })(start);
  }

  function loadStats() {
    return LF.api.get('/api/items/stats').then(function (stats) {
      ['open', 'resolved', 'found', 'lost'].forEach(function (key) {
        var el = document.querySelector('[data-stat="' + key + '"]');
        if (el) countTo(el, stats[key]);
      });
    });
  }

  /* ------------------------------------------------------------------
     Category tiles
     ------------------------------------------------------------------ */

  function loadCategories() {
    var host = document.querySelector('[data-category-tiles]');
    if (!host) return Promise.resolve();

    return LF.api.get('/api/items/categories').then(function (categories) {
      host.innerHTML = categories
        .slice(0, 8)
        .map(function (c) {
          var cat = LF.category(c.value);
          return (
            '<a href="/browse.html?category=' + encodeURIComponent(c.value) + '" ' +
            'class="card card-interactive flex flex-col items-start gap-3 p-4">' +
            '<span class="grid h-10 w-10 place-items-center rounded-xl ' + cat.tint + '">' +
            '<svg class="icon h-5 w-5" aria-hidden="true"><use href="/assets/icons.svg#' + cat.icon + '"></use></svg>' +
            '</span>' +
            '<span class="text-sm font-semibold text-heading">' + e(c.label) + '</span>' +
            '</a>'
          );
        })
        .join('');
    });
  }

  /* ------------------------------------------------------------------
     Hero card stack. Three real items, offset and slightly rotated, so the
     hero shows the product instead of describing it.
     ------------------------------------------------------------------ */

  function renderHeroCards(items) {
    var host = document.querySelector('[data-hero-cards]');
    if (!host || !items.length) return;

    var layout = [
      { cls: 'left-0 top-4 w-[17rem] -rotate-[5deg] z-10' },
      { cls: 'left-24 top-28 w-[17rem] rotate-[3deg] z-20' },
      { cls: 'left-2 top-56 w-[17rem] -rotate-[2deg] z-30' }
    ];

    host.innerHTML =
      '<div class="relative h-[26rem]" aria-hidden="true">' +
      items.slice(0, 3).map(function (item, i) {
        var cat = LF.category(item.category);
        var thumb = item.photoUrl
          ? '<img src="' + e(item.photoUrl) + '" alt="" class="h-12 w-12 rounded-lg object-cover">'
          : '<span class="grid h-12 w-12 place-items-center rounded-lg bg-gradient-to-br ' + cat.grad + '">' +
            '<svg class="icon h-6 w-6 ' + cat.solid + '" aria-hidden="true"><use href="/assets/icons.svg#' +
            cat.icon + '"></use></svg></span>';

        return (
          '<div class="absolute ' + layout[i].cls +
          ' rounded-2xl border border-line bg-surface p-4 shadow-[var(--shadow-pop)] transition duration-500 hover:rotate-0">' +
          '<div class="flex items-center gap-3">' + thumb +
          '<div class="min-w-0 flex-1">' +
          '<p class="truncate text-sm font-semibold text-heading">' + e(item.title) + '</p>' +
          '<p class="truncate text-xs text-muted">' + e(item.location) + '</p>' +
          '</div></div>' +
          '<div class="mt-3 flex items-center justify-between">' +
          LF.kindPill(item.kind) +
          '<span class="text-xs text-faint">' + e(LF.timeAgo(item.createdAt)) + '</span>' +
          '</div></div>'
        );
      }).join('') +
      '</div>';
  }

  /* ------------------------------------------------------------------
     Recent
     ------------------------------------------------------------------ */

  function loadRecent() {
    var host = document.querySelector('[data-recent]');
    if (!host) return Promise.resolve();

    host.innerHTML = LF.skeletonGrid(3);

    return LF.api.get('/api/items?size=6&sort=recent').then(function (page) {
      if (!page.content.length) {
        host.innerHTML = LF.emptyState({
          title: 'Nothing on the board yet',
          message: host.dataset.emptyMessage || '',
          actionHref: '/report.html',
          actionLabel: 'Post the first item'
        });
        return;
      }
      host.innerHTML = LF.itemGrid(page.content);
      renderHeroCards(page.content);
    });
  }

  function load() {
    setApiStatus('checking', 'Checking the registry…');

    Promise.all([loadStats(), loadCategories(), loadRecent()])
      .then(function () {
        setApiStatus('up', 'Registry online');
      })
      .catch(function (err) {
        setApiStatus('down', 'Registry unreachable');
        var host = document.querySelector('[data-recent]');
        if (host) {
          host.innerHTML = LF.errorState(err.message);
          var retry = host.querySelector('[data-retry]');
          if (retry) retry.addEventListener('click', load);
        }
        document.querySelectorAll('[data-stat]').forEach(function (el) {
          el.textContent = '—';
        });
      });
  }

  document.addEventListener('lf:ready', load);
})();
