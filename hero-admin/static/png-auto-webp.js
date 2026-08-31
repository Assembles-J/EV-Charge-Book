(function(){
  const PNG_RE=/\.png$/i;

  function isPngName(value){
    return PNG_RE.test(String(value||'').trim());
  }

  function decorateRow(row){
    const name=row.querySelector('.file-name')?.textContent||'';
    if(!isPngName(name)){
      row.removeAttribute('data-auto-convert-webp');
      row.querySelector('.png-webp-note')?.remove();
      return;
    }

    row.setAttribute('data-auto-convert-webp','png-to-webp');
    const facts=row.querySelector('.file-facts');
    if(facts&&!facts.querySelector('.png-webp-note')){
      const note=document.createElement('span');
      note.className='png-webp-note';
      note.textContent=`${facts.textContent?' · ':''}PNG → WebP（发布时自动转换）`;
      facts.appendChild(note);
    }
  }

  function decorateQueue(root){
    root.querySelectorAll('.queue-row').forEach(decorateRow);
  }

  globalThis.__evPngAutoWebpTest={isPngName};
  if(typeof document==='undefined')return;

  const root=document.getElementById('resourceQueue');
  if(!root)return;

  decorateQueue(root);
  const observer=new MutationObserver(()=>decorateQueue(root));
  observer.observe(root,{childList:true,subtree:true});
})();
