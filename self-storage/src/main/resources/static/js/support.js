(() => { const $=id=>document.getElementById(id), api=(url,o={})=>SafeBoxAuth.authFetch(url,{...o,headers:{'Content-Type':'application/json',...(o.headers||{})}}).then(async r=>{if(!r.ok)throw new Error((await r.json().catch(()=>({}))).message||'Không thể xử lý yêu cầu');return r.status===204?null:r.json();}); const esc=s=>String(s??'').replace(/[&<>"']/g,x=>({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[x])); let me;
async function init(){if(!await SafeBoxAuth.requireRole('CUSTOMER','FACILITY_STAFF','FACILITY_MANAGER'))return;me=SafeBoxAuth.getCurrentUser();$('intro').textContent=me.role==='CUSTOMER'?'Gửi và theo dõi phản hồi cho hợp đồng của bạn.':'Yêu cầu hỗ trợ thuộc phạm vi cơ sở của bạn.';$('create').hidden=me.role!=='CUSTOMER';if(me.role==='CUSTOMER'){const cs=await api('/api/contracts/my');$('contract').innerHTML='<option value="">Chọn hợp đồng</option>'+cs.map(c=>`<option value="${c.contractId}">${esc(c.contractNumber||c.contractId)}</option>`).join('');}$('create-form').onsubmit=create;await load();document.body.style.visibility='visible';}
async function create(e){e.preventDefault();try{await api('/api/support',{method:'POST',body:JSON.stringify({contractId:$('contract').value,requestType:$('type').value,title:$('title').value,description:$('description').value,priority:$('priority').value})});e.target.reset();await load();}catch(x){$('notice').textContent=x.message;}}
async function load(){try{$('notice').textContent='';const rows=await api(me.role==='CUSTOMER'?'/api/support/mine':'/api/support/queue');$('tickets').innerHTML=rows.length?rows.map(card).join(''):'<div class="bg-white p-5 rounded-xl">Chưa có yêu cầu hỗ trợ nào.</div>';document.querySelectorAll('[data-action]').forEach(b=>b.onclick=()=>act(b));}catch(x){$('notice').textContent=x.message;}}
function card(t){const status={OPEN:'Mới',IN_PROGRESS:'Đang xử lý',RESOLVED:'Đã giải quyết',CLOSED:'Đã đóng'}[t.status]||'Đang cập nhật';let actions='';if(me.role==='FACILITY_MANAGER'&&t.status!=='CLOSED')actions=`<button data-action="assign" data-id="${t.requestId}" data-facility="${t.facilityId}" data-facility-name="${esc(t.facilityName)}" class="px-3 py-1 bg-blue-600 text-white rounded">Phân công</button>`;if((me.role==='FACILITY_STAFF'||me.role==='FACILITY_MANAGER')&&t.status==='OPEN')actions+=` <button data-action="status" data-next="IN_PROGRESS" data-id="${t.requestId}" class="px-3 py-1 bg-amber-500 text-white rounded">Nhận xử lý</button>`;if((me.role==='FACILITY_STAFF'||me.role==='FACILITY_MANAGER')&&t.status==='IN_PROGRESS')actions+=` <button data-action="status" data-next="RESOLVED" data-id="${t.requestId}" class="px-3 py-1 bg-emerald-600 text-white rounded">Giải quyết</button>`;if((me.role==='FACILITY_STAFF'||me.role==='FACILITY_MANAGER')&&t.status==='RESOLVED')actions+=` <button data-action="status" data-next="CLOSED" data-id="${t.requestId}" class="px-3 py-1 bg-slate-700 text-white rounded">Đóng</button>`;if(me.role==='CUSTOMER'&&t.status==='RESOLVED')actions+=` <button data-action="status" data-next="OPEN" data-id="${t.requestId}" class="px-3 py-1 bg-orange-600 text-white rounded">Mở lại</button>`;if(t.status!=='CLOSED')actions+=` <button data-action="reply" data-id="${t.requestId}" class="px-3 py-1 border rounded">Phản hồi</button>`;return `<article class="bg-white p-5 rounded-xl shadow"><div class="flex justify-between gap-4"><div><h2 class="font-bold">${esc(t.title)}</h2><p class="text-sm text-slate-500">${esc(t.facilityName)} · ${esc(t.contractNumber)} · ${status}</p></div><span class="text-sm">${esc({LOW:'Thấp',MEDIUM:'Trung bình',HIGH:'Cao',URGENT:'Khẩn cấp'}[t.priority]||'Chưa phân loại')}</span></div><p class="mt-3">${esc(t.description)}</p><p class="text-sm mt-2">Phụ trách: ${esc(t.assignedStaffName||'Chưa phân công')}</p>${t.resolutionNote?`<p class="mt-2 bg-emerald-50 p-2">Giải pháp: ${esc(t.resolutionNote)}</p>`:''}<div class="mt-3 flex flex-wrap gap-2">${actions}</div><details class="mt-3"><summary>Lịch sử trao đổi (${t.messages.length})</summary><div class="mt-2 space-y-1">${t.messages.map(m=>`<p class="text-sm border-l-2 pl-2"><b>${esc(m.authorName)}:</b> ${esc(m.message)}</p>`).join('')}</div></details></article>`;}
async function act(b){try{let id=b.dataset.id,a=b.dataset.action;if(a==='reply'){let message=prompt('Nội dung phản hồi:');if(!message)return;await api(`/api/support/${id}/messages`,{method:'POST',body:JSON.stringify({message})});}else if(a==='status'){let note=prompt(b.dataset.next==='OPEN'?'Lý do mở lại:':'Nội dung xử lý (bắt buộc khi giải quyết/đóng):')||'';await api(`/api/support/${id}/status`,{method:'PUT',body:JSON.stringify({status:b.dataset.next,note})});}else {await assign(b);return;}await load();}catch(x){$('notice').textContent=x.message;}}

let assignmentVersion=0;
const dialog=document.createElement('dialog');
dialog.id='staff-assignment';dialog.setAttribute('aria-labelledby','assignment-title');
dialog.className='p-6 rounded-xl shadow-xl';dialog.style.cssText='width:min(34rem,calc(100vw - 2rem));max-height:90vh;overflow:auto';
dialog.innerHTML='<form id="assignment-form"><h2 id="assignment-title" class="font-bold text-xl">Phân công nhân viên</h2><p id="assignment-facility" class="my-3"></p><label class="block">Tìm theo tên<input id="assignment-search" type="search" class="block w-full border rounded p-2 my-2" autocomplete="off"></label><label class="block">Nhân viên<select id="assignment-staff" class="block w-full border rounded p-2 my-2" size="6" required></select></label><p id="assignment-notice" role="status" class="my-3"></p><div class="flex gap-3 justify-end"><button id="assignment-close" type="button" class="px-4 py-2 border rounded">Đóng</button><button id="assignment-save" class="px-4 py-2 bg-blue-600 text-white rounded" disabled>Lưu phân công</button></div></form>';
document.body.append(dialog);
$('assignment-close').onclick=()=>dialog.close();
dialog.addEventListener('close',()=>{assignmentVersion++});
async function assign(button){
  const version=++assignmentVersion,id=button.dataset.id,facility=button.dataset.facility;
  let staff=[];
  const search=$('assignment-search'),select=$('assignment-staff'),save=$('assignment-save'),notice=$('assignment-notice');
  search.value='';search.disabled=false;select.disabled=false;select.replaceChildren();save.disabled=true;
  $('assignment-facility').textContent=button.dataset.facilityName||'Cơ sở của yêu cầu';
  notice.textContent='Đang tải nhân viên đủ điều kiện…';
  if(!dialog.open)dialog.showModal();search.focus();
  function render(){
    const selected=select.value,term=search.value.trim().toLocaleLowerCase('vi');
    const counts=new Map();staff.forEach(x=>counts.set(x.name,(counts.get(x.name)||0)+1));
    const matches=staff.filter(x=>String(x.name||'').toLocaleLowerCase('vi').includes(term));
    select.replaceChildren(...matches.map(x=>new Option((x.name||'Nhân viên')+(counts.get(x.name)>1?' · '+x.id.slice(-8):''),x.id)));
    select.value=matches.some(x=>x.id===selected)?selected:'';
    save.disabled=!select.value;
    notice.textContent=matches.length?'Chọn nhân viên rồi lưu phân công.':staff.length?'Không có tên phù hợp.':'Chưa có nhân viên đủ điều kiện tại cơ sở này.';
  }
  search.oninput=render;select.onchange=()=>{save.disabled=!select.value};
  $('assignment-form').onsubmit=async e=>{
    e.preventDefault();const staffId=select.value;
    if(version!==assignmentVersion||!staff.some(x=>x.id===staffId))return;
    save.disabled=true;search.disabled=true;select.disabled=true;
    try{
      await api('/api/support/'+id+'/assignment',{method:'PUT',body:JSON.stringify({staffId})});
      if(version===assignmentVersion){dialog.close();await load();button.focus()}
    }catch(error){if(version===assignmentVersion){notice.textContent=error.message+' Hãy đóng và mở lại để cập nhật danh sách.';select.value=''}}
    finally{if(version===assignmentVersion){search.disabled=false;select.disabled=false;save.disabled=!select.value}}
  };
  try{const rows=await api('/api/manager/staff?facilityId='+encodeURIComponent(facility));if(version!==assignmentVersion)return;staff=rows;render()}
  catch(error){if(version===assignmentVersion)notice.textContent=error.message}
}

init();})();
