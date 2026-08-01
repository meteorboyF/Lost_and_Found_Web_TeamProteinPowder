(function (global) {
  'use strict';
  LF.ready(function () {
    var recent=document.querySelector('[data-recent-items]');
    LF.api.items().then(function(items){
      var found=items.filter(function(i){return i.kind==='found';}).slice(0,3);
      recent.innerHTML=found.map(LF.itemCard.render).join(''); LF.enhance(recent);
      animateCounts(items);
    }).catch(function(){ recent.innerHTML='<div class="empty-inline"><h3>Registry data unavailable</h3><p class="meta">Run this project from a local static server to load live records.</p></div>'; });
    function animateCounts(items){
      var values={records:items.length,stored:items.filter(function(i){return i.status==='in-storage';}).length,returned:items.filter(function(i){return i.status==='returned';}).length};
      document.querySelectorAll('[data-count]').forEach(function(el){var end=values[el.dataset.count]||0,start=performance.now(),reduced=matchMedia('(prefers-reduced-motion: reduce)').matches; function frame(now){var p=reduced?1:Math.min(1,(now-start)/700);el.textContent=Math.round(end*p);if(p<1)requestAnimationFrame(frame);}requestAnimationFrame(frame);});
    }
  });
})(window);
