const {chromium}=require('playwright');
const fs=require('node:fs');
const path=require('node:path');
const assert=require('node:assert/strict');
const base='http://127.0.0.1:18080';
const password=process.env.SAFEBOX_E2E_PASSWORD;
const phase=process.argv[2]||'prepare';
if(!password||!['prepare','pay','handover','inspect'].includes(phase))throw Error('SAFEBOX_E2E_PASSWORD and prepare|pay|handover|inspect required');
const output=path.resolve(__dirname,'../../../../docs/testing/evidence/recovery-phase3-journey.json');
const facility='3f4e5607-b0b4-f111-a965-932c2c6addb7';
const unit='00000000-0000-4000-8000-00000000b401';
const staff='384e5607-b0b4-f111-a965-932c2c6addb7';
const returnStaff='00000000-0000-4000-8000-00000000b403';
const today=new Intl.DateTimeFormat('en-CA',{timeZone:'Asia/Ho_Chi_Minh',year:'numeric',month:'2-digit',day:'2-digit'}).format(new Date());
const when=`${today}T08:00:00+07:00`;
const result=fs.existsSync(output)?JSON.parse(fs.readFileSync(output)): {database:'SelfStorageFacilityDB_Test',unit,events:[]};
const save=()=>fs.writeFileSync(output,JSON.stringify(result,null,2));
async function main(){
 const browser=await chromium.launch({executablePath:'C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe',headless:true});
 const pages={};
 try{
  for(const [role,email] of Object.entries({customer:'customer3@safebox.vn',manager:'manager.q7@safebox.vn',staff:'staff.q7@safebox.vn',returnStaff:'recovery-p3-b403@example.invalid'})){
   const context=await browser.newContext(),page=await context.newPage();
   await page.goto(base+'/stitch/ng_nh_p_ng_k_safebox_storage.html',{waitUntil:'domcontentloaded'});
   const login=await page.evaluate(async({email,password})=>{const r=await fetch('/api/auth/login',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({email,password})});return{status:r.status,body:await r.json()}},{email,password});
   result.events.push({role,step:'login',status:login.status});assert.equal(login.status,200,`login ${role}`);
   await page.evaluate(x=>SafeBoxAuth.saveAuth(x.token,x.user,false),login.body);pages[role]={context,page};
  }
  async function api(role,step,method,url,body){const r=await pages[role].page.evaluate(async({method,url,body})=>{const res=await fetch(url,{method,headers:{Authorization:`Bearer ${SafeBoxAuth.getToken()}`,...(body?{'Content-Type':'application/json'}:{})},body:body?JSON.stringify(body):undefined});const raw=await res.text();let value;try{value=JSON.parse(raw)}catch{value=raw.slice(0,400)}return{status:res.status,body:value}},{method,url,body});result.events.push({role,step,method,url,status:r.status,body:r.body});save();return r}
  async function shot(role,name,url){await pages[role].page.goto(base+url,{waitUntil:'domcontentloaded'});await pages[role].page.waitForTimeout(600);await pages[role].page.screenshot({path:path.resolve(__dirname,`../../../../docs/testing/evidence/recovery-phase3-${name}.png`),fullPage:false})}
  if(phase==='prepare'){
   const booked=await api('customer','reservation','POST','/api/reservations',{facilityId:facility,typeId:1,startDate:today,months:1});assert.equal(booked.status,201);result.reservationId=booked.body.reservationId;
   const approved=await api('manager','approve','POST',`/api/manager/reservations/${result.reservationId}/confirm`,{});assert.equal(approved.status,200);result.contractId=approved.body.contractId;
   const summary=await api('customer','summary','GET',`/api/contracts/${result.contractId}/payments/summary`);assert.equal(summary.status,200);
   const deposit=await api('customer','deposit','POST',`/api/contracts/${result.contractId}/payments`,{paymentType:'DEPOSIT',amount:summary.body.deposit.remainingAmount,paymentMethod:'CASH',idempotencyKey:'recovery-phase3-b401-deposit'});assert.equal(deposit.status,201);
   const confirmed=await api('manager','confirm deposit','POST',`/api/staff/contracts/${result.contractId}/payments/${deposit.body.id}/confirm-cash`);assert.equal(confirmed.status,200);
   const appointment=await api('customer','pickup appointment','POST',`/api/contracts/${result.contractId}/pickup-appointment`,{appointmentTime:when});assert.equal(appointment.status,201);
   const assigned=await api('manager','assign staff','PUT',`/api/manager/contracts/${result.contractId}/pickup-appointment/assignment`,{staffId:staff,reason:'Recovery Phase 3 isolated synthetic journey'});assert.equal(assigned.status,200);
   const rentSummary=await api('customer','rent summary','GET',`/api/contracts/${result.contractId}/payments/summary`);assert.equal(rentSummary.status,200);
   const rent=await api('customer','remaining rent','POST',`/api/contracts/${result.contractId}/payments`,{paymentType:'REMAINING_RENT',amount:rentSummary.body.initialRent.remainingAmount,paymentMethod:'CASH',idempotencyKey:'recovery-phase3-b401-rent'});assert.equal(rent.status,201);
   result.rentPaymentId=rent.body.id;save();
   const rentConfirmed=await api('staff','confirm rent','POST',`/api/staff/contracts/${result.contractId}/payments/${rent.body.id}/confirm-cash`);assert.equal(rentConfirmed.status,200);
   result.scheduledAt=when;save();
  }else if(phase==='pay'){
   const paymentId=result.rentPaymentId||result.events.findLast(e=>e.step==='remaining rent'&&e.status===201)?.body?.id;
   assert(result.contractId&&paymentId,'prepare must create the payment');
   const rentConfirmed=await api('staff','confirm rent','POST',`/api/staff/contracts/${result.contractId}/payments/${paymentId}/confirm-cash`);assert.equal(rentConfirmed.status,200);
   await shot('customer','paid-contract',`/stitch/uc11-customer-payments-appointments.html?contractId=${result.contractId}`);
   result.scheduledAt=when;save();
  }else if(phase==='handover'){
   assert(result.contractId,'prepare must complete first');
   const handover=await api('staff','handover','POST',`/api/staff/contracts/${result.contractId}/handover`,{conditionStatus:'GOOD',accessMethod:'KEY',accessIdentifier:'P3-B401-KEY',customerSignature:'Synthetic customer',staffSignature:'Synthetic staff',estimatedRepairCost:0});
   assert.equal(handover.status,201);result.handoverId=handover.body.id||handover.body.handoverId;save();
   await shot('staff','handover','/staff-tasks.html');
   const now=new Date(Date.now()+120000);const returnAt=new Intl.DateTimeFormat('sv-SE',{timeZone:'Asia/Ho_Chi_Minh',year:'numeric',month:'2-digit',day:'2-digit',hour:'2-digit',minute:'2-digit',second:'2-digit',hour12:false}).format(now).replace(' ','T');
   const request=await api('customer','early return request','POST',`/api/customer/contracts/${result.contractId}/early-return`,{proposedReturnAt:returnAt,reason:'Isolated synthetic return test'});assert.equal(request.status,201);result.earlyReturnId=request.body.id;
   const schedule=await api('customer','return schedule','POST',`/api/customer/rentals/${result.contractId}/return-schedule`,{contractId:result.contractId,scheduledAt:returnAt,note:'Isolated synthetic return',checklistEmptyConfirmed:true,checklistAssetsConfirmed:true});assert.equal(schedule.status,200);result.returnHandoverId=schedule.body.handoverId||schedule.body.id;result.returnAt=returnAt;save();
   const options=await api('manager','return eligible staff','GET',`/api/manager/returns/${result.returnHandoverId}/eligible-staff`);assert.equal(options.status,200);
   assert(options.body.some(x=>(x.staffId||x.userId)===returnStaff),'isolated return Staff must be eligible');
   const assign=await api('manager','assign return staff','PUT',`/api/manager/returns/${result.returnHandoverId}/assignment`,{staffId:returnStaff});assert.equal(assign.status,200);
  }else{
   assert(result.returnHandoverId,'handover and return schedule must complete first');
   const inspection=await api('returnStaff','return inspection','POST',`/api/facility/returns/${result.returnHandoverId}/complete`,{handoverId:result.returnHandoverId,leftoverItems:false,cleanlinessStatus:'CLEAN',accessReturned:true,accessMethod:'KEY',accessIdentifier:'P3-B401-KEY',conditionStatus:'GOOD',estimatedRepairCost:0,customerConfirmed:true,customerSignature:'Synthetic customer',staffSignature:'Synthetic staff',unitNextStatus:'INACTIVE'});assert.equal(inspection.status,200);
   await shot('returnStaff','returned','/stitch/bien_ban_tra_kho_safebox_manager.html');
   const settlement=await api('manager','early return settlement status','GET',`/api/manager/early-returns/${result.earlyReturnId}/settlement`);assert.equal(settlement.status,200);result.settlementStatus=settlement.body.status;save();
  }
 }finally{for(const x of Object.values(pages))await x.context.close();await browser.close();save()}
 console.log(JSON.stringify({phase,contractId:result.contractId,events:result.events.length}));
}
main().catch(e=>{result.error=e.stack||String(e);save();console.error(result.error);process.exitCode=1});
