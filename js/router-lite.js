/** Small path/query helper for this static multi-page application. */
(function (global) {
  'use strict';
  var LF = (global.LF = global.LF || {});
  LF.route = {
    query: function (name) {
      return new URLSearchParams(global.location.search).get(name);
    },
    to: function (path, params) {
      var url = new URL(path, global.location.href);
      Object.keys(params || {}).forEach(function (key) {
        if (params[key] !== '' && params[key] != null) url.searchParams.set(key, params[key]);
      });
      return url.pathname + url.search;
    }
  };
})(window);
