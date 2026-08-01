(function (global) {
  'use strict';
  LF.ready(function(){
    var form=document.getElementById('filters'), grid=document.getElementById('results'), count=document.getElementById('result-count'), sort=document.getElementById('sort'), search=document.getElementById('search'), timer;
    function selected(name){return Array.from(form.querySelectorAll('[name="'+name+'"]:checked')).map(function(x){return x.value;});}
    function positions(){var out={};grid.querySelectorAll('[data-item-id]').forEach(function(el){out[el.dataset.itemId]=el.getBoundingClientRect();});return out;}
    function load(push){
      var old=positions(), q={text:search.value,kind:form.querySelector('[name="kind"]:checked')?.value||'',category:selected('category'),building:selected('building'),sort:sort.value,perPage:50};
      LF.api.query(q).then(function(result){
        grid.innerHTML=result.rows.length?result.rows.map(LF.itemCard.render).join(''):'<div class="empty-inline"><h2>No records match</h2><p class="meta">Clear a filter or try a broader search.</p><button class="btn btn--secondary u-mt-4" type="button" data-clear-all>Clear all filters</button></div>';
        count.innerHTML='<strong>'+result.total+'</strong> record'+(result.total===1?'':'s'); LF.enhance(grid);
        grid.querySelectorAll('[data-item-id]').forEach(function(el){var before=old[el.dataset.itemId],after=el.getBoundingClientRect();if(before){el.animate([{transform:'translate('+(before.left-after.left)+'px,'+(before.top-after.top)+'px)'},{transform:'none'}],{duration:240,easing:'cubic-bezier(.2,0,0,1)'});}else{el.dataset.entering='true';requestAnimationFrame(function(){delete el.dataset.entering;});}});
        var clear=grid.querySelector('[data-clear-all]');if(clear)clear.addEventListener('click',reset);
        if(push){var p=new URLSearchParams();if(search.value)p.set('q',search.value);if(q.kind)p.set('kind',q.kind);history.replaceState(null,'',location.pathname+(p.toString()?'?'+p:''));}
      }).catch(function(){grid.innerHTML='<div class="empty-inline"><h2>Could not load records</h2><p class="meta">Serve the project over HTTP, then try again.</p></div>';count.textContent='Unavailable';});
    }
    function reset(){form.reset();search.value='';sort.value='recent';load(true);}
    form.addEventListener('change',function(){load(true);});form.addEventListener('reset',function(){setTimeout(function(){load(true);},0);});sort.addEventListener('change',function(){load(true);});search.addEventListener('input',function(){clearTimeout(timer);timer=setTimeout(function(){load(true);},180);});
    var params=new URLSearchParams(location.search);search.value=params.get('q')||'';var kind=params.get('kind');if(kind){var radio=form.querySelector('[name="kind"][value="'+CSS.escape(kind)+'"]');if(radio)radio.checked=true;}load(false);
  });
})(window);
