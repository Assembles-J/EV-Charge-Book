(function(){
  if(typeof document==='undefined')return;
  const normalizeIdentity=value=>String(value||'').trim().toLocaleLowerCase().replace(/\s+/g,'');
  const isLegacyId=id=>/^brand-[a-f0-9]{8,}$/i.test(String(id||''));
  function duplicatesFor(brand){const name=normalizeIdentity(brand.name),english=normalizeIdentity(brand.englishName);return (catalog?.brands||[]).filter(other=>other.brandId!==brand.brandId&&((name&&normalizeIdentity(other.name)===name)||(english&&normalizeIdentity(other.englishName)===english)))}
  function vehiclesFor(brandId){return (catalog?.vehicles||[]).filter(v=>v.brandId===brandId)}
  function escapeHtml(v){return String(v??'').replace(/[&<>"']/g,c=>({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]))}

  const dialog=document.createElement('dialog');dialog.id='brandDetailDialog';dialog.innerHTML='<div class="dialog-body"><div class="dialog-head"><h2 id="brandDetailTitle">品牌详情</h2><button type="button" class="secondary" id="closeBrandDetail">关闭</button></div><div id="brandDetailContent"></div></div>';document.body.appendChild(dialog);
  document.getElementById('closeBrandDetail').onclick=()=>dialog.close();

  function logoCard(brand,variant){const light=variant==='light',url=light?brand.logoLightUrl:brand.logoDarkUrl,version=light?brand.logoLightVersion:brand.logoDarkVersion,label=light?'Light Logo':'Dark Logo';return `<article class="logo-variant-card ${light?'logo-light-surface':'logo-dark-surface'}"><div class="logo-preview-box">${url?`<img src="${escapeHtml(url)}" alt="${escapeHtml(brand.name)} ${label}" style="max-width:100%;max-height:100%;object-fit:contain" />`:'<span class="meta">未发布</span>'}</div><strong>${label}</strong><span class="meta">${url?`v${Number(version||0)} · ${escapeHtml(url)}`:'尚未配置'}</span></article>`}
  function vehicleRows(list){if(!list.length)return '<div class="status">该品牌当前没有车型。</div>';return `<div class="vehicle-list">${list.map(v=>`<div class="vehicle-row"><div><div class="vehicle-title">${escapeHtml(v.modelName)} <span class="badge${v.isActive===false?' off':''}">${v.isActive===false?'已下架':'在用'}</span></div><div class="vehicle-sub">${escapeHtml(v.series)} · ${escapeHtml(v.trimName||'未配置 Trim')} · ${escapeHtml(v.catalogId)}</div></div><div class="vehicle-facts">${v.batteryCapacityKwh??'--'} kWh · ${v.rangeKm??'--'} km ${escapeHtml(v.rangeStandard||'')}</div><div class="vehicle-facts">${escapeHtml(v.powertrainType)} · ${v.modelYear??'--'} · Hero ${escapeHtml(v.heroArtworkKey||'未配置')}</div></div>`).join('')}</div>`}
  function targetOptions(source){return (catalog?.brands||[]).filter(b=>b.brandId!==source.brandId).sort((a,b)=>Number(isLegacyId(a.brandId))-Number(isLegacyId(b.brandId))||(a.name||'').localeCompare(b.name||'','zh-CN')).map(b=>`<option value="${escapeHtml(b.brandId)}">${escapeHtml(b.name)} · ${escapeHtml(b.brandId)}${isLegacyId(b.brandId)?' · legacy?':''}</option>`).join('')}

  function openBrandDetail(brand){
    const list=vehiclesFor(brand.brandId),dupes=duplicatesFor(brand),legacy=isLegacyId(brand.brandId),root=document.getElementById('brandDetailContent');document.getElementById('brandDetailTitle').textContent=`${brand.name} · 品牌详情`;
    const warning=(legacy||dupes.length)?`<div class="status error"><strong>数据质量提示</strong><br/>${legacy?'当前 brandId 看起来是历史临时 ID。 ':''}${dupes.length?`发现同名/同英文品牌：${dupes.map(x=>escapeHtml(`${x.name} (${x.brandId})`)).join('、')}。`:''}不要直接删除；请核对后合并到规范品牌。</div>`:'';
    root.innerHTML=`${warning}<div class="status"><strong>${escapeHtml(brand.name)}</strong> · ${escapeHtml(brand.englishName||'--')} · <code>${escapeHtml(brand.brandId)}</code> · ${brand.isActive===false?'已下架':'在用'}<br/>旗下车型 ${list.length} 个 · Light ${brand.logoLightUrl?`v${brand.logoLightVersion||0}`:'未发布'} · Dark ${brand.logoDarkUrl?`v${brand.logoDarkVersion||0}`:'未发布'}</div><div class="logo-variant-grid" style="margin-top:14px">${logoCard(brand,'light')}${logoCard(brand,'dark')}</div><h3 style="margin:22px 0 10px">旗下车型</h3>${vehicleRows(list)}<div class="status" style="margin-top:18px"><strong>合并历史品牌</strong><br/>如果这是随机/重复 brandId，可把旗下车型改指向一个规范品牌，再自动下架当前品牌。目标品牌现有 Logo 会继续复用；不会删除历史图片文件。<div class="action-group" style="justify-content:flex-start;margin-top:10px"><select id="mergeBrandTarget" style="min-width:280px">${targetOptions(brand)}</select><button id="mergeBrandNow" class="danger">确认合并并下架当前品牌</button></div><div class="meta" id="mergeBrandStatus" style="margin-top:8px"></div></div>`;
    const mergeButton=document.getElementById('mergeBrandNow');if(!(catalog?.brands||[]).some(b=>b.brandId!==brand.brandId))mergeButton.disabled=true;mergeButton.onclick=()=>mergeBrand(brand);dialog.showModal();
  }

  async function postJson(url,payload){const response=await fetch(url,{method:'POST',headers:adminHeaders,body:JSON.stringify(payload)});const data=await response.json();if(!response.ok)throw new Error(data.error||'请求失败');return data}
  async function mergeBrand(source){
    const targetId=document.getElementById('mergeBrandTarget').value,target=(catalog?.brands||[]).find(b=>b.brandId===targetId),list=vehiclesFor(source.brandId),status=document.getElementById('mergeBrandStatus');if(!target)return;
    if(!confirm(`把 ${source.name} (${source.brandId}) 合并到 ${target.name} (${target.brandId})？\n\n将改写 ${list.length} 个车型的 brandId，然后下架旧品牌。目标品牌 Logo 将继续复用；不会删除 Hero、Logo 文件或用户历史。`))return;
    try{
      status.textContent=`正在迁移 ${list.length} 个车型…`;
      for(let index=0;index<list.length;index++){
        const v=list[index];status.textContent=`迁移车型 ${index+1}/${list.length} · ${v.catalogId}`;
        await postJson('api/catalog/save',{catalogId:v.catalogId,brandId:target.brandId,series:v.series,modelName:v.modelName,modelYear:v.modelYear??null,trimName:v.trimName??null,powertrainType:v.powertrainType,batteryCapacityKwh:v.batteryCapacityKwh??null,rangeKm:v.rangeKm??null,rangeStandard:v.rangeStandard??null,heroArtworkKey:v.heroArtworkKey??null});
      }
      await postJson('api/brand/status',{brandId:source.brandId,isActive:false});status.textContent='合并完成，正在刷新品牌目录…';await loadCatalog();dialog.close();setStatus(document.getElementById('brandStatus'),`${source.name} (${source.brandId}) 已合并到 ${target.name} (${target.brandId})；旧品牌已下架。`,'ok');
    }catch(error){status.textContent=`合并中止：${error.message||error}`;status.style.color='#ffb5b5'}
  }

  const baseRenderBrands=renderBrands;
  renderBrands=function(){
    baseRenderBrands();
    if(!catalog)return;const q=document.getElementById('brandSearch').value.trim().toLowerCase(),visible=(catalog.brands||[]).filter(b=>!q||`${b.brandId} ${b.name} ${b.englishName||''}`.toLowerCase().includes(q)),nodes=[...document.getElementById('brandList').children];
    nodes.forEach((row,index)=>{const brand=visible[index];if(!brand)return;row.style.cursor='pointer';row.tabIndex=0;row.setAttribute('role','button');row.setAttribute('aria-label',`查看 ${brand.name} 品牌详情、Logo 和旗下车型`);row.onclick=event=>{if(event.target.closest('button'))return;openBrandDetail(brand)};row.onkeydown=event=>{if((event.key==='Enter'||event.key===' ')&&!event.target.closest('button')){event.preventDefault();openBrandDetail(brand)}};const dupes=duplicatesFor(brand);if(isLegacyId(brand.brandId)||dupes.length){row.style.borderColor='rgba(243,191,104,.42)';row.title=`数据质量提示：${isLegacyId(brand.brandId)?'疑似历史临时 brandId；':''}${dupes.length?'存在同名/同英文品牌；':''} 点击查看并合并`}}
  };
  globalThis.__evBrandDetailTest={normalizeIdentity,isLegacyId};
  renderBrands();
})();
