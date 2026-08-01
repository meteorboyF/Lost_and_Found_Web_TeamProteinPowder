(function (global) {
  'use strict';
  var LF = (global.LF = global.LF || {});
  var labels = {'in-storage':'In Storage','match-suggested':'Match Suggested','claim-pending':'Claim Pending','self-closed':'Self-Closed','reported':'Reported','verified':'Verified','returned':'Returned','archived':'Archived','disposed':'Disposed / Donated','disputed':'Disputed'};
  var icons = {'in-storage':'storage','match-suggested':'match','claim-pending':'pending','self-closed':'self-closed','reported':'reported','verified':'verified','returned':'returned','archived':'archived','disposed':'disposed','disputed':'disputed'};
  function esc(value) { var el=document.createElement('span'); el.textContent=String(value||''); return el.innerHTML; }
  function pathPrefix() { return location.pathname.indexOf('/pages/') !== -1 ? '' : 'pages/'; }
  function imagePath(src) { return LF.root + src; }
  function render(item) {
    var found=item.kind==='found'; var o=item.obscure||{};
    return '<article class="card item-card" data-item-id="'+esc(item.id)+'">'+
      '<div class="card__media"><img src="'+esc(imagePath(item.image))+'" alt="" loading="lazy" data-media="optional"><span class="media-placeholder" data-media="placeholder">Image withheld to reduce data</span>'+
      (found?'<span class="obscured" aria-hidden="true" style="--obscure-x:'+Number(o.x||36)+'%;--obscure-y:'+Number(o.y||50)+'%;--obscure-w:'+Number(o.w||30)+'%;--obscure-h:'+Number(o.h||20)+'%"></span>':'')+
      '<div class="card__media-badges"><span class="kind-tag kind-tag--'+esc(item.kind)+'">'+esc(item.kind)+'</span></div></div>'+
      '<div class="card__body"><div class="card__meta"><span>'+esc(item.id)+'</span><span>'+esc(item.category)+'</span></div><h3 class="card__title"><a class="card__link" href="'+pathPrefix()+'item.html?id='+encodeURIComponent(item.id)+'">'+esc(item.title)+'</a></h3><p class="meta">'+esc(item.buildingName)+' · '+esc(item.dateLabel)+'</p></div>'+
      '<div class="card__footer"><span class="status-chip" data-status="'+esc(item.status)+'"><svg class="icon" aria-hidden="true"><use href="#i-st-'+esc(icons[item.status]||'reported')+'"></use></svg><span class="status-chip__label">'+esc(labels[item.status]||item.status)+'</span></span></div></article>';
  }
  LF.itemCard={render:render,escape:esc,statusLabel:function(s){return labels[s]||s;},statusIcon:function(s){return icons[s]||'reported';}};
})(window);
