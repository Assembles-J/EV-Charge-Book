(function(){
  const $=id=>document.getElementById(id);
  const KEY_RE=/^[a-z0-9][a-z0-9-]{1,100}$/;
  const IMAGE_RE=/\.(png|webp)$/i;
  const LIGHT_L=rgbLuminance(245,246,245);
  const DARK_L=rgbLuminance(8,17,13);
  const MIN_CONTRAST=3;
  let catalog=null,manifest=null,bundle=null,files=[],rows=[];

  function normalizeToken(value){
    return String(value||'').trim().toLowerCase().replace(/\.(png|webp)$/i,'').replace(/[\s_]+/g,'-').replace(/[^a-z0-9-]+/g,'-').replace(/-+/g,'-').replace(/^-|-$/g,'');
  }
  function safeSlug(value){return String(value||'').toLowerCase().replace(/[^a-z0-9]+/g,'_').replace(/^_+|_+$/g,'')}
  function stripHeroVariant(value){return String(value||'').trim().toLowerCase().replace(/-(dark|light)$/,'')}
  function heroVariantKey(base,variant){return `${stripHeroVariant(base)}-${variant}`}
  function srgb(value){const c=Math.max(0,Math.min(255,Number(value)||0))/255;return c<=.04045?c/12.92:Math.pow((c+.055)/1.055,2.4)}
  function rgbLuminance(r,g,b){return .2126*srgb(r)+.7152*srgb(g)+.0722*srgb(b)}
  function contrastRatio(a,b){const hi=Math.max(a,b),lo=Math.min(a,b);return (hi+.05)/(lo+.05)}
  function longestPrefix(fileName,targets){
    const stem=normalizeToken(fileName);let best=null;
    for(const target of targets||[]){for(const alias of target.aliases||[]){if(alias&&stem.startsWith(alias)&&(!best||alias.length>best.alias.length))best={target,alias,stem}}}
    return best;
  }
  function fieldDiff(current,next,fields){
    return fields.flatMap(field=>{
      const before=current?.[field]??null,after=next?.[field]??null;
      return JSON.stringify(before)===JSON.stringify(after)?[]:[{field,before,after}];
    });
  }
  function inferVariant(fileName){
    const tokens=normalizeToken(fileName).split('-');
    if(tokens.includes('light'))return 'light';
    if(tokens.includes('dark'))return 'dark';
    return '';
  }
  function heroExistingInfo(base,stateManifest=manifest){
    const clean=stripHeroVariant(base),artworks=stateManifest?.artworks||{};
    return ['dark','light'].map(variant=>{
      const key=heroVariantKey(clean,variant);
      const exact=artworks[key];
      const legacy=variant==='dark'?artworks[clean]:null;
      return {variant,key,current:exact||legacy||null,legacy:!exact&&!!legacy};
    });
  }
  function nextHeroFilename(base,variant,stateManifest=manifest){
    const info=heroExistingInfo(base,stateManifest).find(x=>x.variant===variant);
    const currentVersion=Number(info?.current?.version||0);
    return `${safeSlug(heroVariantKey(base,variant))}_v${currentVersion+1}.webp`;
  }
  function nextLogoFilename(brand,variant){
    const v=Number(variant==='light'?brand?.logoLightVersion:brand?.logoDarkVersion)||0;
    return `brand_${safeSlug(brand?.brandId)}_${variant}_v${v+1}.webp`;
  }

  globalThis.__evResourceWorkbenchTest={normalizeToken,safeSlug,stripHeroVariant,heroVariantKey,longestPrefix,fieldDiff,inferVariant,nextHeroFilename};
  if(typeof document==='undefined')return;

  function brandTargets(){
    const all=[...(catalog?.brands||[])];
    const staged=bundle?.catalogImport?.brands?.[0];
    if(staged&&!all.some(x=>x.brandId===staged.brandId))all.push({...staged,logoKey:`brand-${staged.brandId}`,logoLightVersion:0,logoDarkVersion:0});
    return all.map(brand=>({id:brand.brandId,brand,aliases:[normalizeToken(brand.brandId),normalizeToken(brand.logoKey),normalizeToken(`brand-${brand.brandId}`)].filter(Boolean)}));
  }
  function heroTargets(){
    const keys=new Set();
    for(const vehicle of catalog?.vehicles||[]){if(vehicle.heroArtworkKey)keys.add(stripHeroVariant(vehicle.heroArtworkKey))}
    for(const key of Object.keys(manifest?.artworks||{}))keys.add(stripHeroVariant(key));
    if(bundle?.heroKey)keys.add(stripHeroVariant(bundle.heroKey));
    return [...keys].filter(Boolean).map(key=>({id:key,key,aliases:[normalizeToken(key),normalizeToken(`hero-${key}`)]}));
  }
  function brandById(id){return brandTargets().find(x=>x.id===id)?.brand||null}

  async function fetchJson(url){const r=await fetch(url,{headers:{Accept:'application/json'}});const data=await r.json();if(!r.ok)throw new Error(data.error||`${url} 读取失败`);return data}
  async function loadState(){
    $('runtimeSummary').textContent='正在读取 Catalog / Hero manifest…';
    try{
      [catalog,manifest]=await Promise.all([fetchJson('../api/catalog/state'),fetchJson('../api/state')]);
      $('runtimeSummary').textContent=`Catalog v${catalog.catalogVersion||'?'} · 品牌 ${(catalog.brands||[]).length} · 车型 ${(catalog.vehicles||[]).length} · Hero Key ${Object.keys(manifest.artworks||{}).length}`;
      if(bundle)validateBundle(false);
      if(files.length)await rebuildRows();
    }catch(error){$('runtimeSummary').textContent=`读取失败：${error.message||error}`}
  }

  function normalizeBundle(raw){
    if(!raw||typeof raw!=='object')throw new Error('JSON 必须是对象');
    if(raw.format==='ev-charge-book-resource-bundle'){
      if(Number(raw.version)!==1)throw new Error('resource bundle version 仅支持 1');
      const ci=raw.catalogImport;
      if(!ci||ci.format!=='ev-charge-book-vehicle-catalog'||Number(ci.version)!==1)throw new Error('catalogImport 必须是 ev-charge-book-vehicle-catalog v1');
      return raw;
    }
    if(raw.format==='ev-charge-book-vehicle-catalog'&&Number(raw.version)===1){
      const heroKey=raw.vehicles?.[0]?.heroArtworkKey||'';
      return {format:'ev-charge-book-resource-bundle',version:1,heroKey,catalogImport:raw,assets:{brandLogo:{light:'',dark:''},hero:{dark:'',light:''}}};
    }
    throw new Error('仅支持 resource bundle v1 或单条 vehicle catalog v1 JSON');
  }

  function inspectBundle(next){
    const ci=next.catalogImport||{},brands=ci.brands||[],vehicles=ci.vehicles||[];
    if(brands.length!==1||vehicles.length!==1)throw new Error('资源工作台一次只接受 1 个品牌 + 1 个车型；批量车型请继续使用车型库导入');
    const brand=brands[0],vehicle=vehicles[0];
    if(!KEY_RE.test(String(brand.brandId||'')))throw new Error('brandId 无效，必须是稳定 ASCII slug');
    if(!KEY_RE.test(String(vehicle.catalogId||'')))throw new Error('catalogId 无效，必须是稳定 ASCII slug');
    if(vehicle.brandId!==brand.brandId)throw new Error('vehicle.brandId 必须与 bundle 品牌一致');
    const heroKey=stripHeroVariant(next.heroKey||vehicle.heroArtworkKey||'');
    if(!KEY_RE.test(heroKey))throw new Error('heroKey 无效，必须是小写字母/数字/连字符');
    next.heroKey=heroKey;vehicle.heroArtworkKey=heroKey;
    return {brand,vehicle,heroKey};
  }

  function renderBundleDiff(info){
    const currentBrand=(catalog?.brands||[]).find(x=>x.brandId===info.brand.brandId);
    const currentVehicle=(catalog?.vehicles||[]).find(x=>x.catalogId===info.vehicle.catalogId);
    const brandFields=['name','englishName','isActive'];
    const vehicleFields=['brandId','series','modelName','modelYear','trimName','powertrainType','batteryCapacityKwh','rangeKm','rangeStandard','heroArtworkKey','isActive'];
    const diffs=[];
    if(currentBrand)diffs.push(...fieldDiff(currentBrand,info.brand,brandFields).map(x=>({...x,scope:`品牌 ${info.brand.brandId}`})));
    if(currentVehicle)diffs.push(...fieldDiff(currentVehicle,info.vehicle,vehicleFields).map(x=>({...x,scope:`车型 ${info.vehicle.catalogId}`})));
    const heroRefs=(catalog?.vehicles||[]).filter(x=>x.heroArtworkKey===info.heroKey&&x.catalogId!==info.vehicle.catalogId);
    const existingHero=heroExistingInfo(info.heroKey).filter(x=>x.current);
    const root=$('bundleDiff');root.innerHTML='';
    for(const d of diffs){const row=document.createElement('div');row.className='diff-row warn';row.innerHTML=`<div class="diff-key">${escapeHtml(d.scope)} · ${escapeHtml(d.field)}</div><div class="diff-before">当前：${escapeHtml(show(d.before))}</div><div class="diff-after">将变为：${escapeHtml(show(d.after))}</div>`;root.appendChild(row)}
    for(const h of existingHero){const row=document.createElement('div');row.className='diff-row warn';row.innerHTML=`<div class="diff-key">Hero ${escapeHtml(h.key)}</div><div class="diff-before">当前：v${h.current.version}${h.legacy?' · legacy base':''}</div><div class="diff-after">上传对应图片时将生成新不可变版本</div>`;root.appendChild(row)}
    if(heroRefs.length){const row=document.createElement('div');row.className='diff-row warn';row.innerHTML=`<div class="diff-key">Hero Key 被复用</div><div class="diff-before">${heroRefs.map(x=>escapeHtml(x.catalogId)).join('<br>')}</div><div class="diff-after">更新该 Key 会同步影响这些车型，请确认语义确实共用。</div>`;root.appendChild(row)}
    const requiresConfig=!!((currentBrand&&fieldDiff(currentBrand,info.brand,brandFields).length)||(currentVehicle&&fieldDiff(currentVehicle,info.vehicle,vehicleFields).length));
    const requiresHero=existingHero.length>0||heroRefs.length>0;
    $('configUpdateConfirmWrap').classList.toggle('hidden',!requiresConfig);
    $('heroUpdateConfirmWrap').classList.toggle('hidden',!requiresHero);
    if(!requiresConfig)$('configUpdateConfirm').checked=false;
    if(!requiresHero)$('heroUpdateConfirm').checked=false;
    return {currentBrand,currentVehicle,diffs,requiresConfig,requiresHero,heroRefs,existingHero};
  }

  function validateBundle(showErrors=true){
    const text=$('bundleJson').value.trim();
    if(!text){bundle=null;$('bundleSummary').textContent='未载入资源包。只批量上传图片时可以跳过本步骤。';$('bundleDiff').innerHTML='';return null}
    try{
      bundle=normalizeBundle(JSON.parse(text));const info=inspectBundle(bundle);const review=renderBundleDiff(info);
      const brandState=review.currentBrand?'已有品牌':'新品牌',vehicleState=review.currentVehicle?'已有车型':'新车型';
      $('bundleSummary').textContent=`校验通过 · ${brandState} ${info.brand.brandId} · ${vehicleState} ${info.vehicle.catalogId} · Hero Key ${info.heroKey}${review.requiresConfig?' · 有配置变更待确认':''}${review.requiresHero?' · Hero Key 已存在/复用待确认':''}`;
      void rebuildRows();return {info,review};
    }catch(error){bundle=null;$('bundleSummary').textContent=`资源包错误：${error.message||error}`;$('bundleDiff').innerHTML='';if(showErrors)$('publishLog').textContent=`资源包错误：${error.message||error}`;return null}
  }

  async function analyzeImage(file){
    if(typeof createImageBitmap!=='function')throw new Error('浏览器不支持图片像素校验');
    const bitmap=await createImageBitmap(file);
    try{
      const size=96,canvas=document.createElement('canvas');canvas.width=size;canvas.height=size;const ctx=canvas.getContext('2d',{willReadFrequently:true});if(!ctx)throw new Error('无法创建校验画布');
      ctx.clearRect(0,0,size,size);ctx.drawImage(bitmap,0,0,size,size);const p=ctx.getImageData(0,0,size,size).data;
      let visible=0,transparent=0,lum=0,weight=0;
      for(let i=0;i<p.length;i+=4){const a=p[i+3];if(a<245)transparent++;if(a<24)continue;const w=a/255;visible++;lum+=rgbLuminance(p[i],p[i+1],p[i+2])*w;weight+=w}
      return {width:bitmap.width,height:bitmap.height,ratio:bitmap.width/bitmap.height,visiblePixels:visible,transparentRatio:transparent/(size*size),averageLuminance:weight?lum/weight:0};
    }finally{bitmap.close?.()}
  }
  function inferKind(fileName,stats){const t=normalizeToken(fileName).split('-');if(t.includes('logo')||t.includes('brand'))return 'logo';if(t.includes('hero'))return 'hero';return stats.ratio>=1.25?'hero':'logo'}
  function validateLogo(stats,variant){
    if(Math.max(stats.width,stats.height)<512)return 'Logo 最长边至少 512 px';
    if(stats.visiblePixels<32)return '未检测到有效 Logo 主体';
    if(stats.transparentRatio<.02)return 'Logo 背景基本不透明';
    const lc=contrastRatio(stats.averageLuminance,LIGHT_L),dc=contrastRatio(stats.averageLuminance,DARK_L);
    if(variant==='light'&&lc<MIN_CONTRAST)return `浅色界面对比度不足 ${lc.toFixed(1)}`;
    if(variant==='dark'&&dc<MIN_CONTRAST)return `深色界面对比度不足 ${dc.toFixed(1)}`;
    if(variant==='both'&&(lc<MIN_CONTRAST||dc<MIN_CONTRAST))return `两端通用对比度不足（浅 ${lc.toFixed(1)} / 深 ${dc.toFixed(1)}）`;
    return '';
  }
  function autoLogoVariant(stats){const lc=contrastRatio(stats.averageLuminance,LIGHT_L),dc=contrastRatio(stats.averageLuminance,DARK_L);if(lc>=MIN_CONTRAST&&dc>=MIN_CONTRAST)return 'both';if(lc>=MIN_CONTRAST)return 'light';if(dc>=MIN_CONTRAST)return 'dark';return 'auto'}
  function validateHero(stats){if(stats.width<1200||stats.height<800)return `Hero 至少 1200×800，当前 ${stats.width}×${stats.height}`;if(stats.ratio<1.40||stats.ratio>1.55)return `Hero 比例需 1.40–1.55:1，当前 ${stats.ratio.toFixed(2)}:1`;return ''}

  async function rebuildRows(){
    const generation=Date.now()+Math.random();rebuildRows.generation=generation;
    $('resourceQueue').innerHTML='<div class="status-strip">正在分析图片…</div>';
    const next=[];
    for(let index=0;index<files.length;index++){
      const file=files[index];if(!IMAGE_RE.test(file.name)&&!['image/png','image/webp'].includes(file.type))continue;
      try{
        const stats=await analyzeImage(file);if(rebuildRows.generation!==generation)return;
        const kind=inferKind(file.name,stats),explicit=inferVariant(file.name);
        let targetId='',variant=explicit;
        if(kind==='logo'){
          const match=longestPrefix(file.name,brandTargets());targetId=match?.target?.id||bundle?.catalogImport?.brands?.[0]?.brandId||'';variant=variant||autoLogoVariant(stats);
        }else{
          const match=longestPrefix(file.name,heroTargets());targetId=match?.target?.id||bundle?.heroKey||'';variant=variant||'dark';
        }
        next.push({id:`r${index}`,file,index,stats,kind,targetId,variant,manual:false,customKey:'',state:'pending',message:''});
      }catch(error){next.push({id:`r${index}`,file,index,stats:null,kind:'hero',targetId:'',variant:'dark',state:'invalid',message:`图片读取失败：${error.message||error}`})}
    }
    rows=next;recomputeRows();renderRows();
  }

  function effectiveTarget(row){return row.targetId==='__custom__'?stripHeroVariant(row.customKey):row.targetId}
  function rowClaims(row){
    if(row.state==='invalid'||row.state==='blocked')return [];
    const target=effectiveTarget(row);if(!target)return [];
    if(row.kind==='logo'){const variants=row.variant==='both'?['light','dark']:[row.variant];return variants.filter(x=>x==='light'||x==='dark').map(v=>`logo:${target}:${v}`)}
    return [`hero:${target}:${row.variant}`];
  }
  function recomputeRows(){
    const claims=new Set();
    for(const row of rows){
      if(!row.stats){row.state='invalid';continue}
      const target=effectiveTarget(row);
      if(!target){row.state='blocked';row.message='未匹配；请选择目标后即可继续';continue}
      if(row.kind==='hero'&&!KEY_RE.test(target)){row.state='blocked';row.message='Hero Key 无效';continue}
      let error='';
      if(row.kind==='logo'){
        if(!brandById(target)){error='请选择有效品牌'}else if(!['light','dark','both'].includes(row.variant))error='请选择 Light / Dark / 两边通用';else error=validateLogo(row.stats,row.variant);
      }else{
        if(!['light','dark'].includes(row.variant))error='请选择 Hero Light / Dark';else error=validateHero(row.stats);
      }
      if(error){row.state='invalid';row.message=error;continue}
      const own=row.kind==='logo'?(row.variant==='both'?['light','dark']:[row.variant]).map(v=>`logo:${target}:${v}`):[`hero:${target}:${row.variant}`];
      const duplicate=own.find(c=>claims.has(c));
      if(duplicate){row.state='blocked';row.message='同一目标/界面已有更早文件；请手动改目标或版本';continue}
      own.forEach(c=>claims.add(c));
      if(row.kind==='hero'){
        const existing=heroExistingInfo(target).find(x=>x.variant===row.variant)?.current;
        row.state=existing?'update':'ready';row.message=existing?`已有版本 v${existing.version}，确认后将新增版本`:'可新建发布';
      }else{row.state='ready';row.message='校验通过'}
    }
    updatePublishGate();
  }

  function targetOptions(row){
    if(row.kind==='logo')return brandTargets().map(t=>`<option value="${escapeAttr(t.id)}" ${row.targetId===t.id?'selected':''}>${escapeHtml(t.brand.name||t.id)} · ${escapeHtml(t.id)}</option>`).join('');
    const opts=heroTargets().map(t=>`<option value="${escapeAttr(t.id)}" ${row.targetId===t.id?'selected':''}>${escapeHtml(t.id)}</option>`).join('');
    return opts+`<option value="__custom__" ${row.targetId==='__custom__'?'selected':''}>+ 手动输入新 Hero Key</option>`;
  }
  function variantOptions(row){
    const list=row.kind==='logo'?[['light','Light'],['dark','Dark'],['both','Light + Dark']]:[['dark','Dark Hero'],['light','Light Hero']];
    return list.map(([v,label])=>`<option value="${v}" ${row.variant===v?'selected':''}>${label}</option>`).join('')
  }
  function filenamePreview(row){
    const target=effectiveTarget(row);if(!target)return '等待选择目标';
    if(row.kind==='logo'){
      const brand=brandById(target);if(!brand)return '无有效品牌';
      if(row.variant==='both')return `${nextLogoFilename(brand,'light')} + ${nextLogoFilename(brand,'dark')}`;
      return nextLogoFilename(brand,row.variant);
    }
    return nextHeroFilename(target,row.variant);
  }
  function renderRows(){
    const root=$('resourceQueue');root.innerHTML='';
    if(!rows.length){root.innerHTML='<div class="status-strip">尚未选择图片。</div>';updateQueueSummary();return}
    for(const row of rows){
      const el=document.createElement('div');el.className=`queue-row ${row.state}`;el.dataset.rowId=row.id;
      el.innerHTML=`
        <div class="file-meta"><div class="file-name">${escapeHtml(row.file.name)}</div><div class="file-facts">${row.stats?`${row.stats.width}×${row.stats.height} · ${row.stats.ratio.toFixed(2)}:1`:''}</div></div>
        <select class="kind-select" aria-label="资源类型"><option value="logo" ${row.kind==='logo'?'selected':''}>品牌 Logo</option><option value="hero" ${row.kind==='hero'?'selected':''}>车型 Hero</option></select>
        <div class="target-cell"><select class="target-select">${targetOptions(row)}</select>${row.kind==='hero'&&row.targetId==='__custom__'?`<input class="custom-key" type="text" value="${escapeAttr(row.customKey)}" placeholder="例如 byd-seal-08-2026" />`:''}</div>
        <select class="variant-select">${variantOptions(row)}</select>
        <div class="result-cell"><div class="result-name">服务器保存：${escapeHtml(filenamePreview(row))}</div><div class="result-status">${escapeHtml(row.message)}</div></div>`;
      root.appendChild(el);
      el.querySelector('.kind-select').onchange=e=>{row.kind=e.target.value;row.targetId='';row.variant=row.kind==='logo'?autoLogoVariant(row.stats):'dark';recomputeRows();renderRows()};
      el.querySelector('.target-select').onchange=e=>{row.targetId=e.target.value;row.manual=true;recomputeRows();renderRows()};
      el.querySelector('.variant-select').onchange=e=>{row.variant=e.target.value;row.manual=true;recomputeRows();renderRows()};
      const custom=el.querySelector('.custom-key');if(custom)custom.oninput=e=>{row.customKey=normalizeToken(e.target.value);row.manual=true;recomputeRows();el.querySelector('.result-name').textContent=`服务器保存：${filenamePreview(row)}`;el.querySelector('.result-status').textContent=row.message;el.className=`queue-row ${row.state}`;updatePublishGate()};
    }
    updateQueueSummary();
  }
  function updateQueueSummary(){const ready=rows.filter(r=>r.state==='ready').length,updates=rows.filter(r=>r.state==='update').length,blocked=rows.filter(r=>['blocked','invalid'].includes(r.state)).length;$('queueSummary').textContent=`已分析 ${rows.length} 张 · 新增 ${ready} · 更新 ${updates} · 需处理 ${blocked}`;$('recheckQueue').disabled=!files.length}

  function reviewState(){
    let bundleReview=null;
    if($('bundleJson').value.trim()){const result=validateBundle(false);if(!result)return {blocked:true,reason:'资源包 JSON 未通过校验'};bundleReview=result.review}
    const bad=rows.filter(r=>['blocked','invalid'].includes(r.state));if(bad.length)return {blocked:true,reason:`还有 ${bad.length} 张图片未匹配或未通过校验`};
    if(bundleReview?.requiresConfig&&!$('configUpdateConfirm').checked)return {blocked:true,reason:'存在已有品牌/车型配置变更，请先勾选确认'};
    const heroUpdates=rows.some(r=>r.kind==='hero'&&r.state==='update')||bundleReview?.requiresHero;
    if(heroUpdates&&!$('heroUpdateConfirm').checked)return {blocked:true,reason:'存在已有/复用 Hero Key，请先检查变动并确认更新'};
    if(!rows.length&&!bundle)return {blocked:true,reason:'请至少提供资源包 JSON 或图片'};
    return {blocked:false,reason:'可以发布'};
  }
  function updatePublishGate(){const review=reviewStateSafe();$('publishBundle').disabled=review.blocked;$('publishLog').textContent=review.reason;$('publishLog').className=`publish-log ${review.blocked?'warn':'ok'}`;updateQueueSummary()}
  function reviewStateSafe(){try{return reviewState()}catch(error){return {blocked:true,reason:error.message||String(error)}}}

  async function adminJson(url,payload){const r=await fetch(url,{method:'POST',headers:{'Content-Type':'application/json','X-Hero-Admin-Request':'1'},body:JSON.stringify(payload)});const data=await r.json();if(!r.ok)throw new Error(data.error||'请求失败');return data}
  async function adminFile(url,fields,file){const body=new FormData();for(const [k,v] of Object.entries(fields))body.append(k,v);body.append('file',file);const r=await fetch(url,{method:'POST',headers:{'X-Hero-Admin-Request':'1'},body});const data=await r.json();if(!r.ok)throw new Error(data.error||'上传失败');return data}
  function hasChanges(current,next,fields){return !current||fieldDiff(current,next,fields).length>0}

  async function publishAll(){
    const gate=reviewStateSafe();if(gate.blocked){updatePublishGate();return}
    $('publishBundle').disabled=true;const logs=[];const log=msg=>{$('publishLog').textContent=msg;logs.push(msg)};
    try{
      let parsed=null;if($('bundleJson').value.trim()){bundle=normalizeBundle(JSON.parse($('bundleJson').value));parsed=inspectBundle(bundle)}
      if(parsed){
        const currentBrand=(catalog.brands||[]).find(x=>x.brandId===parsed.brand.brandId);
        if(hasChanges(currentBrand,parsed.brand,['name','englishName','isActive'])){log(`保存品牌 ${parsed.brand.brandId}…`);await adminJson('../api/brand/save',parsed.brand);await loadState()}
        const currentVehicle=(catalog.vehicles||[]).find(x=>x.catalogId===parsed.vehicle.catalogId);
        if(hasChanges(currentVehicle,parsed.vehicle,['brandId','series','modelName','modelYear','trimName','powertrainType','batteryCapacityKwh','rangeKm','rangeStandard','heroArtworkKey','isActive'])){log(`保存车型 ${parsed.vehicle.catalogId}…`);await adminJson('../api/catalog/save',parsed.vehicle);await loadState()}
      }
      const active=rows.filter(r=>r.state==='ready'||r.state==='update');let taskIndex=0;const total=active.reduce((sum,r)=>sum+(r.kind==='logo'&&r.variant==='both'?2:1),0);
      for(const row of active){
        const target=effectiveTarget(row);
        if(row.kind==='logo'){
          const variants=row.variant==='both'?['light','dark']:[row.variant];
          for(const variant of variants){taskIndex++;log(`上传 ${taskIndex}/${total} · Logo ${target} ${variant}…`);const data=await adminFile('../api/brand/logo',{brandId:target,variant},row.file);row.message=`已发布 ${data.filename}`}
        }else{
          taskIndex++;const key=heroVariantKey(target,row.variant);log(`上传 ${taskIndex}/${total} · Hero ${key}…`);const data=await adminFile('../api/publish',{artworkKey:key},row.file);row.message=`已发布 ${data.filename}`;
        }
        row.state='done';renderRows();
      }
      await loadState();$('publishLog').textContent=`整套资源发布完成 · ${logs.length?logs[logs.length-1]:'配置已保存'}`;$('publishLog').className='publish-log ok';
    }catch(error){$('publishLog').textContent=`发布中止：${error.message||error}`;$('publishLog').className='publish-log error'}finally{updatePublishGate()}
  }

  function escapeHtml(v){return String(v??'').replace(/[&<>"']/g,c=>({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]))}
  function escapeAttr(v){return escapeHtml(v)}
  function show(v){return v===null||v===undefined?'∅':typeof v==='object'?JSON.stringify(v):String(v)}

  function bind(){
    $('validateBundle').onclick=()=>{validateBundle();updatePublishGate()};
    $('readClipboard').onclick=async()=>{try{$('bundleJson').value=await navigator.clipboard.readText();validateBundle();updatePublishGate()}catch(error){$('bundleSummary').textContent=`读取剪贴板失败：${error.message||error}`}};
    $('bundleJson').addEventListener('input',()=>{bundle=null;$('bundleSummary').textContent='内容已修改，请重新校验。';updatePublishGate()});
    $('configUpdateConfirm').onchange=updatePublishGate;$('heroUpdateConfirm').onchange=updatePublishGate;
    const input=$('resourceFiles'),drop=$('resourceDrop');
    input.onchange=()=>{files=Array.from(input.files||[]);void rebuildRows()};
    ['dragenter','dragover'].forEach(t=>drop.addEventListener(t,e=>{e.preventDefault();drop.classList.add('dragging')}));
    ['dragleave','drop'].forEach(t=>drop.addEventListener(t,e=>{e.preventDefault();drop.classList.remove('dragging')}));
    drop.addEventListener('drop',e=>{files=Array.from(e.dataTransfer?.files||[]);void rebuildRows()});
    $('recheckQueue').onclick=()=>void rebuildRows();$('refreshState').onclick=()=>void loadState();$('publishBundle').onclick=()=>void publishAll();
  }
  bind();void loadState();updatePublishGate();
})();
