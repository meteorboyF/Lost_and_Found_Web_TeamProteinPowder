/** Explore real reported locations. Text-only legacy reports never receive invented pins. */
(function () {
  'use strict';
  var LF = window.LF, e = LF.escapeHtml, geo = LF.maps;
  var state = { items: [], kind: '', query: '', resolved: false, selected: LF.qs('ref'), origin: null, loading: false, tileError: false };
  var map, markers = new Map(), layer, area, userMarker, accuracy;
  var firstLoad = true;
  var list = document.querySelector('[data-map-list]'), status = document.querySelector('[data-map-status]');

  function visible() {
    var items = state.items.filter(function (item) {
      return (!state.kind || item.kind === state.kind) && (state.resolved || item.status !== 'RESOLVED') &&
        (!state.query || [item.title, item.location, item.categoryLabel, item.description].join(' ').toLowerCase().includes(state.query));
    });
    if (state.origin) items.sort(function (a, b) {
      return (geo.hasPin(a) ? geo.distance(state.origin, a) : Infinity) - (geo.hasPin(b) ? geo.distance(state.origin, b) : Infinity);
    });
    return items;
  }
  function updateStatus(message) {
    var pinned = visible().filter(geo.hasPin).length;
    status.textContent = message || (pinned + ' reported pin' + (pinned === 1 ? '' : 's') +
      ' · ' + (visible().length - pinned) + ' reports without coordinates' +
      (state.tileError ? '. Map imagery could not load; report details and directions are still available.' : ''));
  }
  function setReference(reference) {
    var url = new URL(window.location.href);
    if (reference) url.searchParams.set('ref', reference); else url.searchParams.delete('ref');
    history.replaceState(null, '', url.pathname + url.search);
  }
  function closeSelection() {
    state.selected = null; setReference(null);
    if (map) map.closePopup();
    paint();
  }
  function paintSelection(item) {
    var host = document.querySelector('[data-map-selection]');
    if (area && map) { map.removeLayer(area); area = null; }
    if (!item) { host.innerHTML = ''; return; }
    var pinned = geo.hasPin(item), itemUrl = '/item.html?ref=' + encodeURIComponent(item.reference);
    host.innerHTML = '<div class="map-selection-card"><button type="button" class="map-close" data-map-close aria-label="Close selected report">' +
      '<svg class="icon" aria-hidden="true"><use href="/assets/icons.svg#i-close"></use></svg></button>' +
      LF.kindPill(item.kind) + '<h3>' + e(item.title) + '</h3><p>' + e(item.location) + '</p>' +
      (pinned ? '<p class="map-location-coords">' + geo.coordinate(item.latitude) + ', ' + geo.coordinate(item.longitude) + '</p>' +
        (item.searchRadiusMeters ? '<p class="mt-2">Suggested search area: within ' + item.searchRadiusMeters + ' metres of the pin.</p>' : '') +
        '<a class="btn btn-primary" href="' + e(geo.directions(item, state.origin)) + '" target="_blank" rel="noopener noreferrer">Walking directions</a>'
        : '<p class="mt-2">The poster has not added a pin. Read their location notes or ask for a more precise spot.</p>') +
      '<a class="btn btn-secondary" href="' + itemUrl + '#clues">View report &amp; clues</a>' +
      '<button type="button" class="btn btn-ghost" data-map-share>Copy link to this report</button></div>';
    host.querySelector('[data-map-close]').addEventListener('click', closeSelection);
    host.querySelector('[data-map-share]').addEventListener('click', function () {
      if (!navigator.clipboard) { LF.toast.info('Copy the page address to share this report.'); return; }
      navigator.clipboard.writeText(window.location.href).then(function () { LF.toast.success('Report location link copied.'); })
        .catch(function () { LF.toast.info('Copy the page address to share this report.'); });
    });
    if (pinned && map && item.searchRadiusMeters) area = L.circle([item.latitude, item.longitude], {
      radius: item.searchRadiusMeters, color: item.kind === 'LOST' ? '#be123c' : '#047857', weight: 1.5, fillOpacity: 0.12
    }).addTo(map);
  }
  function select(reference, pan) {
    var item = visible().find(function (record) { return record.reference === reference; });
    if (!item) return;
    state.selected = reference; setReference(reference);
    markers.forEach(function (marker, ref) {
      var record = state.items.find(function (entry) { return entry.reference === ref; });
      marker.setIcon(geo.icon(record.kind, ref === reference));
    });
    paintSelection(item); paintList();
    var marker = markers.get(reference);
    if (marker && map) {
      if (pan) map.setView(marker.getLatLng(), Math.max(18, map.getZoom()), { animate: false });
      // The selected report card supplies the same details on narrow screens.
      // Keep its popup from obscuring the mobile map toolbar on initial focus.
      if (window.innerWidth >= 768 || !pan) marker.openPopup();
      else map.closePopup();
    }
  }
  function paintList() {
    var items = visible();
    document.querySelector('[data-map-heading]').textContent = state.origin ? 'Closest reports first' : 'Reports near campus';
    document.querySelector('[data-map-count]').textContent = items.length;
    if (!items.length) {
      list.innerHTML = '<p class="map-list-empty">No reports match these filters. Try another search or <a href="/report.html" class="text-brand">post an item</a>.</p>';
      return;
    }
    list.innerHTML = items.map(function (item) {
      var pinned = geo.hasPin(item), category = LF.category(item.category);
      var distance = state.origin && pinned ? geo.distanceLabel(geo.distance(state.origin, item)) + ' from you · ' : '';
      return '<button type="button" class="map-report-row' + (item.reference === state.selected ? ' is-selected' : '') +
        '" data-map-report="' + e(item.reference) + '" aria-pressed="' + (item.reference === state.selected) + '">' +
        '<span class="map-row-icon ' + (item.kind === 'LOST' ? 'is-lost' : 'is-found') + '"><svg class="icon" aria-hidden="true"><use href="/assets/icons.svg#' +
        category.icon + '"></use></svg></span><span class="map-row-copy"><strong>' + e(item.title) + '</strong><p>' + e(item.location) + '</p>' +
        '<small>' + e(distance) + (pinned ? 'Pinned location' : 'Pin not provided') + ' · ' + e(LF.timeAgo(item.createdAt)) +
        (item.status === 'RESOLVED' ? ' · Returned' : '') + '</small></span></button>';
    }).join('');
    list.querySelectorAll('[data-map-report]').forEach(function (button) {
      button.addEventListener('click', function () { select(button.dataset.mapReport, true); });
    });
  }
  /* With no pinned reports the map is just streets, which reads as broken.
     Say why, and how to add one. */
  function paintEmptyHint(show) {
    var host = document.querySelector('[data-map]');
    var hint = host && host.parentElement.querySelector('[data-map-empty]');
    if (!host) return;
    if (!show) { if (hint) hint.remove(); return; }
    if (hint) return;
    hint = document.createElement('div');
    hint.setAttribute('data-map-empty', '');
    hint.setAttribute('role', 'status');
    hint.style.cssText = 'position:absolute;left:50%;top:50%;transform:translate(-50%,-50%);z-index:500;max-width:20rem;' +
      'padding:1rem 1.25rem;border-radius:1rem;text-align:center;background:var(--color-surface,#fff);' +
      'color:var(--color-body,#334155);box-shadow:0 10px 30px rgba(0,0,0,.25);font-size:.875rem;line-height:1.4';
    hint.innerHTML = '<strong style="display:block;margin-bottom:.25rem">No pinned reports to show</strong>' +
      'None of the reports matching these filters has a map pin yet. Drop a pin when you ' +
      '<a href="/report.html" class="text-brand">post an item</a>, or add one to your own post from its page.';
    if (getComputedStyle(host.parentElement).position === 'static') host.parentElement.style.position = 'relative';
    host.parentElement.appendChild(hint);
  }
  function paint() {
    var items = visible(), selected = items.find(function (item) { return item.reference === state.selected; });
    if (!selected && state.selected) { state.selected = null; setReference(null); }
    if (layer) layer.clearLayers(); markers.clear();
    items.filter(geo.hasPin).forEach(function (item) {
      if (!map) return;
      var marker = L.marker([item.latitude, item.longitude], { icon: geo.icon(item.kind, item.reference === state.selected), title: item.title })
        .bindPopup('<strong>' + e(item.title) + '</strong><p>' + e(item.location) + '</p>' +
          '<a href="/item.html?ref=' + encodeURIComponent(item.reference) + '#clues">View report &amp; clues</a>',
          { maxWidth: 250, autoPanPaddingTopLeft: [12, 85], autoPanPaddingBottomRight: [12, 100] });
      marker.on('click', function () { select(item.reference, false); });
      marker.addTo(layer); markers.set(item.reference, marker);
    });
    var active = state.items.filter(function (item) { return item.status !== 'RESOLVED'; });
    document.querySelector('[data-map-lost]').textContent = active.filter(function (item) { return item.kind === 'LOST'; }).length;
    document.querySelector('[data-map-found]').textContent = active.filter(function (item) { return item.kind === 'FOUND'; }).length;
    document.querySelector('[data-map-pinned]').textContent = active.filter(geo.hasPin).length;
    paintEmptyHint(state.loaded && items.filter(geo.hasPin).length === 0);
    paintSelection(selected); paintList(); updateStatus();
  }
  function load() {
    if (state.loading) return;
    state.loading = true; document.querySelector('[data-map-refresh]').disabled = true;
    updateStatus('Refreshing reports…');
    var records = [];
    function page(index) {
      return LF.api.get('/api/items?size=60&sort=recent&page=' + index).then(function (result) {
        records = records.concat(result.content);
        return result.last || index + 1 >= result.totalPages ? records : page(index + 1);
      });
    }
    page(0).then(function (items) {
      state.items = items; state.loaded = true;
      var selected = items.find(function (item) { return item.reference === state.selected; });
      if (selected && selected.status === 'RESOLVED') {
        state.resolved = true; document.querySelector('[data-map-resolved]').checked = true;
      }
      paint();
      if (firstLoad && selected && geo.hasPin(selected)) select(selected.reference, true);
      firstLoad = false;
    }).catch(function (err) {
      updateStatus(err.message + ' Use Refresh to try again.');
      if (!state.items.length) list.innerHTML = '<p class="map-list-empty">Could not load reports. Use Refresh to try again.</p>';
    }).finally(function () { state.loading = false; document.querySelector('[data-map-refresh]').disabled = false; });
  }
  document.addEventListener('lf:ready', function () {
    try {
      map = geo.create(document.querySelector('[data-map]'), { onTileError: function () { state.tileError = true; updateStatus(); } });
      layer = L.layerGroup().addTo(map);
    } catch (err) { state.tileError = true; updateStatus(err.message); }
    document.querySelectorAll('[data-map-kind]').forEach(function (button) {
      button.addEventListener('click', function () {
        state.kind = button.dataset.mapKind;
        document.querySelectorAll('[data-map-kind]').forEach(function (b) { b.setAttribute('aria-pressed', b === button ? 'true' : 'false'); });
        paint();
      });
    });
    document.querySelector('[data-map-search]').addEventListener('input', function (ev) { state.query = ev.target.value.trim().toLowerCase(); paint(); });
    document.querySelector('[data-map-resolved]').addEventListener('change', function (ev) { state.resolved = ev.target.checked; paint(); });
    document.querySelector('[data-map-refresh]').addEventListener('click', load);
    document.querySelector('[data-map-campus]').addEventListener('click', function () { if (map) map.setView([geo.campus.lat, geo.campus.lng], geo.campus.zoom); });
    document.querySelector('[data-map-fit]').addEventListener('click', function () {
      var pins = visible().filter(geo.hasPin);
      if (!pins.length) { LF.toast.info('No pins match these filters yet.'); return; }
      if (map) map.fitBounds(L.latLngBounds(pins.map(function (item) { return [item.latitude, item.longitude]; })), { padding: [60, 75], maxZoom: 18 });
    });
    document.querySelector('[data-map-locate]').addEventListener('click', function (ev) {
      var button = ev.currentTarget; button.disabled = true; updateStatus('Finding your location…');
      geo.locate().then(function (position) {
        state.origin = position;
        if (map) {
          if (userMarker) map.removeLayer(userMarker); if (accuracy) map.removeLayer(accuracy);
          accuracy = L.circle([position.latitude, position.longitude], { radius: Math.min(position.accuracy, 500), color: '#2563eb', fillOpacity: 0.06, weight: 1 }).addTo(map);
          userMarker = L.circleMarker([position.latitude, position.longitude], { radius: 7, color: '#fff', weight: 3, fillColor: '#2563eb', fillOpacity: 1 })
            .bindTooltip('You are here', { permanent: true, direction: 'top' }).addTo(map);
          map.setView([position.latitude, position.longitude], 17);
        }
        paintList(); paintSelection(visible().find(function (item) { return item.reference === state.selected; }));
        updateStatus('Distances are approximate, in a straight line. Use Walking directions to follow a route.');
      }).catch(function (err) { updateStatus(err.message); }).finally(function () { button.disabled = false; });
    });
    load();
    setInterval(function () { if (!document.hidden) load(); }, 30000);
  });
})();
