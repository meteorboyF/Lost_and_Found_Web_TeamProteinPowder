/**
 * alerts.js — "tell me if something like this turns up".
 *
 * There is no mail server, so an alert is a watch the browser re-checks:
 * every page load asks the matching endpoint whether anything new has
 * appeared for the items you posted, and surfaces it. That is honest about
 * what it is — the UI never promises an email it cannot send.
 */
(function () {
  'use strict';

  var LF = window.LF;
  var e = LF.escapeHtml;
  var SEEN_KEY = 'lf.seenMatches';

  function seen() {
    try {
      var raw = JSON.parse(localStorage.getItem(SEEN_KEY));
      return Array.isArray(raw) ? raw : [];
    } catch (err) {
      return [];
    }
  }

  function markSeen(references) {
    try {
      var merged = seen().concat(references).filter(function (v, i, a) { return a.indexOf(v) === i; });
      /* Keep this bounded — it is a notification log, not an archive. */
      localStorage.setItem(SEEN_KEY, JSON.stringify(merged.slice(-200)));
    } catch (err) {}
  }

  /**
   * Ask for matches on everything this browser posted, and keep only pairs
   * we have not already shown.
   */
  function check() {
    var mine = LF.mine.all();
    if (!mine.items.length) return Promise.resolve([]);

    var already = seen();

    return Promise.all(mine.items.map(function (ref) {
      return LF.api
        .get('/api/items/' + encodeURIComponent(ref) + '/matches')
        .then(function (matches) {
          return matches.map(function (m) {
            return { source: ref, match: m, id: ref + '>' + m.item.reference };
          });
        })
        .catch(function () { return []; });
    })).then(function (lists) {
      return lists.flat().filter(function (entry) {
        return already.indexOf(entry.id) === -1;
      });
    });
  }

  /* ------------------------------------------------------------------
     The bell in the masthead
     ------------------------------------------------------------------ */

  function mount(entries) {
    var host = document.querySelector('[data-alerts]');
    if (!host) return;

    var button = host.querySelector('[data-alerts-toggle]');
    var badge = host.querySelector('[data-alerts-badge]');
    var panel = host.querySelector('[data-alerts-panel]');

    button.hidden = false;
    badge.textContent = entries.length;
    badge.hidden = entries.length === 0;
    button.setAttribute('aria-label',
      entries.length ? entries.length + ' possible matches for your posts' : 'No new matches');

    panel.innerHTML = entries.length
      ? '<p class="border-b border-line px-4 py-3 text-sm font-semibold text-heading">' +
          'Possible matches for your posts</p>' +
        '<ul class="max-h-80 overflow-y-auto">' + entries.slice(0, 8).map(function (entry) {
          var m = entry.match;
          return (
            '<li><a class="flex items-start gap-3 px-4 py-3 transition hover:bg-sunken" ' +
               'href="/item.html?ref=' + encodeURIComponent(m.item.reference) + '">' +
              '<span class="pill shrink-0 ' +
                (m.percent >= 65 ? 'bg-found text-white dark:text-[#11121a]' : 'bg-brand-soft text-brand-text') +
              '">' + m.percent + '%</span>' +
              '<span class="min-w-0">' +
                '<span class="block truncate text-sm font-medium text-heading">' + e(m.item.title) + '</span>' +
                '<span class="block truncate text-xs text-muted">' +
                  (m.reasons[0] ? e(m.reasons[0]) : e(m.item.location)) + '</span>' +
              '</span>' +
            '</a></li>'
          );
        }).join('') + '</ul>' +
        '<button type="button" class="w-full border-t border-line px-4 py-2.5 text-sm text-muted transition hover:bg-sunken hover:text-heading" ' +
          'data-alerts-clear>Mark all as seen</button>'
      : '<p class="px-4 py-6 text-center text-sm text-muted">Nothing new right now.</p>';

    function setOpen(open) {
      panel.hidden = !open;
      button.setAttribute('aria-expanded', open ? 'true' : 'false');
    }

    button.addEventListener('click', function (ev) {
      ev.stopPropagation();
      setOpen(panel.hidden);
    });

    document.addEventListener('click', function (ev) {
      if (!panel.hidden && !host.contains(ev.target)) setOpen(false);
    });

    document.addEventListener('keydown', function (ev) {
      if (ev.key === 'Escape' && !panel.hidden) {
        setOpen(false);
        button.focus();
      }
    });

    var clear = panel.querySelector('[data-alerts-clear]');
    if (clear) {
      clear.addEventListener('click', function () {
        markSeen(entries.map(function (x) { return x.id; }));
        badge.hidden = true;
        badge.textContent = '0';
        panel.innerHTML = '<p class="px-4 py-6 text-center text-sm text-muted">Nothing new right now.</p>';
        setOpen(false);
        LF.toast.info('Marked as seen. New matches will show up here.');
      });
    }

    setOpen(false);

    /* A strong match is worth interrupting for; a weak one is not. */
    var strong = entries.filter(function (x) { return x.match.percent >= 65; });
    if (strong.length) {
      LF.toast(
        strong.length === 1
          ? 'Something on the board looks like your ' + strong[0].match.item.categoryLabel.toLowerCase() + '.'
          : strong.length + ' posts look like things you reported.',
        { title: 'Possible match', duration: 9000 }
      );
    }
  }

  document.addEventListener('lf:ready', function () {
    if (!document.querySelector('[data-alerts]')) return;
    check().then(mount).catch(function () {
      /* Alerts are supplementary — never let them break a page. */
    });
  });
})();
