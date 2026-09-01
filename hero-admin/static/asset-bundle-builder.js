(function(){
  const KEY_RE=/^[a-z0-9][a-z0-9-]{1,100}$/;
  const normalizeKey=value=>String(value||'').trim().toLowerCase().replace(/[\s_]+/g,'-').replace(/[^a-z0-9-]+/g,'-').replace(/-+/g,'-').replace(/^-|-$/g,'');
  const stripHeroVariant=value=>normalizeKey(value).replace(/-(dark|light)$/,'');
  const nullableNumber=(value,integer=false)=>{const text=String(value??'').trim();if(!text)return null;const n=Number(text);if(!Number.isFinite(n))return null;return integer?Math.trunc(n):n};
  const normalizedIdentity=value=>String(value||'').trim().toLocaleLowerCase().replace(/\s+/g,'');

  function buildResourceBundle(input){
    const brand=input.brand;
    const heroKey=stripHeroVariant(input.heroKey);
    const catalogId=normalizeKey(input.catalogId);
    if(!brand||!KEY_RE.test(String(brand.brandId||'')))throw new Error('请选择有效品牌或填写新品牌 brandId');
    if(!String(brand.name||'').trim())throw new Error('品牌名称不能为空');
    if(!KEY_RE.test(catalogId))throw new Error('catalogId 只允许小写字母、数字和连字符');
    if(!String(input.series||'').trim())throw new Error('车系不能为空');
    if(!String(input.modelName||'').trim())throw new Error('车型名称不能为空');
    if(!KEY_RE.test(heroKey))throw new Error('Hero Key 只允许小写字母、数字和连字符，且不能带 -light / -dark');
    const modelYear=nullableNumber(input.modelYear,true);
    if(modelYear!==null&&(modelYear<1990||modelYear>2100))throw new Error('年款超出合理范围');
    const battery=nullableNumber(input.batteryCapacityKwh,false);
    const range=nullableNumber(input.rangeKm,true);
    const rangeStandard=String(input.rangeStandard||'').trim()||null;
    const needsSource=[];
    if(modelYear===null)needsSource.push('modelYear');
    if(battery===null)needsSource.push('batteryCapacityKwh');
    if(range===null)needsSource.push('rangeKm');
    if(rangeStandard===null)needsSource.push('rangeStandard');
    const brandId=String(brand.brandId).trim().toLowerCase();
    const lightReady=Number(brand.logoLightVersion||0)>0&&Boolean(brand.logoLightUrl);
    const darkReady=Number(brand.logoDarkVersion||0)>0&&Boolean(brand.logoDarkUrl);
    return {
      format:'ev-charge-book-resource-bundle',
      version:1,
      heroKey,
      updateIntent:input.mode==='update',
      changeSummary:[input.mode==='update'?'Web builder: review existing vehicle/resource changes before update':'Web builder: new resource bundle'],
      needsSource,
      catalogImport:{
        format:'ev-charge-book-vehicle-catalog',
        version:1,
        brands:[{
          brandId,
          name:String(brand.name).trim(),
          englishName:String(brand.englishName||'').trim(),
          isActive:brand.isActive!==false
        }],
        vehicles:[{
          catalogId,
          brandId,
          series:String(input.series).trim(),
          modelName:String(input.modelName).trim(),
          modelYear,
          trimName:String(input.trimName||'').trim()||null,
          powertrainType:String(input.powertrainType||'BEV').trim().toUpperCase(),
          batteryCapacityKwh:battery,
          rangeKm:range,
          rangeStandard,
          heroArtworkKey:heroKey,
          isActive:input.vehicleIsActive!==false
        }]
      },
      assets:{
        brandLogo:{
          light:lightReady?null:`brand_${brandId}_light.png`,
          dark:darkReady?null:`brand_${brandId}_dark.png`,
          reuseExisting:{light:lightReady,dark:darkReady}
        },
        hero:{
          dark:`hero_${heroKey}_dark.png`,
          light:`hero_${heroKey}_light.png`
        }
      }
    };
  }

  globalThis.__evAssetBundleBuilderTest={normalizeKey,stripHeroVariant,nullableNumber,normalizedIdentity,buildResourceBundle};
  if(typeof document==='undefined')return;

  const $=id=>document.getElementById(id);
  let catalog=null,manifest=null,logoStandard='',heroDarkPrompt='';

  function existingHeroKeys(){
    const keys=new Set();
    for(const v of catalog?.vehicles||[])if(v.heroArtworkKey)keys.add(stripHeroVariant(v.heroArtworkKey));
    for(const key of Object.keys(manifest?.artworks||{}))keys.add(stripHeroVariant(key));
    return keys;
  }
  function selectedBrand(){
    const value=$('brandSelect').value;
    if(value==='__new__')return {
      brandId:normalizeKey($('brandId').value),
      name:$('brandName').value.trim(),
      englishName:$('brandEnglish').value.trim(),
      isActive:true,
      logoLightUrl:null,logoLightVersion:0,logoDarkUrl:null,logoDarkVersion:0
    };
    return (catalog?.brands||[]).find(b=>b.brandId===value)||null;
  }
  function currentInput(){return {
    mode:$('mode').value,
    brand:selectedBrand(),
    catalogId:$('catalogId').value,
    series:$('series').value,
    modelName:$('modelName').value,
    modelYear:$('modelYear').value,
    trimName:$('trimName').value,
    powertrainType:$('powertrainType').value,
    batteryCapacityKwh:$('batteryCapacityKwh').value,
    rangeKm:$('rangeKm').value,
    rangeStandard:$('rangeStandard').value,
    heroKey:$('heroKey').value,
    vehicleIsActive:true
  }}
  function brandDuplicates(brand){
    if(!brand)return [];
    const name=normalizedIdentity(brand.name),english=normalizedIdentity(brand.englishName);
    return (catalog?.brands||[]).filter(other=>other.brandId!==brand.brandId&&(
      (name&&normalizedIdentity(other.name)===name)||(english&&normalizedIdentity(other.englishName)===english)
    ));
  }
  function collisionState(){
    const input=currentInput(),heroKey=stripHeroVariant(input.heroKey),catalogId=normalizeKey(input.catalogId);
    const heroExists=Boolean(heroKey&&existingHeroKeys().has(heroKey));
    const vehicle=(catalog?.vehicles||[]).find(v=>v.catalogId===catalogId)||null;
    const blocked=input.mode==='create'&&(heroExists||Boolean(vehicle));
    const needsConfirm=input.mode==='update'&&(heroExists||Boolean(vehicle));
    return {heroKey,heroExists,vehicle,blocked,needsConfirm};
  }
  function renderCollision(){
    const state=collisionState(),warning=$('collisionWarning'),wrap=$('updateConfirmWrap');
    warning.classList.toggle('hidden',!(state.heroExists||state.vehicle));
    wrap.classList.toggle('hidden',!state.needsConfirm);
    if(state.heroExists||state.vehicle){
      const parts=[];
      if(state.heroExists)parts.push(`Hero Key ${state.heroKey} 已存在`);
      if(state.vehicle)parts.push(`catalogId ${state.vehicle.catalogId} 已存在（${state.vehicle.brand} ${state.vehicle.modelName}）`);
      warning.textContent=state.blocked?`${parts.join('；')}。新增模式禁止覆盖，请换 Key / catalogId，或切换到更新模式。`:`${parts.join('；')}。更新模式必须核对差异并勾选确认。`;
    }
    return state;
  }
  function renderBrandReuse(){
    const brand=selectedBrand(),el=$('brandReuse');
    if(!brand){el.textContent='请选择品牌。';return}
    const light=brand.logoLightUrl?`Light v${brand.logoLightVersion||0}`:'Light 未发布';
    const dark=brand.logoDarkUrl?`Dark v${brand.logoDarkVersion||0}`:'Dark 未发布';
    const duplicates=brandDuplicates(brand);
    const legacy=/^brand-[a-f0-9]{8,}$/i.test(brand.brandId);
    el.className='reuse'+((duplicates.length||legacy)?' danger':' ok');
    el.textContent=`品牌 ${brand.name} · ${brand.brandId} · ${light} · ${dark}`+
      (brand.logoLightUrl&&brand.logoDarkUrl?' · 本次新增车型无需重新上传 Logo，将直接复用。':' · 只需要补齐未发布的 Logo 版本。')+
      (duplicates.length?` · 警告：发现同名/同英文品牌 ${duplicates.map(x=>x.brandId).join(', ')}，建议先到品牌详情合并。`:'')+
      (legacy?' · 当前 brandId 看起来是历史临时 ID，请优先选择规范品牌。':'');
  }
  function syncNewBrandFields(){
    const show=$('brandSelect').value==='__new__';
    for(const id of ['newBrandIdWrap','newBrandNameWrap','newBrandEnglishWrap'])$(id).classList.toggle('hidden',!show);
  }
  function refreshBuilder(){
    syncNewBrandFields();renderBrandReuse();const collision=renderCollision();
    try{
      if(collision.blocked)throw new Error('存在重复 Hero Key 或 catalogId，新增模式已阻止生成可发布 JSON');
      if(collision.needsConfirm&&!$('updateConfirm').checked)throw new Error('更新模式检测到已有资源，请先勾选确认更新');
      const bundle=buildResourceBundle(currentInput());
      $('resourceJson').value=JSON.stringify(bundle,null,2);$('copyJson').disabled=false;
      $('builderStatus').className='status ok';$('builderStatus').textContent=`Resource JSON 已生成 · brandId ${bundle.catalogImport.brands[0].brandId} · catalogId ${bundle.catalogImport.vehicles[0].catalogId} · Hero ${bundle.heroKey}-dark / ${bundle.heroKey}-light`;
      $('jsonNote').textContent=bundle.needsSource.length?`以下标准字段当前为 null，需要资料后补：${bundle.needsSource.join(', ')}。Web 不会猜数字。`:'车型标准字段已完整填写。';
      return bundle;
    }catch(error){
      $('resourceJson').value='';$('copyJson').disabled=true;$('builderStatus').className='status'+(String(error.message||error).includes('不能为空')?'':' danger');$('builderStatus').textContent=error.message||error;return null;
    }
  }
  function populateBrands(){
    const select=$('brandSelect'),previous=select.value;select.innerHTML='';
    const brands=[...(catalog?.brands||[])].sort((a,b)=>{
      const aLegacy=/^brand-[a-f0-9]{8,}$/i.test(a.brandId),bLegacy=/^brand-[a-f0-9]{8,}$/i.test(b.brandId);
      return Number(aLegacy)-Number(bLegacy)||(a.name||'').localeCompare(b.name||'','zh-CN');
    });
    for(const brand of brands){const o=document.createElement('option');o.value=brand.brandId;const legacy=/^brand-[a-f0-9]{8,}$/i.test(brand.brandId);o.textContent=`${brand.name} · ${brand.brandId}${brand.isActive===false?' · 已下架':''}${legacy?' · legacy?':''}`;select.appendChild(o)}
    const add=document.createElement('option');add.value='__new__';add.textContent='+ 新品牌（手工填写稳定 brandId）';select.appendChild(add);
    if(previous&&[...select.options].some(o=>o.value===previous))select.value=previous;
    else if(brands.length)select.value=brands.find(b=>b.isActive!==false&&!/^brand-[a-f0-9]{8,}$/i.test(b.brandId))?.brandId||brands[0].brandId;
  }
  function populateHeroKeys(){
    $('heroKeyList').innerHTML='';[...existingHeroKeys()].sort().forEach(key=>{const o=document.createElement('option');o.value=key;$('heroKeyList').appendChild(o)});
  }
  async function loadState(){
    try{
      $('builderStatus').className='status';$('builderStatus').textContent='正在读取 Catalog / Hero manifest…';
      const [c,m,library]=await Promise.all([
        fetch('../api/catalog/state').then(async r=>{const d=await r.json();if(!r.ok)throw new Error(d.error||'Catalog 读取失败');return d}),
        fetch('../api/state').then(async r=>{const d=await r.json();if(!r.ok)throw new Error(d.error||'Hero manifest 读取失败');return d}),
        fetch('prompt-library.html').then(r=>r.text())
      ]);
      catalog=c;manifest=m;const doc=new DOMParser().parseFromString(library,'text/html');logoStandard=doc.querySelector('#logoText')?.value||doc.querySelector('#logoText')?.textContent||'';heroDarkPrompt=doc.querySelector('#heroText')?.value||doc.querySelector('#heroText')?.textContent||'';
      populateBrands();populateHeroKeys();refreshBuilder();
    }catch(error){$('builderStatus').className='status danger';$('builderStatus').textContent=`线上状态读取失败：${error.message||error}`}
  }
  function maybePrefillVehicle(){
    if($('mode').value!=='update'||!catalog)return;
    const id=normalizeKey($('catalogId').value),v=(catalog.vehicles||[]).find(x=>x.catalogId===id);if(!v)return;
    if([...$('brandSelect').options].some(o=>o.value===v.brandId))$('brandSelect').value=v.brandId;
    $('series').value=v.series||'';$('modelName').value=v.modelName||'';$('modelYear').value=v.modelYear??'';$('trimName').value=v.trimName||'';$('powertrainType').value=v.powertrainType||'BEV';$('batteryCapacityKwh').value=v.batteryCapacityKwh??'';$('rangeKm').value=v.rangeKm??'';$('rangeStandard').value=v.rangeStandard||'';$('heroKey').value=stripHeroVariant(v.heroArtworkKey||'');refreshBuilder();
  }
  function lightHeroRules(model,color){return `【Light Hero】\n车型：${model}\n颜色：${color}\n- WebP / PNG 源图均可，后台最终统一转 WebP 1600×1100。\n- 中央约 1360×1100 是真实 1.24:1 Crop 可视区，左右推荐各留 180 px 安全背景。\n- 与 Dark Hero 保持同一量产车型、同一车身颜色、相近姿态与镜头语言。\n- 背景使用珍珠灰 / 银灰 / 柔和暖白 / 明亮阴天或高级摄影棚，保持克制。\n- 白色/银色车辆必须用自然阴影、轮廓光和材质反差与浅背景分离。\n- 不过曝、不纯白炸亮、不霓虹、不加文字、Logo overlay、水印、UI、车牌文字。`}
  function buildImagePrompt(){
    const bundle=refreshBuilder();if(!bundle)throw new Error('Resource JSON 尚未通过校验');
    const vehicle=bundle.catalogImport.vehicles[0],brand=bundle.catalogImport.brands[0],color=$('bodyColor').value.trim()||'未指定；必须先确认官方量产色，不得猜测',notes=$('notes').value.trim();
    const assets=bundle.assets;const requests=[];
    if(assets.brandLogo.light)requests.push(`品牌 Logo Light：${assets.brandLogo.light}`);
    if(assets.brandLogo.dark)requests.push(`品牌 Logo Dark：${assets.brandLogo.dark}`);
    requests.push(`车型 Hero Dark：${assets.hero.dark}`,`车型 Hero Light：${assets.hero.light}`);
    const dark=heroDarkPrompt.replaceAll('{{车型名称}}',vehicle.modelName).replaceAll('{{颜色}}',color);
    const logoNeeded=Boolean(assets.brandLogo.light||assets.brandLogo.dark);
    return `你只负责为 EV Charge Book 生成图片资产。ID 和 JSON 已由 Web Admin 生成并锁定，不要重新生成、改写或返回任何 JSON。\n\n【锁定信息】\n品牌：${brand.name} (${brand.brandId})\n车型：${vehicle.modelName}\ncatalogId：${vehicle.catalogId}\nHero Key：${bundle.heroKey}\n车身颜色：${color}\n${notes?`补充要求：${notes}\n`:''}\n【本次需要生成的文件】\n${requests.map(x=>'- '+x).join('\n')}\n\n${logoNeeded?`【品牌 Logo 正式标准】\n${logoStandard}\n\n只生成上面列出的缺失 Logo；已有品牌 Logo 不要重复生成。\n\n`:''}【Dark Hero 正式 Prompt】\n${dark}\n\n${lightHeroRules(vehicle.modelName,color)}\n\n【交付要求】\n- AI 如果输出 PNG 可以直接保留 PNG，Web 资源工作台会自动转换为 WebP。\n- 文件名前缀必须严格使用上面锁定的 brandId / Hero Key，便于自动匹配。\n- 不要修改 brandId、catalogId、Hero Key。\n- 不要输出车型配置 JSON；配置 JSON 已由 Web Admin 生成。`;
  }
  async function copyText(value,status){await navigator.clipboard.writeText(value);$('builderStatus').className='status ok';$('builderStatus').textContent=status}
  function onAnyInput(event){if(event?.target?.id==='catalogId')maybePrefillVehicle();else refreshBuilder()}
  document.querySelectorAll('input,select').forEach(el=>{if(el.id==='updateConfirm')el.addEventListener('change',refreshBuilder);else{el.addEventListener('input',onAnyInput);el.addEventListener('change',onAnyInput)}});
  $('brandSelect').addEventListener('change',refreshBuilder);$('refreshState').onclick=loadState;
  $('copyJson').onclick=()=>copyText($('resourceJson').value,'Resource JSON 已复制，可直接粘贴到资源工作台。');
  $('generatePrompt').onclick=()=>{try{$('imagePrompt').value=buildImagePrompt();$('copyPrompt').disabled=false;$('builderStatus').className='status ok';$('builderStatus').textContent='图片 Prompt 已生成；AI 不再负责 JSON / Key。'}catch(error){$('builderStatus').className='status danger';$('builderStatus').textContent=error.message||error}};
  $('copyPrompt').onclick=()=>copyText($('imagePrompt').value,'图片 Prompt 已复制到剪贴板。');
  loadState();
})();
