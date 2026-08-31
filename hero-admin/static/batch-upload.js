(function(){
  const IMAGE_RE=/\.(png|webp)$/i;

  function normalizeToken(value){
    return String(value||'')
      .trim()
      .toLowerCase()
      .replace(/\.(png|webp)$/i,'')
      .replace(/[\s_]+/g,'-')
      .replace(/[^a-z0-9-]+/g,'-')
      .replace(/-+/g,'-')
      .replace(/^-|-$/g,'');
  }

  function imageFiles(files){
    return Array.from(files||[]).filter(file=>IMAGE_RE.test(file.name)||['image/png','image/webp'].includes(file.type));
  }

  function heroTargets(){
    const keys=new Set(Object.keys((manifest&&manifest.artworks)||{}));
    for(const vehicle of (catalog&&catalog.vehicles)||[]){
      if(vehicle.heroArtworkKey)keys.add(vehicle.heroArtworkKey);
    }
    return [...keys].map(key=>({id:key,key,aliases:[normalizeToken(key)]}));
  }

  function brandTargets(){
    return ((catalog&&catalog.brands)||[]).map(brand=>{
      const aliases=new Set([
        normalizeToken(brand.brandId),
        normalizeToken(brand.logoKey),
        normalizeToken(`brand-${brand.brandId}`)
      ].filter(Boolean));
      return {id:brand.brandId,brand,aliases:[...aliases]};
    });
  }

  function matchByLongestPrefix(fileName,targets){
    const stem=normalizeToken(fileName);
    let best=null;
    for(const target of targets){
      for(const alias of target.aliases){
        if(!alias||!stem.startsWith(alias))continue;
        if(!best||alias.length>best.alias.length)best={target,alias,stem};
      }
    }
    return best;
  }

  function planBatch(files,targets,targetLabel){
    const seen=new Set();
    return imageFiles(files).map((file,index)=>{
      const matched=matchByLongestPrefix(file.name,targets);
      if(!matched)return {file,index,state:'unmatched',message:`未找到${targetLabel}前缀`};
      const id=matched.target.id;
      if(seen.has(id))return {file,index,state:'duplicate',target:matched.target,message:`同一${targetLabel}已有更早文件，按“最先上传优先”跳过`};
      seen.add(id);
      return {file,index,state:'ready',target:matched.target,message:`匹配 ${id}`};
    });
  }

  globalThis.__evBatchUploadTest={normalizeToken,matchByLongestPrefix,planBatch};
  if(typeof document==='undefined')return;

  function createQueueRows(root,plan){
    root.innerHTML='';
    if(!plan.length){root.innerHTML='<div class="batch-empty">尚未选择图片。</div>';return}
    for(const item of plan){
      const row=document.createElement('div');row.className=`batch-row ${item.state}`;
      const name=document.createElement('div');name.className='batch-file';name.textContent=item.file.name;
      const target=document.createElement('div');target.className='batch-target';target.textContent=item.target?.id||'未匹配';
      const status=document.createElement('div');status.className='batch-state';status.textContent=item.message;
      row.append(name,target,status);root.appendChild(row);
    }
  }

  function makeDropZone({id,title,description,acceptLabel,onFiles}){
    const zone=document.createElement('div');zone.id=id;zone.className='batch-drop';zone.tabIndex=0;zone.setAttribute('role','button');
    const input=document.createElement('input');input.type='file';input.multiple=true;input.accept='image/png,image/webp,.png,.webp';input.className='hidden';
    zone.innerHTML=`<strong>${title}</strong><span>${description}</span><small>${acceptLabel||'支持 PNG / WebP，多选或拖拽'}</small>`;
    zone.appendChild(input);
    const choose=()=>input.click();
    zone.addEventListener('click',event=>{if(event.target!==input)choose()});
    zone.addEventListener('keydown',event=>{if(event.key==='Enter'||event.key===' '){event.preventDefault();choose()}});
    input.addEventListener('change',()=>{onFiles(Array.from(input.files||[]));input.value=''});
    ['dragenter','dragover'].forEach(type=>zone.addEventListener(type,event=>{event.preventDefault();zone.classList.add('dragging')}));
    ['dragleave','drop'].forEach(type=>zone.addEventListener(type,event=>{event.preventDefault();zone.classList.remove('dragging')}));
    zone.addEventListener('drop',event=>onFiles(Array.from(event.dataTransfer?.files||[])));
    return zone;
  }

  function injectBrandBatchUi(){
    const assetCenter=document.querySelector('#brandsPanel .asset-center');
    if(!assetCenter||$('brandBatchUploader'))return;
    const section=document.createElement('section');section.id='brandBatchUploader';section.className='batch-center';
    section.innerHTML=`
      <div class="batch-head">
        <div>
          <div class="eyebrow">BATCH BRAND LOGO</div>
          <h3>品牌 Logo 批量上传</h3>
          <p class="meta">按文件名前缀自动匹配 <code>brandId / logoKey</code>。Light / Dark 分开拖入，同品牌多张时最先进入队列的文件优先，其余跳过。</p>
        </div>
      </div>
      <div class="batch-drop-grid" id="brandBatchDrops"></div>
      <div class="batch-summary" id="brandBatchSummary">等待图片…</div>
      <div class="batch-queue" id="brandBatchQueue"><div class="batch-empty">尚未选择图片。</div></div>
      <div class="batch-actions"><button id="publishBrandBatch" disabled>批量发布匹配 Logo</button></div>`;
    assetCenter.appendChild(section);

    let lightFiles=[],darkFiles=[],plan=[];
    const rebuild=()=>{
      const light=planBatch(lightFiles,brandTargets(),'品牌').map(item=>({...item,variant:'light',message:`Light · ${item.message}`}));
      const dark=planBatch(darkFiles,brandTargets(),'品牌').map(item=>({...item,variant:'dark',message:`Dark · ${item.message}`}));
      plan=[...light,...dark];
      createQueueRows($('brandBatchQueue'),plan);
      const ready=plan.filter(item=>item.state==='ready').length;
      const skipped=plan.length-ready;
      $('brandBatchSummary').textContent=`已分析 ${plan.length} 张 · 可发布 ${ready} · 跳过 ${skipped}`;
      $('publishBrandBatch').disabled=!ready;
    };

    const drops=$('brandBatchDrops');
    drops.appendChild(makeDropZone({id:'brandLightBatchDrop',title:'Light Logo',description:'拖入浅色界面版本；文件名前缀例如 xiaomi_v2.webp',onFiles:files=>{lightFiles=files;rebuild()}}));
    drops.appendChild(makeDropZone({id:'brandDarkBatchDrop',title:'Dark Logo',description:'拖入深色界面版本；文件名前缀例如 xiaomi_final.webp',onFiles:files=>{darkFiles=files;rebuild()}}));

    $('publishBrandBatch').onclick=async()=>{
      const ready=plan.filter(item=>item.state==='ready');if(!ready.length)return;
      $('publishBrandBatch').disabled=true;
      let ok=0,failed=0;
      for(let i=0;i<ready.length;i++){
        const item=ready[i];const brand=item.target.brand;
        $('brandBatchSummary').textContent=`正在发布 ${i+1}/${ready.length} · ${brand.name} · ${item.variant==='light'?'Light':'Dark'} · ${item.file.name}`;
        const body=new FormData();body.append('brandId',brand.brandId);body.append('variant',item.variant);body.append('file',item.file);
        try{
          const response=await fetch('api/brand/logo',{method:'POST',headers:{'X-Hero-Admin-Request':'1'},body});
          const data=await response.json();if(!response.ok)throw new Error(data.error||'Logo 发布失败');
          item.state='done';item.message=`${item.variant==='light'?'Light':'Dark'} · 已发布 v${data.version}`;ok++;
        }catch(error){item.state='error';item.message=`${item.variant==='light'?'Light':'Dark'} · 失败：${error.message||error}`;failed++}
        createQueueRows($('brandBatchQueue'),plan);
      }
      await loadCatalog();
      $('brandBatchSummary').textContent=`批量 Logo 完成 · 成功 ${ok} · 失败 ${failed} · 重复/未匹配 ${plan.length-ready.length}`;
      $('publishBrandBatch').disabled=false;
    };
  }

  function injectHeroBatchUi(){
    const panel=$('heroPanel');if(!panel||$('heroBatchUploader'))return;
    const section=document.createElement('section');section.id='heroBatchUploader';section.className='batch-center hero-batch-center';
    section.innerHTML=`
      <div class="batch-head">
        <div>
          <div class="eyebrow">BATCH HERO</div>
          <h3>Hero 批量上传</h3>
          <p class="meta">按现有 Hero Key 做最长文件名前缀匹配。例：<code>xiaomi-su7-ultra_v4.webp</code> 优先匹配 <code>xiaomi-su7-ultra</code>，不会误落到 <code>xiaomi-su7</code>。同一 Key 多张时最先上传优先。</p>
        </div>
      </div>
      <div id="heroBatchDropWrap"></div>
      <div class="batch-summary" id="heroBatchSummary">等待图片…</div>
      <div class="batch-queue" id="heroBatchQueue"><div class="batch-empty">尚未选择图片。</div></div>
      <div class="batch-actions"><button id="publishHeroBatch" disabled>批量发布匹配 Hero</button></div>`;
    panel.insertBefore(section,panel.firstChild);

    let files=[],plan=[];
    const rebuild=()=>{
      plan=planBatch(files,heroTargets(),'Hero Key');createQueueRows($('heroBatchQueue'),plan);
      const ready=plan.filter(item=>item.state==='ready').length;
      $('heroBatchSummary').textContent=`已分析 ${plan.length} 张 · 可发布 ${ready} · 跳过 ${plan.length-ready}`;
      $('publishHeroBatch').disabled=!ready;
    };
    $('heroBatchDropWrap').appendChild(makeDropZone({id:'heroBatchDrop',title:'拖入多个 Hero 图片',description:'文件名必须以已存在的 Hero Key 开头；后续 _v2 / -日期 / _final 等信息不会影响匹配。',onFiles:selected=>{files=selected;rebuild()}}));

    $('publishHeroBatch').onclick=async()=>{
      const ready=plan.filter(item=>item.state==='ready');if(!ready.length)return;
      $('publishHeroBatch').disabled=true;
      let ok=0,failed=0;
      for(let i=0;i<ready.length;i++){
        const item=ready[i];const key=item.target.key;
        $('heroBatchSummary').textContent=`正在发布 ${i+1}/${ready.length} · ${key} · ${item.file.name}`;
        const body=new FormData();body.append('artworkKey',key);body.append('file',item.file);
        try{
          const response=await fetch('api/publish',{method:'POST',headers:{'X-Hero-Admin-Request':'1'},body});
          const data=await response.json();if(!response.ok)throw new Error(data.error||'Hero 发布失败');
          if(!manifest)manifest={schemaVersion:1,artworks:{}};if(!manifest.artworks)manifest.artworks={};manifest.artworks[data.artworkKey]={version:data.version,url:data.url};
          item.state='done';item.message=`已发布 v${data.version}`;ok++;
        }catch(error){item.state='error';item.message=`失败：${error.message||error}`;failed++}
        createQueueRows($('heroBatchQueue'),plan);
      }
      await loadHero();
      $('heroBatchSummary').textContent=`批量 Hero 完成 · 成功 ${ok} · 失败 ${failed} · 重复/未匹配 ${plan.length-ready.length}`;
      $('publishHeroBatch').disabled=false;
    };
  }

  function init(){
    injectBrandBatchUi();injectHeroBatchUi();
    const timer=setInterval(()=>{
      if(catalog&&manifest){clearInterval(timer);injectBrandBatchUi();injectHeroBatchUi()}
    },100);
  }
  init();
})();
