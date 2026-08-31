(function(){
  const $=id=>document.getElementById(id);
  const plan=$('publishPlan'), log=$('publishLog'), button=$('publishBundle'), queue=$('resourceQueue');
  const bundleInput=$('bundleJson'), bundleSummary=$('bundleSummary');
  if(!plan||!log||!button||!queue||!bundleInput||!bundleSummary)return;

  const BRAND_FIELDS=['name','englishName','isActive'];
  const VEHICLE_FIELDS=['brandId','series','modelName','modelYear','trimName','powertrainType','batteryCapacityKwh','rangeKm','rangeStandard','heroArtworkKey','isActive'];
  let catalog=null;
  let publishStarted=false;
  let scheduled=false;
  let refreshing=false;

  function same(a,b){return JSON.stringify(a??null)===JSON.stringify(b??null)}
  function changed(current,next,fields){return !current||fields.some(field=>!same(current?.[field],next?.[field]))}
  function setText(el,text){if(el.textContent!==text)el.textContent=text}

  function parsedBundle(){
    const text=bundleInput.value.trim();
    if(!text)return {hasJson:false,valid:false,info:null};
    if(!/校验通过/.test(bundleSummary.textContent||''))return {hasJson:true,valid:false,info:null};
    try{
      const raw=JSON.parse(text);
      const catalogImport=raw.format==='ev-charge-book-resource-bundle'?raw.catalogImport:raw;
      const brand=catalogImport?.brands?.[0]||null;
      const vehicle=catalogImport?.vehicles?.[0]||null;
      if(!brand||!vehicle)return {hasJson:true,valid:false,info:null};
      return {hasJson:true,valid:true,info:{brand,vehicle}};
    }catch(_){
      return {hasJson:true,valid:false,info:null};
    }
  }

  function configPlan(bundleState){
    if(!bundleState.hasJson)return {pending:false,label:'车型配置：不会创建/更新（未提供 STEP 1 JSON）'};
    if(!bundleState.valid)return {pending:true,label:'车型配置：JSON 尚未通过校验'};
    if(!catalog)return {pending:true,label:'车型配置：正在读取线上 Catalog…'};

    const {brand,vehicle}=bundleState.info;
    const currentBrand=(catalog.brands||[]).find(item=>item.brandId===brand.brandId);
    const currentVehicle=(catalog.vehicles||[]).find(item=>item.catalogId===vehicle.catalogId);
    const brandPending=changed(currentBrand,brand,BRAND_FIELDS);
    const vehiclePending=changed(currentVehicle,vehicle,VEHICLE_FIELDS);
    const brandAction=!currentBrand?`创建品牌 ${brand.brandId}`:brandPending?`更新品牌 ${brand.brandId}`:`复用品牌 ${brand.brandId}`;
    const logoReuse=currentBrand&&!brandPending
      ? `；Logo 复用 Light v${Number(currentBrand.logoLightVersion||0)} / Dark v${Number(currentBrand.logoDarkVersion||0)}`
      : '';
    const vehicleAction=!currentVehicle?`创建车型 ${vehicle.catalogId}`:vehiclePending?`更新车型 ${vehicle.catalogId}`:`车型 ${vehicle.catalogId} 配置无变动`;
    return {pending:brandPending||vehiclePending,label:`品牌：${brandAction}${logoReuse} · 车型：${vehicleAction}`};
  }

  function imagePlan(){
    const rowEls=[...queue.querySelectorAll('.queue-row')];
    const result={rows:rowEls.length,pendingRows:0,doneRows:0,badRows:0,pendingAssets:0,doneAssets:0,logoPending:0,heroPending:0};
    for(const row of rowEls){
      const state=['ready','update','done','blocked','invalid'].find(name=>row.classList.contains(name))||'';
      const kind=row.querySelector('.kind-select')?.value||'';
      const variant=row.querySelector('.variant-select')?.value||'';
      const units=kind==='logo'&&variant==='both'?2:1;
      if(state==='ready'||state==='update'){
        result.pendingRows++;
        result.pendingAssets+=units;
        if(kind==='logo')result.logoPending+=units;
        if(kind==='hero')result.heroPending+=units;
      }else if(state==='done'){
        result.doneRows++;
        result.doneAssets+=units;
      }else if(state==='blocked'||state==='invalid')result.badRows++;
    }
    return result;
  }

  function render(){
    scheduled=false;
    const bundleState=parsedBundle();
    const config=configPlan(bundleState);
    const images=imagePlan();
    const parts=[config.label];

    if(images.pendingAssets){
      parts.push(`待发布图片 ${images.pendingAssets} 项（Logo ${images.logoPending} / Hero ${images.heroPending}）`);
    }else if(images.doneAssets){
      parts.push(`图片已发布 ${images.doneAssets} 项，无待发布图片`);
    }else{
      parts.push('本次没有待发布图片');
    }
    if(images.badRows)parts.push(`仍有 ${images.badRows} 行需要处理`);
    setText(plan,`本次发布计划：${parts.join(' · ')}`);

    if(!bundleState.hasJson){
      const current=bundleSummary.textContent||'';
      if(!current||current==='未载入资源包。只批量上传图片时可以跳过本步骤。'){
        setText(bundleSummary,'未载入资源包：当前是图片维护模式，不会创建或更新车型配置。新增车型请先粘贴并校验单车型 JSON。');
      }
    }

    const completed=publishStarted&&!config.pending&&images.pendingRows===0&&images.badRows===0&&(
      (images.rows>0&&images.doneRows===images.rows)||(images.rows===0&&bundleState.valid)
    );

    if(completed){
      button.disabled=true;
      setText(button,'已发布完成');
      if((log.textContent||'').includes('预检通过'))setText(log,'本次发布已完成，无待发布项。');
      log.className='publish-log ok';
      return;
    }

    if(!bundleState.hasJson&&images.pendingAssets>0){
      if(!button.disabled)setText(button,`仅发布 ${images.pendingAssets} 个图片资源`);
      if((log.textContent||'')==='预检通过，可以发布'){
        setText(log,`图片维护模式预检通过：本次只发布 ${images.pendingAssets} 个图片资源，不会创建或更新车型。新增车型请先在 STEP 1 提供 JSON。`);
        log.className='publish-log warn';
      }
      return;
    }

    if(bundleState.valid&&(config.pending||images.pendingAssets>0)){
      if(!button.disabled)setText(button,'确认并发布整套资源');
      if((log.textContent||'')==='预检通过，可以发布'){
        setText(log,`预检通过：${config.label}；待发布图片 ${images.pendingAssets} 项。`);
        log.className='publish-log ok';
      }
    }
  }

  function schedule(){
    if(scheduled)return;
    scheduled=true;
    queueMicrotask(render);
  }

  async function refreshCatalog(){
    if(refreshing)return;
    refreshing=true;
    try{
      const response=await fetch('../api/catalog/state',{headers:{Accept:'application/json'}});
      if(response.ok)catalog=await response.json();
    }catch(_){
      // The core workbench already owns runtime error reporting.
    }finally{
      refreshing=false;
      schedule();
    }
  }

  button.addEventListener('click',()=>{
    publishStarted=true;
    schedule();
    setTimeout(refreshCatalog,800);
    setTimeout(refreshCatalog,3000);
  },true);
  bundleInput.addEventListener('input',()=>{publishStarted=false;schedule()});
  $('validateBundle')?.addEventListener('click',()=>setTimeout(schedule,0));
  $('refreshState')?.addEventListener('click',()=>setTimeout(refreshCatalog,300));

  const observer=new MutationObserver(()=>{
    if(publishStarted){
      const images=imagePlan();
      if(images.pendingRows===0&&!refreshing)void refreshCatalog();
    }
    schedule();
  });
  observer.observe(queue,{subtree:true,childList:true,attributes:true,attributeFilter:['class','value']});
  observer.observe(log,{subtree:true,childList:true,characterData:true});
  observer.observe(bundleSummary,{subtree:true,childList:true,characterData:true});

  void refreshCatalog();
  schedule();
})();
