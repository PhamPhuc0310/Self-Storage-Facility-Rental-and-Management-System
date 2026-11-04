(() => {'use strict';
  const $=id=>document.getElementById(id), esc=v=>String(v??'').replace(/[&<>"']/g,c=>({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));
  let selected=null,units=[];
  async function api(path,options){const r=await SafeBoxAuth.authFetch(path,options),d=await r.json().catch(()=>null);if(!r.ok)throw Error(d?.message||'Không thể tải dữ liệu.');return d}
  async function load(){const id=$('facility').value;if(!id)return;try{
    const [rows,history]=await Promise.all([api(`/api/staff/measurements/facilities/${id}/units`),api(`/api/staff/measurements/facilities/${id}/submissions`)]);
    units=rows;$('units').innerHTML=rows.length?rows.map(u=>`<article class="sb-card"><h3>${esc(u.unitNumber)}</h3><p>${esc({AVAILABLE:'Đang mở cho thuê',INACTIVE:'Chưa mở cho thuê',MAINTENANCE:'Đang bảo trì'}[u.status]||u.status)} · Tầng ${esc(u.floor||'—')} · Khu ${esc(u.zone||'—')}</p><p>Thể tích: chưa xác minh</p><button type="button" class="sb-button measure" data-id="${esc(u.unitId)}">Gửi số đo</button></article>`).join(''):'<p>Không có kho chờ xác minh.</p>';
    $('history').innerHTML=history.length?history.map(s=>`<article class="sb-card"><strong>${esc(rows.find(u=>u.unitId===s.unitId)?.unitNumber||s.unitId)}</strong> · ${esc({SUBMITTED:'Chờ duyệt',APPROVED:'Đã duyệt',REJECTED:'Đã từ chối'}[s.status]||s.status)}<p>${esc(s.lengthM)} × ${esc(s.widthM)} × ${esc(s.heightM)} m · ${esc(s.conditionNote)}</p><p>${s.source==='SYNTHETIC_DEMO'?'Mô phỏng demo, không đo thực địa':'Đo thực địa'}</p>${s.decisionReason?`<p>Lý do: ${esc(s.decisionReason)}</p>`:''}</article>`).join(''):'<p>Chưa có phiếu số đo.</p>';
    $('message').textContent='';
  }catch(e){$('message').textContent=e.message}}
  $('facility').onchange=load;
  $('units').onclick=e=>{const b=e.target.closest('.measure');if(!b)return;selected=units.find(u=>u.unitId===b.dataset.id);$('unit-title').textContent=`Ghi nhận số đo ${selected.unitNumber}`;for(const k of ['length','width','height','condition'])$(k).value='';$('source').value='PHYSICAL_INSPECTION';$('form-message').textContent='';$('measure-dialog').showModal()};
  $('cancel').onclick=()=>$('measure-dialog').close();
  const volume=()=>{const v=['length','width','height'].map(k=>Number($(k).value));$('volume').textContent=v.every(n=>n>0)?`Thể tích đề xuất: ${(v[0]*v[1]*v[2]).toFixed(3)} m³`:'Thể tích: —'};
  for(const k of ['length','width','height'])$(k).oninput=volume;
  $('measure-form').onsubmit=async e=>{e.preventDefault();const button=e.submitter;button.disabled=true;try{await api(`/api/staff/measurements/facilities/${$('facility').value}/units/${selected.unitId}`,{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({lengthM:Number($('length').value),widthM:Number($('width').value),heightM:Number($('height').value),conditionNote:$('condition').value.trim(),source:$('source').value})});$('measure-dialog').close();await load();$('message').textContent='Đã gửi phiếu cho Quản lý cơ sở.'}catch(err){$('form-message').textContent=err.message}finally{button.disabled=false}};
  SafeBoxAuth.requireRole('FACILITY_STAFF').then(async ok=>{if(!ok)return;try{const f=await api('/api/staff/measurements/facilities');$('facility').innerHTML=f.map(x=>`<option value="${esc(x.facilityId)}">${esc(x.name)}</option>`).join('');await load()}catch(e){$('message').textContent=e.message}});
})();
