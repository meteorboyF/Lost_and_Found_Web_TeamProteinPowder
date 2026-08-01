/**
 * api.js — the data layer.
 *
 * Fixtures live in /data/*.json and are read with fetch(). Every read goes
 * through here so pages never touch a URL directly, and so the loading and
 * error states have exactly one place to originate.
 *
 * Opened from the filesystem, fetch() on a file:// URL is blocked. That is a
 * supported degradation: the promise rejects with a typed error and views
 * render their error state with a "run a local server" explanation rather
 * than an empty page.
 *
 * Exposed as window.LF.api.
 */
(function (global) {
  'use strict';

  var cache = {};
  var inflight = {};

  var IS_FILE = global.location.protocol === 'file:';

  /* Resolve /data/*.json relative to this script, so a page at /pages/admin/
     and a page at / both land on the same file without hard-coded ../../. */
  var BASE = (function () {
    var scripts = document.getElementsByTagName('script');
    for (var i = scripts.length - 1; i >= 0; i--) {
      var src = scripts[i].src || '';
      var at = src.lastIndexOf('/js/');
      if (at !== -1) return src.slice(0, at + 1);
    }
    return '';
  })();

  function ApiError(message, kind) {
    this.name = 'ApiError';
    this.message = message;
    this.kind = kind || 'network';
  }
  ApiError.prototype = Object.create(Error.prototype);
  ApiError.prototype.constructor = ApiError;

  /** Artificial latency so the skeleton states are actually reachable in a
      demo. Set LF.api.latency = 0 to turn it off. */
  function delay(ms) {
    return new Promise(function (resolve) {
      global.setTimeout(resolve, ms);
    });
  }

  function load(name) {
    if (cache[name]) return Promise.resolve(cache[name]);
    if (inflight[name]) return inflight[name];

    if (IS_FILE) {
      return Promise.reject(
        new ApiError(
          'Fixture data cannot be read from the filesystem. Serve the project over HTTP.',
          'file-protocol'
        )
      );
    }

    var url = BASE + 'data/' + name + '.json';

    inflight[name] = fetch(url, { headers: { Accept: 'application/json' } })
      .then(function (res) {
        if (!res.ok) {
          throw new ApiError('Request for ' + name + ' failed (' + res.status + ')', 'http');
        }
        return res.json();
      })
      .then(function (json) {
        return delay(api.latency).then(function () {
          cache[name] = json;
          delete inflight[name];
          return json;
        });
      })
      .catch(function (err) {
        delete inflight[name];
        if (err instanceof ApiError) throw err;
        throw new ApiError('Could not reach ' + url, 'network');
      });

    return inflight[name];
  }

  /* ---------------------------------------------------------------------
     Query helpers. These operate on the loaded fixture in memory — the
     shape a real endpoint would return, so swapping in a server later means
     changing only this file.
     --------------------------------------------------------------------- */

  function normalise(value) {
    return String(value == null ? '' : value).toLowerCase().trim();
  }

  /** Simple relevance scoring for the free-text search on browse. */
  function score(item, terms) {
    if (!terms.length) return 1;
    var haystack = normalise(
      [item.title, item.category, item.description, item.buildingName, item.colour]
        .filter(Boolean)
        .join(' ')
    );
    var hits = 0;
    for (var i = 0; i < terms.length; i++) {
      if (haystack.indexOf(terms[i]) !== -1) hits++;
      if (normalise(item.title).indexOf(terms[i]) !== -1) hits += 0.5;
    }
    return hits / terms.length;
  }

  var api = {
    latency: 320,

    ApiError: ApiError,

    /** Raw fixture access. */
    load: load,

    items: function () {
      return load('items').then(function (d) { return d.items || []; });
    },
    users: function () {
      return load('users').then(function (d) { return d.users || []; });
    },
    claims: function () {
      return load('claims').then(function (d) { return d.claims || []; });
    },
    buildings: function () {
      return load('buildings').then(function (d) { return d.buildings || []; });
    },
    notifications: function () {
      return load('notifications').then(function (d) { return d.notifications || []; });
    },

    itemById: function (id) {
      return api.items().then(function (items) {
        var found = items.filter(function (i) { return i.id === id; })[0];
        if (!found) throw new ApiError('No item with id ' + id, 'not-found');
        return found;
      });
    },

    /**
     * Faceted query used by browse.
     * @param {Object} q  { text, kind, category, building, status, sort, page, perPage }
     */
    query: function (q) {
      q = q || {};
      return api.items().then(function (items) {
        var terms = normalise(q.text).split(/\s+/).filter(Boolean);

        var rows = items.filter(function (item) {
          if (q.kind && item.kind !== q.kind) return false;
          if (q.category && q.category.length && q.category.indexOf(item.category) === -1) return false;
          if (q.building && q.building.length && q.building.indexOf(item.buildingId) === -1) return false;
          if (q.status && q.status.length && q.status.indexOf(item.status) === -1) return false;
          if (terms.length && score(item, terms) === 0) return false;
          return true;
        });

        var sort = q.sort || 'recent';
        rows.sort(function (a, b) {
          if (sort === 'relevance' && terms.length) return score(b, terms) - score(a, terms);
          if (sort === 'oldest') return a.reportedAt.localeCompare(b.reportedAt);
          if (sort === 'title') return a.title.localeCompare(b.title);
          return b.reportedAt.localeCompare(a.reportedAt);
        });

        var perPage = q.perPage || 12;
        var page = Math.max(1, q.page || 1);
        var pages = Math.max(1, Math.ceil(rows.length / perPage));

        return {
          total: rows.length,
          page: Math.min(page, pages),
          pages: pages,
          perPage: perPage,
          rows: rows.slice((Math.min(page, pages) - 1) * perPage, Math.min(page, pages) * perPage)
        };
      });
    },

    /** Reset the in-memory cache. Used by the styleguide's state switcher. */
    invalidate: function (name) {
      if (name) delete cache[name];
      else cache = {};
    },

    isFileProtocol: IS_FILE
  };

  global.LF = global.LF || {};
  global.LF.api = api;
})(window);
