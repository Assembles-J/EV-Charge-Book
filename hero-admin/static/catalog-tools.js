(function(){
  const FORMAT_NAME='ev-charge-book-vehicle-catalog';
  const FORMAT_VERSION=1;
  const VEHICLE_FIELDS=['catalogId','brandId','series','modelName','modelYear','trimName','powertrainType','batteryCapacityKwh','rangeKm','rangeStandard','heroArtworkKey','isActive'];

  function uniqueHeroKeys(){
    const keys=new Set(Object.keys((manifest&&manifest.artworks)||{}));
    for(const vehicle of (catalog&&catalog.vehicles)||[]){if(vehicle.heroArtworkKey)keys.add(vehicle.heroArtworkKey)}
    return [...keys].sort();
  }

  function renderVehicleHeroKeyOptions(selected){
    const select=$('vehicleHeroKeySelect');
    if(!select)return;
    const previous=selected!==undefined?selected:select.value;
    select.innerHTML='';
    const empty=document.createElement('option');empty.value='';empty.textContent='未配置 Hero';select.appendChild(empty);
    for(const key of uniqueHeroKeys()){
      const option=document.createElement('option');option.value=key;
      const published=manifest&&manifest.artworks&&manifest.artworks[key];
      option.textContent=published?`${key} · v${published.version}`:`${key} · 未发布图片`;
      select.appendChild(option);
    }
    if(previous&&!uniqueHeroKeys().includes(previous)){
      const legacy=document.createElement('option');legacy.value=previous;legacy.textContent=`${previous} · 当前车型引用`;select.appendChild(legacy);
    }
    if(previous)select.value=previous;
  }

  const baseRenderHeroKeys=renderHeroKeys;
  renderHeroKeys=function(){baseRenderHeroKeys();renderVehicleHeroKeyOptions()};
  const baseOpenVehicle=openVehicle;
  openVehicle=function(vehicle){renderVehicleHeroKeyOptions(vehicle&&vehicle.heroArtworkKey||'');baseOpenVehicle(vehicle)};

  function selectedLogoBrand(){
    const select=$('brandLogoBrandSelect');
    return ((catalog&&catalog.brands)||[]).find(brand=>brand.brandId===select.value)||null;
  }

  function renderBrandLogoCenter(){
    const select=$('brandLogoBrandSelect');
    if(!select||!catalog)return;
    const previous=select.value;
    select.innerHTML='';
    for(const brand of catalog.brands||[]){
      const option=document.createElement('option');option.value=brand.brandId;option.textContent=`${brand.name}${brand.isActive===false?' · 已下架':''}`;select.appendChild(option);
    }
    if(previous&&[...select.options].some(option=>option.value===previous))select.value=previous;
    renderBrandLogoPreview();
  }

  function renderBrandLogoPreview(){
    const brand=selectedLogoBrand();
    for(const variant of ['Light','Dark']){
      const key=variant.toLowerCase();
      const img=$(`brandLogo${variant}Preview`);const meta=$(`brandLogo${variant}Meta`);const button=$(`brandLogo${variant}Button`);
      if(!img||!meta||!button)continue;
      const url=brand&&brand[`logo${variant}Url`];const version=brand&&brand[`logo${variant}Version`];
      if(url){img.src=url;img.classList.remove('hidden');meta.textContent=`已配置 · v${version||0} · 512×512 WebP`;button.textContent=`替换${key==='light'?'浅色':'深色'} Logo`}
      else{img.removeAttribute('src');img.classList.add('hidden');meta.textContent='尚未配置';button.textContent=`上传${key==='light'?'浅色':'深色'} Logo`}
      button.disabled=!brand;
    }
    const note=$('brandLogoReuseNote');if(note)note.textContent=brand?`${brand.name} 的 Logo 会自动复用于该 brandId 下的所有车型，无需逐车型配置。`:'请先选择品牌。';
  }

  if($('brandLogoBrandSelect'))$('brandLogoBrandSelect').onchange=renderBrandLogoPreview;
  if($('brandLogoLightButton'))$('brandLogoLightButton').onclick=()=>{const brand=selectedLogoBrand();if(brand)chooseBrandLogo(brand,'light')};
  if($('brandLogoDarkButton'))$('brandLogoDarkButton').onclick=()=>{const brand=selectedLogoBrand();if(brand)chooseBrandLogo(brand,'dark')};

  const baseLoadCatalog=loadCatalog;
  loadCatalog=async function(){const result=await baseLoadCatalog();renderBrandLogoCenter();renderVehicleHeroKeyOptions();return result};

  if($('manageHeroFromVehicle'))$('manageHeroFromVehicle').onclick=()=>{
    const select=$('vehicleHeroKeySelect');const key=select&&select.value||'';
    closeVehicleDialog();switchTab('hero');$('artworkKey').value=key;renderHeroCurrent();$('artworkKey').focus();
  };

  function portableCatalog(){
    return {
      format:FORMAT_NAME,
      version:FORMAT_VERSION,
      exportedAt:new Date().toISOString(),
      note:'Images are intentionally excluded. Brand Logo and Hero binaries are uploaded separately.',
      brands:(catalog.brands||[]).map(brand=>({brandId:brand.brandId,name:brand.name,englishName:brand.englishName||null,isActive:brand.isActive!==false})),
      vehicles:(catalog.vehicles||[]).map(vehicle=>Object.fromEntries(VEHICLE_FIELDS.map(field=>[field,vehicle[field]===undefined?null:vehicle[field]])))
    };
  }

  function downloadText(content,name,type){const blob=new Blob([content],{type});downloadBlob(blob,name)}
  function stamp(){return new Date().toISOString().slice(0,10)}
  if($('exportCatalogJson'))$('exportCatalogJson').onclick=()=>downloadText(JSON.stringify(portableCatalog(),null,2)+"\n",`ev-charge-book-vehicle-catalog-${stamp()}.json`,'application/json;charset=utf-8');

  function csvCell(value){
    if(value===null||value===undefined)return '';
    const text=String(value);return /[",\n\r]/.test(text)?`"${text.replace(/"/g,'""')}"`:text;
  }
  function vehicleCsv(){
    const headers=['brandId','brandName','brandEnglishName',...VEHICLE_FIELDS.filter(field=>field!=='brandId')];
    const brands=new Map((catalog.brands||[]).map(brand=>[brand.brandId,brand]));
    const rows=[headers.join(',')];
    for(const vehicle of catalog.vehicles||[]){
      const brand=brands.get(vehicle.brandId)||{};
      const values=[vehicle.brandId,brand.name||vehicle.brand||'',brand.englishName||'',...VEHICLE_FIELDS.filter(field=>field!=='brandId').map(field=>vehicle[field])];
      rows.push(values.map(csvCell).join(','));
    }
    return '\ufeff'+rows.join('\r\n')+'\r\n';
  }
  if($('exportCatalogCsv'))$('exportCatalogCsv').onclick=()=>downloadText(vehicleCsv(),`ev-charge-book-vehicle-models-${stamp()}.csv`,'text/csv;charset=utf-8');

  function csvTemplate(){
    const headers=['brandId','brandName','brandEnglishName',...VEHICLE_FIELDS.filter(field=>field!=='brandId')];
    const sample=['leapmotor','零跑','Leapmotor','leap-c16-2026-example','C16','C16 2026款','2026','示例配置','REEV','67.7','520','CLTC','leapmotor-c16-2026','true'];
    return '\ufeff'+headers.join(',')+'\r\n'+sample.map(csvCell).join(',')+'\r\n';
  }
  if($('downloadCatalogTemplate'))$('downloadCatalogTemplate').onclick=()=>downloadText(csvTemplate(),'ev-charge-book-vehicle-import-template.csv','text/csv;charset=utf-8');

  function parseCsv(text){
    const rows=[];let row=[],cell='',quoted=false;
    for(let i=0;i<text.length;i++){
      const ch=text[i];
      if(quoted){if(ch==='"'&&text[i+1]==='"'){cell+='"';i++}else if(ch==='"'){quoted=false}else{cell+=ch}}
      else if(ch==='"'){quoted=true}else if(ch===','){row.push(cell);cell=''}else if(ch==='\n'){row.push(cell);rows.push(row);row=[];cell=''}else if(ch!=='\r'){cell+=ch}
    }
    if(cell.length||row.length){row.push(cell);rows.push(row)}
    if(!rows.length)return [];
    const headers=rows.shift().map((value,index)=>index===0?value.replace(/^\ufeff/,'').trim():value.trim());
    return rows.filter(values=>values.some(value=>value.trim()!=='')).map(values=>Object.fromEntries(headers.map((header,index)=>[header,(values[index]||'').trim()])));
  }
  function boolValue(value,defaultValue=true){if(value===true||value==='true'||value==='1'||value==='yes'||value==='是')return true;if(value===false||value==='false'||value==='0'||value==='no'||value==='否')return false;return defaultValue}
  function nullableNumber(value){return value===null||value===undefined||String(value).trim()===''?null:Number(value)}

  function normalizeJsonImport(data){
    if(!data||!Array.isArray(data.brands)||!Array.isArray(data.vehicles))throw new Error('JSON 必须包含 brands 和 vehicles 数组');
    if(data.format&&data.format!==FORMAT_NAME)throw new Error(`不支持的配置格式：${data.format}`);
    return {
      brands:data.brands.map(brand=>({brandId:String(brand.brandId||'').trim().toLowerCase(),name:String(brand.name||'').trim(),englishName:brand.englishName?String(brand.englishName).trim():'',isActive:boolValue(brand.isActive,true)})),
      vehicles:data.vehicles.map(vehicle=>({
        catalogId:String(vehicle.catalogId||'').trim().toLowerCase(),brandId:String(vehicle.brandId||'').trim().toLowerCase(),series:String(vehicle.series||'').trim(),modelName:String(vehicle.modelName||'').trim(),modelYear:nullableNumber(vehicle.modelYear),trimName:vehicle.trimName?String(vehicle.trimName).trim():'',powertrainType:String(vehicle.powertrainType||'').trim().toUpperCase(),batteryCapacityKwh:nullableNumber(vehicle.batteryCapacityKwh),rangeKm:nullableNumber(vehicle.rangeKm),rangeStandard:vehicle.rangeStandard?String(vehicle.rangeStandard).trim().toUpperCase():'',heroArtworkKey:vehicle.heroArtworkKey?String(vehicle.heroArtworkKey).trim().toLowerCase():'',isActive:boolValue(vehicle.isActive,true)
      }))
    };
  }

  function normalizeCsvImport(text){
    const records=parseCsv(text);if(!records.length)throw new Error('CSV 没有车型数据');
    const brands=new Map();const vehicles=[];
    for(const record of records){
      const brandId=String(record.brandId||'').trim().toLowerCase();const name=String(record.brandName||'').trim();
      if(!brandId||!name)throw new Error('CSV 每行都必须包含 brandId 和 brandName');
      if(!brands.has(brandId))brands.set(brandId,{brandId,name,englishName:String(record.brandEnglishName||'').trim(),isActive:true});
      vehicles.push({catalogId:String(record.catalogId||'').trim().toLowerCase(),brandId,series:String(record.series||'').trim(),modelName:String(record.modelName||'').trim(),modelYear:nullableNumber(record.modelYear),trimName:String(record.trimName||'').trim(),powertrainType:String(record.powertrainType||'').trim().toUpperCase(),batteryCapacityKwh:nullableNumber(record.batteryCapacityKwh),rangeKm:nullableNumber(record.rangeKm),rangeStandard:String(record.rangeStandard||'').trim().toUpperCase(),heroArtworkKey:String(record.heroArtworkKey||'').trim().toLowerCase(),isActive:boolValue(record.isActive,true)});
    }
    return {brands:[...brands.values()],vehicles};
  }

  async function apiJson(url,body){const response=await fetch(url,{method:'POST',headers:adminHeaders,body:JSON.stringify(body)});const data=await response.json();if(!response.ok)throw new Error(data.error||`${url} 请求失败`);return data}
  async function importCatalogPackage(pkg){
    const total=pkg.brands.length+pkg.vehicles.length;let done=0;
    for(const brand of pkg.brands){
      setStatus($('catalogStatus'),`正在导入品牌 ${++done}/${total} · ${brand.brandId}`);
      await apiJson('api/brand/save',{brandId:brand.brandId,name:brand.name,englishName:brand.englishName||''});
      await apiJson('api/brand/status',{brandId:brand.brandId,isActive:brand.isActive!==false});
    }
    for(const vehicle of pkg.vehicles){
      setStatus($('catalogStatus'),`正在导入车型 ${++done}/${total} · ${vehicle.catalogId}`);
      await apiJson('api/catalog/save',vehicle);
      await apiJson('api/catalog/status',{catalogId:vehicle.catalogId,isActive:vehicle.isActive!==false});
    }
    await loadCatalog();setStatus($('catalogStatus'),`导入完成 · ${pkg.brands.length} 个品牌 · ${pkg.vehicles.length} 个车型。Logo/Hero 图片未导入，继续在资源页单独上传。`,'ok');
  }

  if($('importCatalog'))$('importCatalog').onclick=()=>$('catalogImportFile').click();
  if($('catalogImportFile'))$('catalogImportFile').onchange=async event=>{
    const file=event.target.files&&event.target.files[0];event.target.value='';if(!file)return;
    try{
      const text=await file.text();const pkg=/\.csv$/i.test(file.name)?normalizeCsvImport(text):normalizeJsonImport(JSON.parse(text));
      if(!pkg.brands.length||!pkg.vehicles.length)throw new Error('导入包至少需要 1 个品牌和 1 个车型');
      if(!confirm(`准备合并导入 ${pkg.brands.length} 个品牌、${pkg.vehicles.length} 个车型。\n\n相同 ID 会更新；未出现在文件中的现有数据不会删除。Logo/Hero 图片二进制不会导入。\n\n继续？`))return;
      await importCatalogPackage(pkg);
    }catch(error){setStatus($('catalogStatus'),`导入失败：${error.message||error}`,'error')}
  };

  const initTimer=setInterval(()=>{if(catalog){clearInterval(initTimer);renderBrandLogoCenter();renderVehicleHeroKeyOptions()}},100);
})();
