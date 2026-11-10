(async()=>{'use strict';
  const auth=window.SafeBoxAuth;if(!await auth.requireRole('FACILITY_MANAGER','BUSINESS_OPERATIONS_MANAGER'))return;
  if(auth.getCurrentUser()?.role==='BUSINESS_OPERATIONS_MANAGER'){location.replace('/stitch/business-reports.html');return;}
  const $=id=>document.getElementById(id),esc=v=>String(v??'').replace(/[&<>"']/g,c=>({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));
  const money=v=>new Intl.NumberFormat('vi-VN').format(Number(v||0))+' đ';
  const {todayISODateVN,formatDateVN,toISODate}=SafeBoxUc12Time;
  const today=todayISODateVN();
  $('end-date').value=formatDateVN(today);$('start-date').value=formatDateVN(today.slice(0,8)+'01');
  const role=auth.getCurrentUser()?.role,business=role==='BUSINESS_OPERATIONS_MANAGER';if ($('facilities-link')) $('facilities-link').hidden=!business;
  let names=new Map(),latest=null;
  async function api(url){const r=await auth.authFetch(url),d=await r.json().catch(()=>null);if(!r.ok)throw Error(d?.message||'Không thể tải báo cáo.');return d}
  async function scope(){try{const [ids,facilities]=await Promise.all([api('/api/reports/facilities'),api(business?'/api/business/facilities':'/api/manager/facilities')]);names=new Map(facilities.map(f=>[f.facilityId,f.name]));ids.forEach(id=>{const o=document.createElement('option');o.value=id;o.textContent=names.get(id)||id;$('facility').append(o)});$('report-state').textContent=ids.length?'Chọn kỳ báo cáo để xem dữ liệu.':'Chưa được cấp phạm vi báo cáo.';if(ids.length)await load()}catch(e){$('report-state').textContent=e.message}}
  function chart(id,points,value,max,label){$(id).innerHTML=points.length?points.map(p=>{const n=Number(value(p)||0),h=max?Math.max(2,Math.round(n/max*155)):2;return `<div class="chart-col"><span title="${esc(label(p))}: ${esc(n.toLocaleString('vi-VN'))}" class="chart-bar" style="height:${h}px"></span><small>${esc(label(p))}</small></div>`}).join(''):'<p class="muted">Chưa có dữ liệu trong kỳ này.</p>'}
  function kpi(label,value,detail=''){return `<article class="sb-card kpi"><span>${esc(label)}</span><strong>${esc(value)}</strong>${detail?`<small class="muted">${esc(detail)}</small>`:''}</article>`}
  async function load(){let q;try{q=new URLSearchParams({startDate:toISODate($('start-date').value),endDate:toISODate($('end-date').value)});}catch(e){$('report-state').textContent=e.message;return;}if($('facility').value)q.set('facilityId',$('facility').value);
    $('report-state').textContent='Đang tải báo cáo...';$('results').hidden=true;$('csv').disabled=true;
    try{const [f,t,p,o]=await Promise.all([api('/api/reports/financial-summary?'+q),api('/api/reports/revenue-trend?'+q+'&grouping='+$('grouping').value),api('/api/reports/facility-performance?'+q),api('/api/reports/operational-summary?'+q)]);
      latest={financial:f,trend:t,performance:p,operational:o};
      $('kpis').innerHTML=[kpi('Tiền thuê đã thu',money(f.rentalCollections),'Giao dịch đã ghi nhận, không gồm tiền cọc'),kpi('Tiền cọc đã thu',money(f.depositCollections)),kpi('Công nợ chưa thu',money(f.outstandingReceivables)),kpi('Đã hoàn tiền',money(f.refundOutflow)),kpi('Hợp đồng đang thuê',o.activeContracts),kpi('Sắp hết hạn trong 30 ngày',o.expiringWithin30Days),kpi('Hợp đồng quá hạn',o.overdueActiveContracts),kpi('Công nợ quá hạn',money(f.overdueReceivables))].join('');
      chart('revenue-chart',t.data,x=>x.rentalCollections,Math.max(0,...t.data.map(x=>Number(x.rentalCollections||0))),x=>formatDateVN(x.periodStart));
      chart('occupancy-chart',p,x=>x.occupancyRate,100,x=>names.get(x.facilityId)||x.facilityId.slice(0,8));
      $('facility-rows').innerHTML=p.map(x=>`<tr><td>${esc(names.get(x.facilityId)||x.facilityId)}</td><td>${x.totalUnits}</td><td>${x.occupiedUnits}</td><td>${x.availableUnits}</td><td>${x.maintenanceUnits}</td><td>${esc(x.occupancyRate)}%</td><td>${money(x.confirmedCollections)}</td><td>${money(x.outstandingReceivables)}</td></tr>`).join('')||'<tr><td colspan="8">Chưa có cơ sở trong phạm vi báo cáo.</td></tr>';
      $('report-state').textContent=`Dữ liệu từ ${formatDateVN(f.startDate)} đến ${formatDateVN(f.endDate)}. Thu tiền ghi nhận trong hệ thống có thể là mô phỏng.`;$('results').hidden=false;$('csv').disabled=false;
    }catch(e){$('report-state').textContent=e.message}}
  $('report-form').onsubmit=e=>{e.preventDefault();load()};$('csv').onclick=()=>{if(!latest)return;const lines=[['Cơ sở','Kho vật lý','Đang thuê','Còn trống','Bảo trì','Tỷ lệ sử dụng (%)','Thu đã ghi nhận','Công nợ'],...latest.performance.map(x=>[names.get(x.facilityId)||x.facilityId,x.totalUnits,x.occupiedUnits,x.availableUnits,x.maintenanceUnits,x.occupancyRate,x.confirmedCollections,x.outstandingReceivables])];const csv='\ufeff'+lines.map(row=>row.map(v=>'"'+String(v??'').replaceAll('"','""')+'"').join(',')).join('\r\n');const a=document.createElement('a'),url=URL.createObjectURL(new Blob([csv],{type:'text/csv;charset=utf-8'}));a.href=url;a.download='safebox-bao-cao-co-so.csv';a.click();setTimeout(()=>URL.revokeObjectURL(url),1000)};
  document.body.style.visibility='visible';await scope();
})();
