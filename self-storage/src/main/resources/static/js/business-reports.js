(async()=>{
  if(!await SafeBoxAuth.requireRole('BUSINESS_OPERATIONS_MANAGER'))return;
  const {esc,mount,drawer,api}=SafeBoxGrid,$=id=>document.getElementById(id),{todayISODateVN,formatDateVN,toISODate}=SafeBoxUc12Time;
  const money=v=>new Intl.NumberFormat('vi-VN').format(Number(v||0))+' đ';
  const today=todayISODateVN();$('end-date').value=formatDateVN(today);$('start-date').value=formatDateVN(today.slice(0,8)+'01');
  let names=new Map(),latest=null,tab='overview',scoped=false,detailColumns=[];
  const name=id=>names.get(id)||'Cơ sở được cấp',open=drawer($('detail'));
  const col=(label,key,format)=>({label,value:x=>x[key],...(format?{render:x=>esc(format(x[key]))}:{})});
  const facility={label:'Cơ sở',value:x=>name(x.facilityId)};
  function render(){
    if(!latest)return;const {f,t,p,o}=latest;
    $('overview').hidden=tab!=='overview';$('table-panel').hidden=tab==='overview';
    document.querySelectorAll('[data-tab]').forEach(b=>b.setAttribute('aria-pressed',String(b.dataset.tab===tab)));
    let rows,columns;
    if(tab==='revenue'){
      rows=t.data;columns=[{label:'Kỳ báo cáo',value:x=>x.periodStart,render:x=>esc(formatDateVN(x.periodStart))},col('Tiền thuê đã thu','rentalCollections',money),col('Tiền cọc đã thu','depositCollections',money),col('Khoản thu khác','otherCollections',money),col('Chưa phân loại','unclassifiedCollections',money),col('Hoàn tiền','refundOutflow',money)];
      $('report-note').textContent='Khoản thu được tổng hợp theo ngày hoặc tháng trong kỳ đã chọn; không phải danh sách giao dịch.';
    }else if(tab==='occupancy'){
      rows=p;columns=[facility,col('Kho vật lý','totalUnits'),col('Đang thuê','occupiedUnits'),col('Còn trống','availableUnits'),col('Bảo trì','maintenanceUnits'),col('Công suất','occupancyRate',v=>v+'%')];
      $('report-note').textContent='Công suất phản ánh tình trạng kho hiện tại trong phạm vi được cấp.';
    }else if(tab==='contracts'){
      rows=p;columns=[facility,col('Hợp đồng đang thuê','activeContracts')];
      $('report-note').textContent=`Toàn phạm vi đã chọn: ${o.expiringWithin30Days} hợp đồng sắp hết hạn trong 30 ngày, ${o.overdueActiveContracts} quá hạn, ${o.pendingReturns} đang chờ trả, ${o.completedReturnsInPeriod} lượt trả hoàn tất trong kỳ. API chỉ cung cấp số tổng hợp, chưa có danh sách chi tiết hợp đồng.`;
    }else if(tab==='payments'){
      rows=p;columns=[facility,col('Thu đã ghi nhận','confirmedCollections',money),col('Công nợ chưa thu','outstandingReceivables',money)];
      $('report-note').textContent=`Toàn phạm vi đã chọn: tiền cọc ${money(f.depositCollections)}, hoàn tiền ${money(f.refundOutflow)}, công nợ quá hạn ${money(f.overdueReceivables)}, biến động tiền ròng ${money(f.netCashMovement)}. API báo cáo chưa cung cấp chi tiết từng giao dịch.`;
    }else{
      $('report-note').textContent='Số liệu lấy từ giao dịch đã ghi nhận. Tiền thuê, tiền cọc và hoàn tiền được trình bày riêng. Chọn nhóm báo cáo để xem bảng chi tiết. Biểu đồ hiển thị tối đa 12 kỳ và 8 cơ sở; bảng chứa đầy đủ dữ liệu đã tải.';return;
    }
    $('report-grid').dataset.caption='Báo cáo trong phạm vi đã chọn';
    const grid=mount($('report-grid'),columns,{search:$('report-search')});grid.set(rows);
    detailColumns=columns;
  }
  $('report-grid').addEventListener('grid-open',e=>{const x=e.detail;const columns=detailColumns;open(`<dl>${columns.map(c=>`<dt>${esc(c.label)}</dt><dd>${c.render?c.render(x):esc(c.value(x))}</dd>`).join('')}</dl>`)});
  function charts(t,p){
    const max=Math.max(0,...t.data.map(x=>Number(x.rentalCollections||0)));
    $('revenue-chart').innerHTML=t.data.length?t.data.slice(-12).map(x=>`<div><div class="chart-label"><span>${esc(formatDateVN(x.periodStart))}</span><strong>${money(x.rentalCollections)}</strong></div><div class="chart-track"><div class="chart-fill" style="width:${max?Math.max(0,Number(x.rentalCollections)/max*100):0}%"></div></div></div>`).join(''):'<p class="notice">Chưa có khoản thu trong kỳ.</p>';
    $('occupancy-chart').innerHTML=p.length?p.slice(0,8).map(x=>`<div><div class="chart-label"><span>${esc(name(x.facilityId))}</span><strong>${esc(x.occupancyRate)}%</strong></div><meter min="0" max="100" value="${Number(x.occupancyRate)||0}" aria-label="${esc(name(x.facilityId))}"></meter></div>`).join(''):'<p class="notice">Chưa có cơ sở trong phạm vi.</p>';
  }
  async function load(){
    if(!scoped)return;
    let q;try{const start=toISODate($('start-date').value),end=toISODate($('end-date').value);if(start>end)throw Error('Ngày kết thúc phải từ ngày bắt đầu trở đi.');q=new URLSearchParams({startDate:start,endDate:end});}catch(e){$('report-state').textContent=e.message;latest=null;$('csv').disabled=true;$('results').hidden=true;return}
    if($('facility').value)q.set('facilityId',$('facility').value);
    latest=null;$('detail').close();$('results').hidden=true;$('csv').disabled=true;$('apply').disabled=true;$('retry').hidden=true;$('results').setAttribute('aria-busy','true');$('report-state').textContent='Đang tải báo cáo...';
    try{
      const [f,t,p,o]=await Promise.all([api('/api/reports/financial-summary?'+q),api('/api/reports/revenue-trend?'+q+'&grouping='+$('grouping').value),api('/api/reports/facility-performance?'+q),api('/api/reports/operational-summary?'+q)]);
      latest={f,t,p,o};$('kpis').innerHTML=[['Tiền thuê đã thu',money(f.rentalCollections)],['Công nợ chưa thu',money(f.outstandingReceivables)],['Hợp đồng đang thuê',o.activeContracts],['Sắp hết hạn trong 30 ngày',o.expiringWithin30Days]].map(([label,value])=>`<article class="sb-card kpi"><span>${esc(label)}</span><strong>${esc(value)}</strong></article>`).join('');
      charts(t,p);render();$('results').hidden=false;$('csv').disabled=false;$('report-state').textContent=`Dữ liệu từ ${formatDateVN(f.startDate)} đến ${formatDateVN(f.endDate)} · ${p.length} cơ sở trong phạm vi báo cáo.`;
    }catch(e){$('report-state').textContent=e.message;$('retry').hidden=false}finally{$('apply').disabled=false;$('results').setAttribute('aria-busy','false')}
  }
  async function scope(){
    $('retry').hidden=true;
    try{const [ids,facilities]=await Promise.all([api('/api/reports/facilities'),api('/api/business/facilities')]);names=new Map(facilities.filter(f=>ids.includes(f.facilityId)).map(f=>[f.facilityId,f.name]));$('facility').innerHTML='<option value="">Tất cả cơ sở được cấp</option>';ids.forEach(id=>$('facility').add(new Option(name(id),id)));scoped=ids.length>0;$('apply').disabled=!scoped;if(scoped)await load();else $('report-state').textContent='Chưa được cấp phạm vi báo cáo. Liên hệ quản trị viên để được cấp cơ sở.'}catch(e){$('report-state').textContent=e.message;$('retry').hidden=false}
  }
  document.querySelectorAll('[data-tab]').forEach(b=>b.onclick=()=>{tab=b.dataset.tab;$('report-search').value='';render()});
  $('report-form').onsubmit=e=>{e.preventDefault();load()};$('retry').onclick=()=>scoped?load():scope();
  $('csv').onclick=()=>{
    if(!latest)return;
    const lines=[['Cơ sở','Kho vật lý','Đang thuê','Còn trống','Bảo trì','Tỷ lệ sử dụng (%)','Thu đã ghi nhận','Công nợ'],...latest.p.map(x=>[name(x.facilityId),x.totalUnits,x.occupiedUnits,x.availableUnits,x.maintenanceUnits,x.occupancyRate,x.confirmedCollections,x.outstandingReceivables])];
    const csv='\ufeff'+lines.map(row=>row.map(v=>'"'+String(v??'').replaceAll('"','""')+'"').join(',')).join('\r\n'),url=URL.createObjectURL(new Blob([csv],{type:'text/csv;charset=utf-8'})),a=document.createElement('a');a.href=url;a.download='safebox-bao-cao-co-so.csv';a.click();setTimeout(()=>URL.revokeObjectURL(url),1000);
  };
  await scope();
})();
