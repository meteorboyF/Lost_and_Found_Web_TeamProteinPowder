/**
 * store.js — the persistence layer.
 *
 * There is no backend. Everything a user does is written to localStorage
 * under a single namespaced key so a reload replays their real state:
 * claims they opened, items they saved, threads they read, theme, language.
 *
 * Exposed as window.LF.store. No modules, because the pages must also work
 * when opened straight off the filesystem, where ES module imports are
 * blocked by CORS.
 */
(function (global) {
  'use strict';

  var NS = 'lf.v1';

  /* Some browsers throw on localStorage access in private mode. Everything
     degrades to an in-memory map rather than breaking the page. */
  var memory = {};
  var available = (function () {
    try {
      var probe = NS + '.probe';
      global.localStorage.setItem(probe, '1');
      global.localStorage.removeItem(probe);
      return true;
    } catch (err) {
      return false;
    }
  })();

  function rawGet(key) {
    if (!available) return Object.prototype.hasOwnProperty.call(memory, key) ? memory[key] : null;
    return global.localStorage.getItem(key);
  }

  function rawSet(key, value) {
    if (!available) {
      memory[key] = value;
      return;
    }
    try {
      global.localStorage.setItem(key, value);
    } catch (err) {
      /* Quota exceeded — fall back to memory for this key. */
      memory[key] = value;
    }
  }

  function rawRemove(key) {
    delete memory[key];
    if (available) global.localStorage.removeItem(key);
  }

  function fullKey(key) {
    return NS + '.' + key;
  }

  /* ---------------------------------------------------------------------
     Subscriptions. Components re-render when the slice they care about
     changes, including from another tab via the storage event.
     --------------------------------------------------------------------- */

  var listeners = {};

  function emit(key, value) {
    (listeners[key] || []).forEach(function (fn) {
      try {
        fn(value, key);
      } catch (err) {
        if (global.console) console.error('[store] listener failed for', key, err);
      }
    });
    (listeners['*'] || []).forEach(function (fn) {
      fn(value, key);
    });
  }

  var store = {
    /** Read a namespaced value, parsed from JSON. */
    get: function (key, fallback) {
      var raw = rawGet(fullKey(key));
      if (raw === null || raw === undefined) {
        return fallback === undefined ? null : fallback;
      }
      try {
        return JSON.parse(raw);
      } catch (err) {
        return fallback === undefined ? null : fallback;
      }
    },

    /** Write a namespaced value as JSON and notify subscribers. */
    set: function (key, value) {
      rawSet(fullKey(key), JSON.stringify(value));
      emit(key, value);
      return value;
    },

    remove: function (key) {
      rawRemove(fullKey(key));
      emit(key, null);
    },

    /** Read-modify-write in one call, so callers never race themselves. */
    update: function (key, fn, fallback) {
      var next = fn(store.get(key, fallback));
      return store.set(key, next);
    },

    /** Append to an array slice, keeping it unique by `idKey` if given. */
    push: function (key, entry, idKey) {
      return store.update(key, function (list) {
        var arr = Array.isArray(list) ? list.slice() : [];
        if (idKey) {
          arr = arr.filter(function (item) {
            return item[idKey] !== entry[idKey];
          });
        }
        arr.push(entry);
        return arr;
      }, []);
    },

    /** Remove from an array slice by predicate. */
    pull: function (key, predicate) {
      return store.update(key, function (list) {
        return (Array.isArray(list) ? list : []).filter(function (item, i) {
          return !predicate(item, i);
        });
      }, []);
    },

    /** Toggle membership of a scalar in an array slice. Returns the new state. */
    toggle: function (key, value) {
      var present = false;
      store.update(key, function (list) {
        var arr = Array.isArray(list) ? list.slice() : [];
        var i = arr.indexOf(value);
        if (i === -1) {
          arr.push(value);
          present = true;
        } else {
          arr.splice(i, 1);
        }
        return arr;
      }, []);
      return present;
    },

    has: function (key, value) {
      var list = store.get(key, []);
      return Array.isArray(list) && list.indexOf(value) !== -1;
    },

    /** Subscribe to a key, or '*' for everything. Returns an unsubscribe fn. */
    subscribe: function (key, fn) {
      if (!listeners[key]) listeners[key] = [];
      listeners[key].push(fn);
      return function () {
        listeners[key] = listeners[key].filter(function (f) {
          return f !== fn;
        });
      };
    },

    /** Wipe every key in our namespace, leaving other apps' data alone. */
    clear: function () {
      if (available) {
        Object.keys(global.localStorage)
          .filter(function (k) {
            return k.indexOf(NS + '.') === 0;
          })
          .forEach(function (k) {
            global.localStorage.removeItem(k);
          });
      }
      memory = {};
      emit('*', null);
    },

    /** True when persistence is real rather than in-memory. */
    persistent: available,

    namespace: NS
  };

  /* Cross-tab sync. */
  if (available && global.addEventListener) {
    global.addEventListener('storage', function (event) {
      if (!event.key || event.key.indexOf(NS + '.') !== 0) return;
      var key = event.key.slice(NS.length + 1);
      var value = null;
      if (event.newValue) {
        try {
          value = JSON.parse(event.newValue);
        } catch (err) {
          value = null;
        }
      }
      emit(key, value);
    });
  }

  global.LF = global.LF || {};
  global.LF.store = store;
})(window);
