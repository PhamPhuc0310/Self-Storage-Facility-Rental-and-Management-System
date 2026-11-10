(async()=>{
  if(!await SafeBoxAuth.requireRole('BUSINESS_OPERATIONS_MANAGER','FACILITY_MANAGER'))return;
  if(SafeBoxAuth.getCurrentUser().role!=='BUSINESS_OPERATIONS_MANAGER')return;
  const $=id=>document.getElementById(id), date=SafeBoxUc12Time.formatDateVN, time=SafeBoxUc12Time.formatDateTimeVN;
  const esc=v=>String(v??'').replace(/[&<>"']/g,c=>({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));
  const url='/api/business/overdue-policies/global';
  async function api(path,options){const r=await SafeBoxAuth.authFetch(path,options),d=await r.json().catch(()=>null);if(!r.ok)throw Error(d?.message||'Không thể xử lý chính sách.');return d}
  let versions=[];
  function period(){ $('global-effective-preview').textContent='Kỳ hiệu lực: '+date($('global-from').value)+' → '+($('global-to').value?date($('global-to').value):'Không giới hạn'); }
  function card(p){return `<article><strong>${p.overdueVersion?'Phiên bản '+esc(p.overdueVersion):'Cấu hình chưa có phiên bản duyệt'}</strong><p>Ân hạn ${esc(p.overdueGraceDays)} ngày · ${p.penaltyType==='FIXED_DAILY'?Number(p.fixedPenaltyAmount).toLocaleString('vi-VN')+' đ/ngày':Number(p.dailyPenaltyRate*100).toLocaleString('vi-VN')+'% đơn giá thuê ngày'}</p><p>Hiệu lực ${date(p.effectiveFrom)} → ${p.effectiveTo?date(p.effectiveTo):'Không giới hạn'} · ${p.status==='ACTIVE'?'Được áp dụng theo kỳ hiệu lực':'Ngừng áp dụng'}</p><small>${p.overdueApprovedBy&&p.overdueApprovedAt?'Đã duyệt lúc '+time(p.overdueApprovedAt):'Chưa được duyệt để tính phí'}</small></article>`}
  function fill(){const p=versions.find(x=>x.policyId===$('global-base').value);if(!p)return;$('global-grace').value=p.overdueGraceDays;$('global-type').value=p.penaltyType;$('global-rate').value=p.dailyPenaltyRate;$('global-fixed').value=p.fixedPenaltyAmount}
  async function load(){
    versions=await api(url);const today=SafeBoxUc12Time.todayISODateVN();
    const current=versions.filter(p=>p.status==='ACTIVE'&&p.effectiveFrom<=today&&(!p.effectiveTo||p.effectiveTo>=today));
    $('global-current').innerHTML=current.filter(p=>p.overdueApprovedBy&&p.overdueApprovedAt).map(card).join('')||'<p>Chưa có chính sách toàn hệ thống đã duyệt và có hiệu lực.</p>';
    $('global-history').innerHTML=versions.map(card).join('')||'<p>Chưa có lịch sử.</p>';
    $('global-base').replaceChildren(...current.map(p=>new Option((p.overdueVersion?'Phiên bản '+p.overdueVersion:'Cấu hình gốc chưa duyệt')+' · '+date(p.effectiveFrom),p.policyId)));
    $('global-from').min=today;$('global-from').value=today;period();fill();$('global-activate').disabled=!current.length;
  }
  $('global-overdue').hidden=false;$('global-base').onchange=fill;
  $('global-from').oninput=period;$('global-to').oninput=period;
  $('global-form').onsubmit=async e=>{
    e.preventDefault();const body={basePolicyId:$('global-base').value,overdueGraceDays:Number($('global-grace').value),penaltyType:$('global-type').value,dailyPenaltyRate:Number($('global-rate').value),fixedPenaltyAmount:Number($('global-fixed').value),effectiveFrom:$('global-from').value,effectiveTo:$('global-to').value||null,reason:$('global-reason').value.trim()};
    if(body.effectiveTo&&body.effectiveTo<body.effectiveFrom){$('global-message').textContent='Ngày kết thúc phải từ ngày bắt đầu trở đi.';return}
    const formula=body.penaltyType==='FIXED_DAILY'?body.fixedPenaltyAmount.toLocaleString('vi-VN')+' đ/ngày':(body.dailyPenaltyRate*100).toLocaleString('vi-VN')+'% đơn giá thuê ngày';
    if(!confirm(`Kích hoạt chính sách toàn hệ thống?\nÂn hạn: ${body.overdueGraceDays} ngày\nMức phạt: ${formula}\nHiệu lực: ${date(body.effectiveFrom)} → ${body.effectiveTo?date(body.effectiveTo):'Không giới hạn'}\nLý do: ${body.reason}`))return;
    $('global-activate').disabled=true;
    try{await api(url+'/activate',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify(body)});$('global-message').textContent='Đã kích hoạt phiên bản mới theo kỳ hiệu lực.';$('global-form').reset();await load()}
    catch(error){$('global-message').textContent=error.message}
    finally{$('global-activate').disabled=!$('global-base').value}
  };
  try{await load()}catch(error){$('global-message').textContent=error.message;$('global-activate').disabled=true}
})();
