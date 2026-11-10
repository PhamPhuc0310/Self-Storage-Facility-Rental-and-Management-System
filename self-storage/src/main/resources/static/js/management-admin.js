(async()=>{
  if(!await SafeBoxAuth.requireRole('SYSTEM_ADMIN'))return;
  const {esc,mount,drawer,api}=SafeBoxGrid,$=id=>document.getElementById(id);
  const roles={CUSTOMER:'Khách hàng',FACILITY_STAFF:'Nhân viên cơ sở',FACILITY_MANAGER:'Quản lý cơ sở',BUSINESS_OPERATIONS_MANAGER:'Quản lý kinh doanh',SYSTEM_ADMIN:'Quản trị hệ thống'};
  Object.entries(roles).forEach(([v,label])=>$('role-filter').add(new Option(label,v)));
  let users=[],facilities=[],selected=null;
  const name=id=>facilities.find(f=>f.facilityId===id)?.name||'Cơ sở chưa có tên';
  const grid=mount($('users'),[
    {label:'Họ và tên',value:u=>u.fullName||u.email},
    {label:'Email',value:u=>u.email},
    {label:'Vai trò',value:u=>roles[u.role]||u.role},
    {label:'Trạng thái',value:u=>u.status==='ACTIVE'?'Hoạt động':'Ngừng hoạt động',render:u=>`<span class="badge ${u.status==='ACTIVE'?'active':''}">${u.status==='ACTIVE'?'Hoạt động':'Ngừng hoạt động'}</span>`},
    {label:'Phạm vi',value:u=>u.role==='BUSINESS_OPERATIONS_MANAGER'?'Xem phạm vi báo cáo':`${u.facilityIds.length} cơ sở · ${u.regionCodes.length} vùng`}
  ],{search:$('search'),filter:u=>(!$('role-filter').value||u.role===$('role-filter').value)&&(!$('status-filter').value||u.status===$('status-filter').value)});
  for(const id of ['role-filter','status-filter'])$(id).onchange=()=>grid.refresh();
  const open=drawer($('detail'));
  async function detail(u){
    selected=u;$('drawer-message').textContent='';
    open(`<p>Đang tải thông tin tài khoản...</p>`);
    let ids=u.facilityIds;
    try{if(u.role==='BUSINESS_OPERATIONS_MANAGER')ids=await api('/api/admin/report-scopes/'+u.userId)}catch(e){if(selected===u)$('detail').querySelector('[data-content]').textContent=e.message;return}
    if(selected!==u || !$('detail').open)return;
    const active=u.status==='ACTIVE',facilityRole=['FACILITY_MANAGER','BUSINESS_OPERATIONS_MANAGER'].includes(u.role);
    open(`<h2>${esc(u.fullName)}</h2><p>${esc(u.email)}</p><p><span class="badge ${active?'active':''}">${active?'Hoạt động':'Ngừng hoạt động'}</span> · ${esc(roles[u.role])}</p><section><h3>Phạm vi hiện tại</h3><p>${ids.length?ids.map(id=>esc(name(id))).join('<br>'):'Chưa được cấp cơ sở.'}</p><p>${u.regionCodes.length?'Vùng: '+u.regionCodes.map(esc).join(', '):'Chưa được cấp vùng.'}</p></section><details><summary>Vai trò và trạng thái</summary><p class="notice">Đổi vai trò hoặc vô hiệu hóa sẽ thu hồi phạm vi hiện tại. Quyền mới có hiệu lực ở yêu cầu kế tiếp.</p><label>Vai trò<select id="selected-role">${Object.entries(roles).map(([r,label])=>`<option value="${r}" ${r===u.role?'selected':''}>${label}</option>`).join('')}</select></label><div class="sb-actions"><button class="sb-button" data-action="role">Lưu vai trò</button><button class="sb-button sb-secondary" data-action="status">${active?'Vô hiệu hóa':'Kích hoạt'}</button></div></details>${facilityRole&&active?`<details><summary>${u.role==='BUSINESS_OPERATIONS_MANAGER'?'Phạm vi báo cáo':'Phân công cơ sở'}</summary><label>Cơ sở<select id="selected-facility"><option value="">Chọn cơ sở</option>${facilities.map(f=>`<option value="${esc(f.facilityId)}">${esc(f.name)}${ids.includes(f.facilityId)?' · Đã cấp':''}</option>`).join('')}</select></label><div class="sb-actions"><button class="sb-button" data-action="grant-facility">Cấp cơ sở</button><button class="sb-button sb-secondary" data-action="revoke-facility">Thu hồi cơ sở</button></div></details>`:''}${u.role==='FACILITY_STAFF'&&active?`<details><summary>Phân công vùng</summary><label>Mã vùng<input id="selected-region" placeholder="Nhập mã vùng" list="region-options"></label><datalist id="region-options">${[...new Set(facilities.map(f=>f.regionCode).filter(Boolean))].map(r=>`<option value="${esc(r)}">`).join('')}</datalist><div class="sb-actions"><button class="sb-button" data-action="grant-region">Cấp vùng</button><button class="sb-button sb-secondary" data-action="revoke-region">Thu hồi vùng</button></div></details>`:''}`);
  }
  $('detail').addEventListener('close',()=>{selected=null});
  $('users').addEventListener('grid-open',e=>detail(e.detail));
  async function load(){
    $('message').textContent='Đang tải tài khoản...';$('reload').disabled=true;$('users').setAttribute('aria-busy','true');
    try{[users,facilities]=await Promise.all([api('/api/admin/access/users'),api('/api/admin/access/facilities')]);grid.set(users);$('message').textContent=`Đã tải ${users.length} tài khoản.`;return true}catch(e){grid.set([]);$('message').textContent=e.message;return false}finally{$('reload').disabled=false;$('users').setAttribute('aria-busy','false')}
  }
  $('detail').addEventListener('click',async e=>{
    const b=e.target.closest('[data-action]');if(!b||!selected)return;
    const u=selected,id=u.userId,action=b.dataset.action;let url='/api/admin/access',method='PUT',body;
    try{
      if(action==='role'){url+=`/users/${id}/role`;body={role:$('selected-role').value};if(body.role!==u.role&&!confirm('Đổi vai trò sẽ thu hồi phạm vi hiện tại. Tiếp tục?'))return}
      else if(action==='status'){url+=`/users/${id}/status`;body={status:u.status==='ACTIVE'?'INACTIVE':'ACTIVE'};if(!confirm(`${u.status==='ACTIVE'?'Vô hiệu hóa':'Kích hoạt'} tài khoản ${u.email}?`))return}
      else if(action.includes('facility')){const f=$('selected-facility').value;if(!/^[0-9a-f-]{36}$/i.test(f))throw Error('Chọn cơ sở hợp lệ.');method=action==='grant-facility'?'POST':'DELETE';if(u.role==='BUSINESS_OPERATIONS_MANAGER'){url=`/api/admin/report-scopes/${id}${method==='DELETE'?'/'+f:''}`;body=method==='POST'?{facilityId:f}:undefined}else url+=`/managers/${id}/facilities/${f}`}
      else{const r=$('selected-region').value.trim();if(!/^[A-Z0-9_-]{2,30}$/.test(r))throw Error('Nhập mã vùng hợp lệ.');url+=`/staff/${id}/regions${action==='revoke-region'?'/'+r:''}`;method=action==='grant-region'?'POST':'DELETE';body=method==='POST'?{regionCode:r}:undefined}
      $('detail').querySelectorAll('[data-action]').forEach(x=>x.disabled=true);$('drawer-message').textContent='Đang cập nhật...';
      await api(url,method,body);
      if(await load()){
        if(selected?.userId===id && $('detail').open){const updated=users.find(x=>x.userId===id);if(updated)await detail(updated);$('drawer-message').textContent='Đã cập nhật quyền truy cập.'}
      }else{if(selected?.userId===id)$('detail').close();$('message').textContent='Đã cập nhật nhưng chưa tải lại được danh sách. Hãy chọn Tải lại.'}
    }catch(e){if(selected?.userId===id)$('drawer-message').textContent=e.message}finally{if(selected?.userId===id)$('detail').querySelectorAll('[data-action]').forEach(x=>x.disabled=false)}
  });
  $('reload').onclick=load;await load();
})();
