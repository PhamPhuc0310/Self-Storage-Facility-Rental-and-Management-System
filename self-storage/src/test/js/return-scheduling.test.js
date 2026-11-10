const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const vm = require('node:vm');
const time = require('../../main/resources/static/js/uc12-time.js');
const html = fs.readFileSync(require.resolve('../../main/resources/static/stitch/dat_lich_tra_kho_safebox_storage.html'), 'utf8');
function screen() {
  const elements = new Map();
  const element = () => ({classList:{add(){},remove(){}},children:[],appendChild(x){this.children.push(x)},removeAttribute(){}});
  const get = id => {if (!elements.has(id)) elements.set(id,element()); return elements.get(id)};
  const context = vm.createContext({URLSearchParams,console,window:{location:{search:'?contractId=test'}},
    SafeBoxUc12Time:{...time,todayISODateVN:()=> '2026-11-10'},
    document:{getElementById:get,createElement:element,addEventListener(){}}});
  const script = [...html.matchAll(/<script[^>]*>([\s\S]*?)<\/script>/g)].find(x=>x[1].includes('function renderFormView'))[1];
  vm.runInContext(script,context);
  return {context,get};
}
test('ACTIVE date picker permits early, on-time and later dates; warning follows selected date',()=>{
  const {context:c,get}=screen();
  c.renderFormView({contract:{status:'ACTIVE',startDate:'2026-11-01',endDate:'2026-12-01'}},null);
  const date=get('return-date-input');
  assert.equal(date.min,'2026-11-10'); assert.equal(date.max,''); assert.equal(date.readOnly,false);
  for(const [value,hidden] of [['2026-11-11',false],['2026-12-01',true],['2026-12-02',true]]) {
    date.value=value; c.updateReturnPolicyNotice(); assert.equal(get('early-return-policy').hidden,hidden);
  }
  assert.equal(get('period-val').textContent,'01/11/2026 – 01/12/2026');
});
test('OVERDUE picker starts at Vietnam today and preserves the expired contractual date',()=>{
  const {context:c,get}=screen();
  c.renderFormView({contract:{status:'OVERDUE',startDate:'2026-10-01',endDate:'2026-11-09'}},null);
  assert.equal(get('return-date-input').value,'2026-11-10');
  assert.equal(get('return-date-input').min,'2026-11-10');
  assert.equal(get('end-date-text').textContent,'09/11/2026');
  assert.equal(get('early-return-policy').hidden,true);
});
test('existing early proposal remains exact and physical service slots remain unchanged',()=>{
  const {context:c,get}=screen();
  c.renderFormView({contract:{status:'ACTIVE',startDate:'2026-11-01',endDate:'2026-12-01'}},
    {scheduledReturnAt:'2026-11-15T10:00:00'});
  assert.equal(get('return-date-input').value,'2026-11-15');
  assert.equal(get('return-date-input').readOnly,true);
  assert.equal(get('early-return-policy').hidden,false);
  const slots=c.generateTimeSlots(); assert.equal(slots.length,34);
  assert.equal(slots[0].start,'06:00:00'); assert.equal(slots[33].start,'22:30:00');
});
test('confirmation distinguishes original contractual end and actual handover in Vietnamese dates',()=>{
  const {context:c,get}=screen();
  const schedule={handoverId:'12345678-1234',status:'SCHEDULED',scheduledAt:'2026-11-15T10:00:00',contractEndDate:'2026-12-01'};
  c.renderConfirmedView(schedule);
  assert.match(get('conf-return-dates').textContent,/01\/12\/2026.*Chưa bàn giao/);
  c.renderConfirmedView({...schedule,status:'COMPLETED',actualAt:'2026-11-15T10:05:00'});
  assert.match(get('conf-return-dates').textContent,/01\/12\/2026.*15\/11\/2026 10:05/);
  assert.equal(get('breadcrumb-title').textContent,'Đã nhận lại kho');
});
