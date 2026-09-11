/**
 * map.js — campus map.
 *
 * The map is hand-drawn inline SVG rather than a tile service: the campus is a
 * fixed, small area, and a schematic of named buildings is more legible at a
 * glance than a real map at this zoom. It also means no third-party request
 * and no API key.
 *
 * Items are placed by matching their free-text location against building
 * keywords, because posts say "Central Library, level 2" rather than carrying
 * coordinates.
 */
(function () {
  'use strict';

  var LF = window.LF;
  var e = LF.escapeHtml;

  /* A schematic campus. Coordinates are in the SVG's 100x70 viewBox. */
  var BUILDINGS = [
    { id: 'library',  name: 'Central Library',   x: 20, y: 16, w: 22, h: 14, keywords: ['library', 'central lib'] },
    { id: 'eng',      name: 'Engineering',       x: 50, y: 10, w: 20, h: 12, keywords: ['engineering', 'eng block', 'block c'] },
    { id: 'science',  name: 'Science Block',     x: 74, y: 14, w: 18, h: 14, keywords: ['science', 'lab', 'lecture theatre'] },
    { id: 'student',  name: 'Student Centre',    x: 16, y: 38, w: 24, h: 14, keywords: ['student centre', 'student center', 'canteen', 'cafeteria', 'cafe'] },
    { id: 'sports',   name: 'Sports Complex',    x: 48, y: 40, w: 20, h: 16, keywords: ['sport', 'gym', 'field', 'court', 'changing'] },
    { id: 'arts',     name: 'Arts Faculty',      x: 74, y: 42, w: 18, h: 12, keywords: ['arts', 'humanities', 'auditorium'] },
    { id: 'gate',     name: 'East Gate',         x: 84, y: 60, w: 12, h: 7,  keywords: ['gate', 'entrance', 'security'] },
    { id: 'hall',     name: 'Halls of Residence', x: 8, y: 58, w: 24, h: 9,  keywords: ['hall', 'residence', 'dorm', 'hostel'] }
  ];

  var state = { items: [], selected: null, kind: '' };

  /** Which building does this item's free-text location point at? */
  function locate(item) {
    var text = (item.location || '').toLowerCase();
    for (var i = 0; i < BUILDINGS.length; i++) {
      var b = BUILDINGS[i];
      for (var k = 0; k < b.keywords.length; k++) {
        if (text.indexOf(b.keywords[k]) !== -1) return b;
      }
    }
    return null;
  }

  function grouped() {
    var map = {};
    BUILDINGS.forEach(function (b) { map[b.id] = []; });
    var unplaced = [];

    state.items
      .filter(function (i) { return !state.kind || i.kind === state.kind; })
      .forEach(function (item) {
        var b = locate(item);
        if (b) map[b.id].push(item);
        else unplaced.push(item);
      });

    return { map: map, unplaced: unplaced };
  }

  /* ------------------------------------------------------------------
     Render
     ------------------------------------------------------------------ */

  function render() {
    var g = grouped();
    var host = document.querySelector('[data-map]');

    /* Heat: more reports at a building means a stronger tint. */
    var counts = BUILDINGS.map(function (b) { return g.map[b.id].length; });
    var max = Math.max.apply(null, counts.concat([1]));

    var shapes = BUILDINGS.map(function (b) {
      var items = g.map[b.id];
      var heat = items.length / max;
      var selected = state.selected === b.id;

      return (
        '<g class="cursor-pointer" data-building="' + b.id + '" tabindex="0" role="button" ' +
           'aria-label="' + e(b.name) + ', ' + items.length + ' item' + (items.length === 1 ? '' : 's') + '">' +
          '<rect x="' + b.x + '" y="' + b.y + '" width="' + b.w + '" height="' + b.h + '" rx="1.5" ' +
            'fill="var(--sc-brand)" fill-opacity="' + (0.08 + heat * 0.42).toFixed(3) + '" ' +
            'stroke="var(--sc-brand)" stroke-opacity="' + (selected ? 1 : 0.35) + '" ' +
            'stroke-width="' + (selected ? 0.8 : 0.4) + '"></rect>' +
          '<text x="' + (b.x + b.w / 2) + '" y="' + (b.y + b.h / 2 - 0.5) + '" ' +
            'text-anchor="middle" font-size="2.6" fill="var(--sc-heading)" ' +
            'font-weight="600">' + e(b.name) + '</text>' +
          (items.length
            ? '<text x="' + (b.x + b.w / 2) + '" y="' + (b.y + b.h / 2 + 3.4) + '" text-anchor="middle" ' +
              'font-size="2.4" fill="var(--sc-muted)">' + items.length + ' item' +
              (items.length === 1 ? '' : 's') + '</text>'
            : '') +
        '</g>'
      );
    }).join('');

    host.innerHTML =
      '<svg viewBox="0 0 100 70" class="w-full rounded-2xl border border-line bg-surface" ' +
           'role="img" aria-label="Schematic campus map showing where items were lost and found">' +
        '<rect width="100" height="70" fill="var(--sc-sunken)" rx="2"></rect>' +
        /* paths, purely decorative */
        '<path d="M0 34 H100 M44 0 V70 M72 0 V70" stroke="var(--sc-line)" stroke-width="0.6" fill="none"></path>' +
        shapes +
      '</svg>';

    host.querySelectorAll('[data-building]').forEach(function (node) {
      var open = function () {
        state.selected = state.selected === node.dataset.building ? null : node.dataset.building;
        render();
        paintList();
      };
      node.addEventListener('click', open);
      node.addEventListener('keydown', function (ev) {
        if (ev.key === 'Enter' || ev.key === ' ') {
          ev.preventDefault();
          open();
        }
      });
    });

    paintList();
  }

  function paintList() {
    var g = grouped();
    var host = document.querySelector('[data-map-list]');
    var heading = document.querySelector('[data-map-heading]');

    var items;
    if (state.selected) {
      var b = BUILDINGS.filter(function (x) { return x.id === state.selected; })[0];
      items = g.map[state.selected];
      heading.textContent = b.name + ' — ' + items.length + ' item' + (items.length === 1 ? '' : 's');
    } else {
      items = state.items.filter(function (i) { return !state.kind || i.kind === state.kind; });
      heading.textContent = 'All locations — ' + items.length + ' item' + (items.length === 1 ? '' : 's');
    }

    if (!items.length) {
      host.innerHTML =
        '<p class="rounded-xl border border-dashed border-line-strong px-4 py-10 text-center text-sm text-muted">' +
        'Nothing reported here yet.</p>';
      return;
    }

    host.innerHTML = LF.itemGrid(items.slice(0, 12));

    if (g.unplaced.length && !state.selected) {
      host.insertAdjacentHTML('beforeend',
        '<p class="mt-4 text-xs text-faint">' + g.unplaced.length +
        ' item' + (g.unplaced.length === 1 ? ' does' : 's do') +
        ' not name a building we recognise, so ' +
        (g.unplaced.length === 1 ? 'it is' : 'they are') + ' not placed on the map.</p>');
    }
  }

  /* ------------------------------------------------------------------
     Boot
     ------------------------------------------------------------------ */

  function load() {
    var host = document.querySelector('[data-map]');
    host.innerHTML = '<div class="aspect-[10/7] w-full animate-pulse rounded-2xl bg-sunken"></div>';

    LF.api
      .get('/api/items?size=60&sort=recent')
      .then(function (page) {
        state.items = page.content;
        render();
      })
      .catch(function (err) {
        host.innerHTML = LF.errorState(err.message);
        var retry = host.querySelector('[data-retry]');
        if (retry) retry.addEventListener('click', load);
      });
  }

  document.addEventListener('lf:ready', function () {
    document.querySelectorAll('[data-map-kind]').forEach(function (btn) {
      btn.addEventListener('click', function () {
        state.kind = btn.dataset.mapKind;
        document.querySelectorAll('[data-map-kind]').forEach(function (b) {
          b.setAttribute('aria-pressed', b === btn ? 'true' : 'false');
        });
        render();
      });
    });
    load();
  });
})();
