/** Shared map tools. UIU coordinates come from the campus's supplied Maps link. */
(function () {
  'use strict';
  var LF = window.LF;
  var campus = { name: 'United International University', lat: 23.7978829, lng: 90.4497100, zoom: 17 };
  function hasPin(item) {
    return item && typeof item.latitude === 'number' && typeof item.longitude === 'number' &&
      Number.isFinite(item.latitude) && Number.isFinite(item.longitude) &&
      Math.abs(item.latitude) <= 90 && Math.abs(item.longitude) <= 180;
  }
  function coordinate(value) { return Number(value).toFixed(6); }
  function directions(item, origin) {
    var url = 'https://www.google.com/maps/dir/?api=1&travelmode=walking&destination=' + encodeURIComponent(item.latitude + ',' + item.longitude);
    if (origin) url += '&origin=' + encodeURIComponent(origin.latitude + ',' + origin.longitude);
    return url;
  }
  function icon(kind, selected) {
    return L.divIcon({ className: 'report-pin-root', iconSize: [38, 48], iconAnchor: [19, 43], popupAnchor: [0, -40],
      html: '<span class="report-pin ' + (kind === 'FOUND' ? 'report-pin-found' : 'report-pin-lost') + (selected ? ' is-selected' : '') +
        '"><svg viewBox="0 0 24 24" aria-hidden="true"><use href="/assets/icons.svg#' + (kind === 'FOUND' ? 'i-check' : 'i-search') + '"></use></svg></span>' });
  }
  function create(container, options) {
    options = options || {};
    if (!window.L) throw new Error('The map could not load. Refresh to try again.');
    var map = L.map(container, { scrollWheelZoom: false, zoomControl: false, maxZoom: 20 })
      .setView(options.point || [campus.lat, campus.lng], options.zoom || campus.zoom);
    map.attributionControl.setPrefix(false);
    L.tileLayer('https://tile.openstreetmap.org/{z}/{x}/{y}.png', { maxNativeZoom: 19, maxZoom: 20,
      attribution: '&copy; <a href="https://www.openstreetmap.org/copyright" target="_blank" rel="noopener noreferrer">OpenStreetMap</a>'
    }).on('tileerror', function () { if (options.onTileError) options.onTileError(); }).addTo(map);
    L.control.zoom({ position: 'bottomright' }).addTo(map);
    L.marker([campus.lat, campus.lng], { interactive: false, keyboard: false,
      icon: L.divIcon({ className: 'campus-label', html: '<span>UIU</span>', iconSize: [44, 24], iconAnchor: [22, 12] }) }).addTo(map);
    if (window.ResizeObserver) {
      var observer = new ResizeObserver(function () { map.invalidateSize(); });
      observer.observe(container);
      map.on('unload', function () { observer.disconnect(); });
    }
    return map;
  }
  function locate() {
    return new Promise(function (resolve, reject) {
      if (!navigator.geolocation) return reject(new Error('Location is unavailable. Choose a spot on the map.'));
      navigator.geolocation.getCurrentPosition(function (position) {
        resolve({ latitude: position.coords.latitude, longitude: position.coords.longitude, accuracy: position.coords.accuracy });
      }, function (error) { reject(new Error(error.code === 1 ? 'Location permission was declined. Place a pin yourself.'
        : 'Could not get your location. Choose a spot on the map.')); }, { enableHighAccuracy: true, timeout: 10000, maximumAge: 30000 });
    });
  }
  function distance(from, to) {
    var rad = Math.PI / 180, dLat = (to.latitude - from.latitude) * rad, dLng = (to.longitude - from.longitude) * rad;
    var a = Math.sin(dLat / 2) ** 2 + Math.cos(from.latitude * rad) * Math.cos(to.latitude * rad) * Math.sin(dLng / 2) ** 2;
    return 6371000 * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
  }
  function distanceLabel(metres) { return metres < 1000 ? Math.round(metres / 10) * 10 + ' m' : (metres / 1000).toFixed(1) + ' km'; }

  function picker(host, initial, options) {
    initial = initial || {}; options = options || {};
    var uid = options.id || 'report-location', map, marker, circle;
    var point = hasPin(initial) ? initial : null;
    host.innerHTML = '<div class="pin-picker-tools">' +
      '<button type="button" class="btn btn-secondary btn-sm" data-pin-locate>Use my location</button>' +
      '<button type="button" class="btn btn-ghost btn-sm" data-pin-campus>Back to UIU</button>' +
      '<button type="button" class="btn btn-ghost btn-sm" data-pin-clear>Clear pin</button></div>' +
      '<div class="geo-map picker-map" data-pin-map aria-label="Click the reported location to place a pin"></div>' +
      '<p class="pin-readout" data-pin-status role="status" aria-live="polite">Click a spot on the map to place your pin.</p>' +
      '<div class="pin-picker-fields"><div><label for="' + uid + '-radius" class="label">How wide should people search?</label>' +
      '<select id="' + uid + '-radius" name="searchRadiusMeters" class="field" data-pin-radius>' +
      [10, 25, 50, 100, 200, 500].map(function (radius) { return '<option value="' + radius + '">Within ' + radius + ' metres</option>'; }).join('') +
      '</select></div><details class="pin-coordinate-details"><summary>Enter coordinates instead</summary>' +
      '<div class="pin-coordinate-grid"><div><label for="' + uid + '-lat" class="label">Latitude</label>' +
      '<input id="' + uid + '-lat" name="latitude" type="number" min="-90" max="90" step="any" class="field" placeholder="23.797883" data-pin-lat></div>' +
      '<div><label for="' + uid + '-lng" class="label">Longitude</label>' +
      '<input id="' + uid + '-lng" name="longitude" type="number" min="-180" max="180" step="any" class="field" placeholder="90.449710" data-pin-lng></div></div></details></div>' +
      '<p class="text-xs text-muted mt-3">Your selected spot and search area are public. Add floor, room or landmark details in the location description.</p>' +
      '<p class="min-h-5 text-sm text-lost" data-error="coordinatesPaired"></p>' +
      '<p class="text-sm text-lost" data-error="latitude"></p><p class="text-sm text-lost" data-error="longitude"></p>';
    var status = host.querySelector('[data-pin-status]'), lat = host.querySelector('[data-pin-lat]'), lng = host.querySelector('[data-pin-lng]');
    var radius = host.querySelector('[data-pin-radius]'); radius.value = String(initial.searchRadiusMeters || 25);
    if (!radius.value) radius.value = '25';
    try { map = create(host.querySelector('[data-pin-map]'), { point: point ? [point.latitude, point.longitude] : null,
      onTileError: function () { status.textContent = 'Map imagery could not load. You can enter coordinates below.'; } }); }
    catch (err) { status.textContent = err.message; }
    function paint(fly) {
      if (marker && map) { map.removeLayer(marker); marker = null; }
      if (circle && map) { map.removeLayer(circle); circle = null; }
      if (!point) { status.textContent = 'No pin selected. Click the reported spot on the map.'; return; }
      status.textContent = 'Pin placed at ' + coordinate(point.latitude) + ', ' + coordinate(point.longitude) + '. Drag it to adjust.';
      if (!map) return;
      var coords = [point.latitude, point.longitude];
      circle = L.circle(coords, { radius: Number(radius.value), color: '#6366f1', fillOpacity: 0.10, weight: 1.5 }).addTo(map);
      marker = L.marker(coords, { draggable: true, icon: icon(options.kind || 'LOST', true), title: 'Reported spot. Drag to adjust.' }).addTo(map);
      marker.on('dragend', function () { var position = marker.getLatLng(); setPoint(position.lat, position.lng, false); });
      // Editors can be saved immediately after a coordinate change. Avoid a pending
      // zoom transition trying to finish after the editor map has been disposed.
      if (fly) map.setView(coords, Math.max(map.getZoom(), 18), { animate: false });
    }
    function setPoint(latitude, longitude, fly) {
      point = { latitude: Number(coordinate(latitude)), longitude: Number(coordinate(longitude)) };
      lat.value = coordinate(point.latitude); lng.value = coordinate(point.longitude);
      host.querySelector('[data-error="coordinatesPaired"]').textContent = ''; paint(fly);
    }
    if (map) map.on('click', function (ev) { setPoint(ev.latlng.lat, ev.latlng.lng, false); });
    radius.addEventListener('change', function () { paint(false); });
    function manual() {
      if (!lat.value && !lng.value) { point = null; paint(false); return; }
      var entered = { latitude: lat.value ? Number(lat.value) : NaN, longitude: lng.value ? Number(lng.value) : NaN };
      if (hasPin(entered)) setPoint(entered.latitude, entered.longitude, true);
      else status.textContent = 'Enter a valid latitude and longitude together.';
    }
    lat.addEventListener('change', manual); lng.addEventListener('change', manual);
    host.querySelector('[data-pin-clear]').addEventListener('click', function () { lat.value = ''; lng.value = ''; point = null; paint(false); });
    host.querySelector('[data-pin-campus]').addEventListener('click', function () { if (map) map.setView([campus.lat, campus.lng], campus.zoom); });
    host.querySelector('[data-pin-locate]').addEventListener('click', function (ev) {
      var button = ev.currentTarget; button.disabled = true; status.textContent = 'Finding your location…';
      locate().then(function (position) {
        radius.value = String([10, 25, 50, 100, 200, 500].find(function (value) { return value >= position.accuracy; }) || 500);
        setPoint(position.latitude, position.longitude, true);
      }).catch(function (err) { status.textContent = err.message; }).finally(function () { button.disabled = false; });
    });
    if (point) setPoint(point.latitude, point.longitude, false);
    return { map: map, read: function () {
      if (!lat.value && !lng.value) return { latitude: null, longitude: null, searchRadiusMeters: null };
      var entered = { latitude: lat.value ? Number(lat.value) : NaN, longitude: lng.value ? Number(lng.value) : NaN };
      if (!hasPin(entered)) throw new Error('Enter a valid latitude and longitude, or clear the pin.');
      entered.searchRadiusMeters = Number(radius.value); return entered;
    }, destroy: function () { if (map) map.remove(); } };
  }
  function preview(host, item) {
    if (!hasPin(item)) return null;
    var map = create(host, { point: [item.latitude, item.longitude], zoom: 18 });
    L.marker([item.latitude, item.longitude], { icon: icon(item.kind, true), title: item.title }).addTo(map);
    if (item.searchRadiusMeters) L.circle([item.latitude, item.longitude], { radius: item.searchRadiusMeters,
      color: item.kind === 'FOUND' ? '#047857' : '#be123c', weight: 1.5, fillOpacity: 0.10 }).addTo(map);
    return map;
  }
  LF.maps = { campus: campus, hasPin: hasPin, coordinate: coordinate, create: create, icon: icon,
    locate: locate, directions: directions, picker: picker, preview: preview, distance: distance, distanceLabel: distanceLabel };
})();
