const test=require('node:test'),assert=require('node:assert/strict'),fs=require('node:fs'),vm=require('node:vm');
const script=fs.readFileSync(require.resolve('../../main/resources/static/js/early-return.js'),'utf8');
const flush=()=>new Promise(setImmediate);
async function screen(role,status='ESTIMATED',legacyZero=false) {
  const elements=new Map(),calls=[];
  const get=id=>{if(!elements.has(id))elements.set(id,{handlers:{},hidden:false,value:'',addEventListener(k,f){this.handlers[k]=f},showModal(){this.open=true},close(){this.open=false}});return elements.get(id)};
  const calculation={returnDate:'2026-11-12',estimated:status==='ESTIMATED',eligiblePrepaidRent:9200,unusedEligibleRent:4900,rentalRefund:3920,retainedRent:980,depositBalance:1000,documentedDeductions:300,outstandingDebt:300,liabilityOffset:300,netRefund:4620,remainingDebt:0};
  const state={status,calculation:legacyZero?null:calculation,settlement:status==='APPROVED'?{status:'APPROVED',depositRefundAmount:legacyZero?0:700}:null};
  vm.runInNewContext(script,{document:{getElementById:get},SafeBoxUc12Time:{todayISODateVN:()=> '2026-11-10',formatDateVN:()=> '12/11/2026',formatDateTimeVN:()=> '12/11/2026 10:00'},
    SafeBoxAuth:{requireRole:async()=>true,getCurrentUser:()=>({role}),authFetch:async(url,options={})=>{
      calls.push({url,...options});let data=url.endsWith('/settlement')?state:url==='/api/contracts/my'?[]:url.endsWith('/refund')?{}:[{id:'request-1',contractId:'contract-1',status:'REQUESTED'}];return{ok:true,json:async()=>data};
    }}});
  await flush();get('requests').handlers.click({target:{closest:()=>({dataset:{id:'request-1'}})}});await flush();
  return{get,calls};
}
test('customer sees estimated 80/20 rent, separate deposit, liabilities and no manager controls',async()=>{
  const {get,calls}=await screen('CUSTOMER');const text=get('settlement-state').textContent;
  assert.match(text,/Ước tính/);assert.match(text,/Hoàn 80%/);assert.match(text,/Giữ lại 20%/);
  assert.match(text,/không thu thêm/);assert.match(text,/Số dư cọc/);assert.match(text,/Nợ còn lại/);
  assert(get('approval-form').hidden);assert(get('refund-confirmation').hidden);
  assert(calls.some(c=>c.url==='/api/customer/early-returns/request-1/settlement'));
});
test('manager approval is distinct from confirmed execution and requires explicit evidence',async()=>{
  const {get,calls}=await screen('FACILITY_MANAGER','APPROVED');
  assert.match(get('settlement-state').textContent,/chưa xác nhận thực hiện/);
  assert.equal(get('refund-confirmation').hidden,false);assert.equal(get('refund-amount').value,4620);
  get('refund-reference').value='TEST-ONLY-REFUND';get('refund-method').value='CASH';
  await get('refund-confirmation').handlers.submit({preventDefault(){}});
  const confirmation=calls.find(c=>c.url.endsWith('/refund'));
  assert.deepEqual(JSON.parse(confirmation.body),{reference:'TEST-ONLY-REFUND',paymentMethod:'CASH',amount:4620});
});
test('customer cannot confirm an approved settlement through UI controls',async()=>{
  const {get}=await screen('CUSTOMER','APPROVED');assert(get('refund-confirmation').hidden);assert(get('refund-button').hidden);
});
test('legacy zero-refund decision does not offer an impossible money transaction',async()=>{
  const {get}=await screen('FACILITY_MANAGER','APPROVED',true);assert(get('refund-confirmation').hidden);assert(get('refund-button').hidden);
});
