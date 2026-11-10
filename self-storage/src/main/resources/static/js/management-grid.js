window.SafeBoxGrid = (() => {
  const esc = v => String(v ?? '').replace(/[&<>"']/g, c => ({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));
  // shortcut: APIs return arrays, use server pagination when those endpoints support it.
  function mount(host, columns, {search, filter, empty='Không có dữ liệu phù hợp.'} = {}) {
    host.dataset.grid="true";
    let rows=[], page=1, size=10, sort=null, direction=1, visible=[];
    host.innerHTML=`<div class="data-panel"><div class="grid-scroll"><table><caption class="notice">${esc(host.dataset.caption || 'Danh sách dữ liệu')}</caption><thead><tr>${columns.map((c,i)=>`<th scope="col"><button class="text-button" data-sort="${i}">${esc(c.label)} ↕</button></th>`).join('')}</tr></thead><tbody></tbody></table></div><div class="pager"><span role="status"></span><label>Số dòng<select aria-label="Số dòng mỗi trang"><option>10</option><option>25</option><option>50</option></select></label><button class="sb-button sb-secondary" data-page="-1" aria-label="Trang trước">←</button><button class="sb-button sb-secondary" data-page="1" aria-label="Trang sau">→</button></div></div>`;
    function render(){
      const term=(search?.value || '').trim().toLocaleLowerCase('vi');
      let data=rows.filter(r=>(!filter || filter(r)) && (!term || columns.some(c=>String(c.value(r)??'').toLocaleLowerCase('vi').includes(term))));
      if(sort!==null)data.sort((a,b)=>{const value=columns[sort].sortValue||columns[sort].value,x=value(a),y=value(b);return direction*(typeof x==='number'&&typeof y==='number'?x-y:String(x??'').localeCompare(String(y??''),'vi',{numeric:true}))});
      const pages=Math.max(1,Math.ceil(data.length/size));page=Math.min(page,pages);visible=data.slice((page-1)*size,page*size);
      host.querySelector('tbody').innerHTML=visible.map((r,i)=>`<tr>${columns.map((c,j)=>`<td>${j===0?`<button class="text-button cell-text" data-open="${i}" data-key="${esc(r.userId||r.facilityId||r.id||r.periodStart||i)}" title="${esc(c.value(r))}">${c.render?c.render(r):esc(c.value(r))}</button>`:c.render?c.render(r):`<span class="cell-text" title="${esc(c.value(r))}">${esc(c.value(r))}</span>`}</td>`).join('')}</tr>`).join('')||`<tr><td colspan="${columns.length}">${esc(empty)}</td></tr>`;
      host.querySelector('.pager span').textContent=`${data.length} kết quả · Trang ${page}/${pages}`;
      host.querySelector('[data-page="-1"]').disabled=page===1;host.querySelector('[data-page="1"]').disabled=page===pages;
      host.querySelectorAll('th').forEach((th,i)=>th.setAttribute('aria-sort',sort===i?(direction===1?'ascending':'descending'):'none'));
    }
    host.onclick=e=>{const b=e.target.closest('button');if(!b)return;if(b.dataset.sort!==undefined){const n=Number(b.dataset.sort);direction=sort===n?-direction:1;sort=n;render()}else if(b.dataset.page){page+=Number(b.dataset.page);render()}else if(b.dataset.open!==undefined)host.dispatchEvent(new CustomEvent('grid-open',{detail:visible[Number(b.dataset.open)]}))};
    host.querySelector('select').onchange=e=>{size=Number(e.target.value);page=1;render()};
    if(search)search.oninput=()=>{page=1;render()};
    return {set(data){rows=data;render()},refresh(){page=1;render()}};
  }
  function drawer(dialog){
    let opener,host,key;
    dialog.querySelector('[data-close]').onclick=()=>dialog.close();
    dialog.addEventListener('close',()=>{
      const replacement=host&&key?host.querySelector('[data-key="'+CSS.escape(key)+'"]'):null;
      (opener?.isConnected?opener:replacement||host?.querySelector('button'))?.focus();
    });
    return content=>{
      if(!dialog.open){opener=document.activeElement;host=opener?.closest('[data-grid]');key=opener?.dataset.key}
      dialog.querySelector('[data-content]').innerHTML=content;if(!dialog.open)dialog.showModal();
    };
  }
  async function api(url,method='GET',body){const r=await SafeBoxAuth.authFetch(url,{method,headers:{'Content-Type':'application/json'},body:body===undefined?undefined:JSON.stringify(body)}),d=await r.json().catch(()=>null);if(!r.ok)throw Error(d?.message||'Không thể xử lý yêu cầu. Vui lòng thử lại.');return d}
  document.addEventListener('keydown',e=>{
    const dialog=document.querySelector('dialog[open]');if(e.key!=='Tab'||!dialog)return;
    const items=[...dialog.querySelectorAll('button:not(:disabled),a[href],input:not(:disabled),select:not(:disabled),textarea:not(:disabled),summary,[tabindex="0"]')].filter(x=>x.getClientRects().length);
    const first=items[0],last=items.at(-1),active=document.activeElement;
    if((e.shiftKey&&active===first)||(!e.shiftKey&&active===last)||!dialog.contains(active)){
      e.preventDefault();(e.shiftKey?last:first)?.focus();
    }
  });
  return {esc,mount,drawer,api};
})();
