(function(){
  if(typeof document==='undefined')return;
  const keyInput=document.getElementById('artworkKey');
  const currentRoot=document.getElementById('heroCurrent');
  const publishButton=document.getElementById('publishHero');
  if(!keyInput||!currentRoot||!publishButton)return;
  const KEY_RE=/^[a-z0-9][a-z0-9-]{1,100}$/;
  const stripVariant=value=>String(value||'').trim().toLowerCase().replace(/-(dark|light)$/,'');
  const variantKey=(base,variant)=>`${stripVariant(base)}-${variant}`;

  const label=document.querySelector('label[for="artworkKey"]');
  if(label)label.textContent='Hero 语义 Key（不含 -dark / -light）';
  keyInput.placeholder='例如 byd-seal-08-2026';

  const variantWrap=document.createElement('div');
  variantWrap.style.marginTop='12px';
  variantWrap.innerHTML='<label for="heroVariant">Hero 主题版本</label><select id="heroVariant"><option value="dark">Dark Hero</option><option value="light">Light Hero</option></select>';
  currentRoot.parentElement.insertBefore(variantWrap,currentRoot);
  const variantSelect=document.getElementById('heroVariant');

  const helper=document.createElement('p');
  helper.className='meta';
  helper.innerHTML='Catalog 只保存一个语义 Hero Key；发布时自动派生 <code>&lt;heroKey&gt;-dark</code> / <code>&lt;heroKey&gt;-light</code>。旧 base Key 只作为 Dark fallback，不再新增 legacy base 图片。';
  variantWrap.appendChild(helper);

  function semanticKeys(){
    const keys=new Set();
    for(const key of Object.keys(manifest?.artworks||{}))keys.add(stripVariant(key));
    for(const vehicle of catalog?.vehicles||[])if(vehicle.heroArtworkKey)keys.add(stripVariant(vehicle.heroArtworkKey));
    return [...keys].filter(Boolean).sort();
  }
  function heroInfo(base,variant){
    const exact=manifest?.artworks?.[variantKey(base,variant)]||null;
    const legacy=variant==='dark'&&!exact?manifest?.artworks?.[stripVariant(base)]||null:null;
    return {key:variantKey(base,variant),current:exact||legacy,legacy:Boolean(legacy)};
  }
  function card(name,value,detail=''){
    const el=document.createElement('div');el.className='current-card';
    const b=document.createElement('b');b.textContent=value;
    const s=document.createElement('span');s.textContent=detail?`${name} · ${detail}`:name;
    el.append(b,s);return el;
  }

  renderHeroKeys=function(){
    const list=document.getElementById('heroKeys');list.innerHTML='';
    semanticKeys().forEach(key=>{const option=document.createElement('option');option.value=key;list.appendChild(option)});
    renderHeroCurrent();
  };
  renderHeroCurrent=function(){
    const base=stripVariant(keyInput.value);if(keyInput.value!==base&&base)keyInput.value=base;
    const dark=base?heroInfo(base,'dark'):null,light=base?heroInfo(base,'light'):null,selected=base?heroInfo(base,variantSelect.value):null;
    currentRoot.innerHTML='';
    currentRoot.append(
      card('Dark 当前',dark?.current?`v${dark.current.version}`:'未发布',dark?.legacy?'legacy base fallback':''),
      card('Light 当前',light?.current?`v${light.current.version}`:'未发布'),
      card('本次发布 Key',selected?.key||'等待 Hero Key'),
      card('目标版本',selected?`v${Number(selected.current?.version||0)+1}`:'--')
    );
    updatePublishEnabled();
  };
  updatePublishEnabled=function(){
    const base=stripVariant(keyInput.value);publishButton.disabled=!(chosenFile&&KEY_RE.test(base));
    publishButton.textContent=base?`发布 ${variantSelect.value==='dark'?'Dark':'Light'} Hero`:'发布 Hero';
  };

  keyInput.oninput=renderHeroCurrent;
  keyInput.addEventListener('blur',()=>{keyInput.value=stripVariant(keyInput.value);renderHeroCurrent()});
  variantSelect.onchange=renderHeroCurrent;

  publishButton.onclick=async()=>{
    const base=stripVariant(keyInput.value),variant=variantSelect.value,key=variantKey(base,variant);
    if(!chosenFile||!KEY_RE.test(base))return;
    publishButton.disabled=true;setStatus(document.getElementById('heroStatus'),`正在校验、转换并发布 ${key}…`);
    const body=new FormData();body.append('artworkKey',key);body.append('file',chosenFile);
    try{
      const response=await fetch('api/publish',{method:'POST',headers:{'X-Hero-Admin-Request':'1'},body});const data=await response.json();if(!response.ok)throw new Error(data.error||'发布失败');
      manifest.artworks[data.artworkKey]={version:data.version,url:data.url};chosenFile=null;document.getElementById('file').value='';document.getElementById('drop').classList.remove('has-preview');document.getElementById('preview').removeAttribute('src');renderHeroKeys();
      setStatus(document.getElementById('heroStatus'),`发布成功：${data.artworkKey} → v${data.version} · ${data.outputWidth}×${data.outputHeight} WebP · ${(data.outputBytes/1024).toFixed(0)} KB`,'ok');
    }catch(error){setStatus(document.getElementById('heroStatus'),error.message||error,'error')}finally{updatePublishEnabled()}
  };

  globalThis.__evAdminHeroVariantTest={stripVariant,variantKey};
  renderHeroKeys();
})();
