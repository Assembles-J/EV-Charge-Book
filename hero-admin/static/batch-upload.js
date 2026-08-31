(function(){
  const IMAGE_RE=/\.(png|webp)$/i;
  const LOGO_SAMPLE_SIZE=96;
  const MIN_LOGO_SIDE=64;
  const MIN_VISIBLE_PIXELS=32;
  const MIN_TRANSPARENT_RATIO=0.02;
  const MIN_CONTRAST=3;

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

  function srgbChannel(value){
    const c=Math.max(0,Math.min(255,Number(value)||0))/255;
    return c<=0.04045?c/12.92:Math.pow((c+0.055)/1.055,2.4);
  }

  function rgbLuminance(r,g,b){
    return 0.2126*srgbChannel(r)+0.7152*srgbChannel(g)+0.0722*srgbChannel(b);
  }

  function contrastRatio(a,b){
    const high=Math.max(a,b),low=Math.min(a,b);
    return (high+0.05)/(low+0.05);
  }

  const LIGHT_SURFACE_LUMINANCE=rgbLuminance(245,246,245);
  const DARK_SURFACE_LUMINANCE=rgbLuminance(8,17,13);

  function variantLabel(variant){
    return variant==='light'?'浅色界面':'深色界面';
  }

  function classifyLogoContrast(stats){
    if(!stats||stats.width<MIN_LOGO_SIDE||stats.height<MIN_LOGO_SIDE){
      return {variants:[],valid:false,reason:`图片尺寸过小，至少 ${MIN_LOGO_SIDE}×${MIN_LOGO_SIDE}`};
    }
    if(stats.visiblePixels<MIN_VISIBLE_PIXELS){
      return {variants:[],valid:false,reason:'没有检测到足够的可见 Logo 主体像素'};
    }
    if(stats.transparentRatio<MIN_TRANSPARENT_RATIO){
      return {variants:[],valid:false,reason:'背景基本不透明，请使用带透明背景的 PNG / WebP Logo'};
    }
    const lightContrast=contrastRatio(stats.averageLuminance,LIGHT_SURFACE_LUMINANCE);
    const darkContrast=contrastRatio(stats.averageLuminance,DARK_SURFACE_LUMINANCE);
    const variants=[];
    if(lightContrast>=MIN_CONTRAST)variants.push('light');
    if(darkContrast>=MIN_CONTRAST)variants.push('dark');
    if(!variants.length){
      return {
        variants,
        valid:false,
        lightContrast,
        darkContrast,
        reason:`Logo 与浅/深背景对比度都不足（${lightContrast.toFixed(1)} / ${darkContrast.toFixed(1)}）`
      };
    }
    return {variants,valid:true,lightContrast,darkContrast,reason:''};
  }

  async function analyzeLogoFile(file){
    if(typeof createImageBitmap!=='function')throw new Error('当前浏览器不支持图片像素校验，请升级浏览器后重试');
    const bitmap=await createImageBitmap(file);
    try{
      const canvas=document.createElement('canvas');
      canvas.width=LOGO_SAMPLE_SIZE;canvas.height=LOGO_SAMPLE_SIZE;
      const ctx=canvas.getContext('2d',{willReadFrequently:true});
      if(!ctx)throw new Error('无法创建图片校验画布');
      ctx.clearRect(0,0,LOGO_SAMPLE_SIZE,LOGO_SAMPLE_SIZE);
      ctx.drawImage(bitmap,0,0,LOGO_SAMPLE_SIZE,LOGO_SAMPLE_SIZE);
      const pixels=ctx.getImageData(0,0,LOGO_SAMPLE_SIZE,LOGO_SAMPLE_SIZE).data;
      let visiblePixels=0,transparentPixels=0,luminanceSum=0,weightSum=0;
      const total=LOGO_SAMPLE_SIZE*LOGO_SAMPLE_SIZE;
      for(let i=0;i<pixels.length;i+=4){
        const alpha=pixels[i+3];
        if(alpha<245)transparentPixels++;
        if(alpha<24)continue;
        const weight=alpha/255;
        visiblePixels++;
        luminanceSum+=rgbLuminance(pixels[i],pixels[i+1],pixels[i+2])*weight;
        weightSum+=weight;
      }
      return {
        width:bitmap.width,
        height:bitmap.height,
        visiblePixels,
        transparentRatio:transparentPixels/total,
        averageLuminance:weightSum?luminanceSum/weightSum:0
      };
    }finally{
      if(typeof bitmap.close==='function')bitmap.close();
    }
  }

  async function planBrandBatch(files,targets,analyzer=analyzeLogoFile){
    const seen=new Set();
    const plan=[];
    const selected=imageFiles(files);
    for(let index=0;index<selected.length;index++){
      const file=selected[index];
      const matched=matchByLongestPrefix(file.name,targets);
      if(!matched){
        plan.push({file,index,state:'unmatched',message:'未找到品牌前缀'});
        continue;
      }
      let stats,classification;
      try{
        stats=await analyzer(file);
        classification=classifyLogoContrast(stats);
      }catch(error){
        plan.push({file,index,state:'invalid',target:matched.target,message:`前端图片校验失败：${error.message||error}`});
        continue;
      }
      if(!classification.valid){
        plan.push({file,index,state:'invalid',target:matched.target,stats,classification,message:classification.reason});
        continue;
      }
      const publishVariants=[];
      const duplicateVariants=[];
      for(const variant of classification.variants){
        const claim=`${matched.target.id}:${variant}`;
        if(seen.has(claim))duplicateVariants.push(variant);
        else{seen.add(claim);publishVariants.push(variant)}
      }
      if(!publishVariants.length){
        plan.push({
          file,index,state:'duplicate',target:matched.target,stats,classification,publishVariants,duplicateVariants,
          message:`${classification.variants.map(variantLabel).join(' + ')}均已有更早文件，按“最先上传优先”跳过`
        });
        continue;
      }
      const contrastText=`浅 ${classification.lightContrast.toFixed(1)} / 深 ${classification.darkContrast.toFixed(1)}`;
      const duplicateText=duplicateVariants.length?`；${duplicateVariants.map(variantLabel).join(' + ')}已被更早文件占用`:'';
      plan.push({
        file,index,state:'ready',target:matched.target,stats,classification,publishVariants,duplicateVariants,
        targetText:`${matched.target.id} · ${publishVariants.map(variantLabel).join(' + ')}`,
        message:`自动校验通过 · ${publishVariants.map(variantLabel).join(' + ')} · 对比度 ${contrastText}${duplicateText}`
      });
    }
    return plan;
  }

  globalThis.__evBatchUploadTest={normalizeToken,matchByLongestPrefix,planBatch,rgbLuminance,contrastRatio,classifyLogoContrast,planBrandBatch};
  if(typeof document==='undefined')return;

  function createQueueRows(root,plan){
    root.innerHTML='';
    if(!plan.length){root.innerHTML='<div class="batch-empty">尚未选择图片。</div>';return}
    for(const item of plan){
      const row=document.createElement('div');row.className=`batch-row ${item.state}`;
      const name=document.createElement('div');name.className='batch-file';name.textContent=item.file.name;
      const target=document.createElement('div');target.className='batch-target';target.textContent=item.targetText||item.target?.id||'未匹配';
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
          <p class="meta">只保留一个上传入口。按文件名前缀匹配 <code>brandId / logoKey</code>，浏览器自动检查透明背景与浅/深界面对比度，再分配到 Light、Dark 或两边通用。同一品牌同一界面版本仍按最先进入队列的文件优先。</p>
        </div>
      </div>
      <div id="brandBatchDropWrap"></div>
      <div class="batch-summary" id="brandBatchSummary">等待图片…</div>
      <div class="batch-queue" id="brandBatchQueue"><div class="batch-empty">尚未选择图片。</div></div>
      <div class="batch-actions"><button id="publishBrandBatch" disabled>批量发布匹配 Logo</button></div>`;
    assetCenter.appendChild(section);

    let files=[],plan=[],analysisGeneration=0;
    const rebuild=async()=>{
      const generation=++analysisGeneration;
      $('publishBrandBatch').disabled=true;
      $('brandBatchSummary').textContent=`正在前台校验 ${files.length} 张 Logo…`;
      const next=await planBrandBatch(files,brandTargets());
      if(generation!==analysisGeneration)return;
      plan=next;
      createQueueRows($('brandBatchQueue'),plan);
      const readyItems=plan.filter(item=>item.state==='ready');
      const readyVariants=readyItems.reduce((sum,item)=>sum+item.publishVariants.length,0);
      const invalid=plan.filter(item=>item.state==='invalid').length;
      const skipped=plan.length-readyItems.length;
      $('brandBatchSummary').textContent=`已分析 ${plan.length} 张 · 可发布 ${readyVariants} 个界面版本 · 校验不通过 ${invalid} · 其余跳过 ${skipped-invalid}`;
      $('publishBrandBatch').disabled=!readyVariants;
    };

    $('brandBatchDropWrap').appendChild(makeDropZone({
      id:'brandBatchDrop',
      title:'拖入多个品牌 Logo',
      description:'无需区分 Light / Dark。AI 生成图片直接拖入，前端自动校验透明背景、主体亮度和浅/深背景对比度。文件名前缀例如 xiaomi_v2.webp。',
      onFiles:selected=>{files=selected;void rebuild()}
    }));

    $('publishBrandBatch').onclick=async()=>{
      const ready=plan.filter(item=>item.state==='ready');
      const tasks=ready.flatMap(item=>item.publishVariants.map(variant=>({item,variant})));
      if(!tasks.length)return;
      $('publishBrandBatch').disabled=true;
      let ok=0,failed=0;
      const itemResults=new Map();
      for(let i=0;i<tasks.length;i++){
        const {item,variant}=tasks[i];const brand=item.target.brand;
        $('brandBatchSummary').textContent=`正在发布 ${i+1}/${tasks.length} · ${brand.name} · ${variantLabel(variant)} · ${item.file.name}`;
        const body=new FormData();body.append('brandId',brand.brandId);body.append('variant',variant);body.append('file',item.file);
        try{
          const response=await fetch('api/brand/logo',{method:'POST',headers:{'X-Hero-Admin-Request':'1'},body});
          const data=await response.json();if(!response.ok)throw new Error(data.error||'Logo 发布失败');
          const messages=itemResults.get(item)||[];messages.push(`${variantLabel(variant)} v${data.version}`);itemResults.set(item,messages);ok++;
        }catch(error){
          const messages=itemResults.get(item)||[];messages.push(`${variantLabel(variant)}失败：${error.message||error}`);itemResults.set(item,messages);failed++;
        }
      }
      for(const item of ready){
        const messages=itemResults.get(item)||[];
        const hasFailure=messages.some(message=>message.includes('失败'));
        item.state=hasFailure?'error':'done';
        item.message=messages.length?messages.join(' · '):item.message;
      }
      createQueueRows($('brandBatchQueue'),plan);
      await loadCatalog();
      $('brandBatchSummary').textContent=`批量 Logo 完成 · 成功 ${ok} · 失败 ${failed} · 未匹配/重复/校验失败 ${plan.length-ready.length}`;
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
