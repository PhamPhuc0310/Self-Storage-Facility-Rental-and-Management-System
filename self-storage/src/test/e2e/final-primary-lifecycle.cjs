// Mutates only the existing isolated rehearsal database. Never logs secrets.
const { chromium } = require('playwright');
const fs = require('node:fs');
const path = require('node:path');
const { execFileSync } = require('node:child_process');
const assert = require('node:assert/strict');

const root = path.resolve(__dirname, '../../../..');
const local = Object.fromEntries(fs.readFileSync(path.join(root,'.env.codex.local'),'utf8')
  .split(/\r?\n/).filter(x=>/^[A-Za-z_][A-Za-z0-9_]*=/.test(x))
  .map(x=>{const i=x.indexOf('=');return [x.slice(0,i),x.slice(i+1).replace(/^['"]|['"]$/g,'')]}));
const password=local.SAFEBOX_DEMO_PASSWORD;
const db='SafeBox_FinalRehearsal_20261030', pid=Number(process.env.SAFEBOX_REHEARSAL_APP_PID);
const base='http://127.0.0.1:18083';
const C={renew:'00000000-0000-4000-8000-00000000D401',overdue:'00000000-0000-4000-8000-00000000D402',returning:'00000000-0000-4000-8000-00000000D403'};
const staffId='384E5607-B0B4-F111-A965-932C2C6ADDB7';
if(!password||!pid) throw new Error('Local rehearsal credentials or app PID missing');
function identity(){
  const q=`SET NOCOUNT ON; IF DB_NAME()<>'${db}' THROW 51970,'Wrong DB',1;
    IF NOT EXISTS(SELECT 1 FROM sys.dm_exec_sessions WHERE host_process_id=${pid} AND database_id=DB_ID('${db}')) THROW 51971,'No rehearsal app session',1;
    IF EXISTS(SELECT 1 FROM sys.dm_exec_sessions WHERE host_process_id=${pid} AND database_id<>DB_ID('${db}')) THROW 51972,'App has other DB session',1;
    SELECT DB_NAME();`;
  assert(execFileSync('sqlcmd',['-S','tcp:127.0.0.1,1433','-E','-C','-d',db,'-b','-W','-h','-1','-Q',q],{encoding:'utf8',timeout:15000}).includes(db));
}
async function call(page,method,url,body){
  identity();
  const result=await page.evaluate(async ({method,url,body})=>{
    const r=await SafeBoxAuth.authFetch(url,{method,headers:{'Content-Type':'application/json'},...(body===undefined?{}:{body:JSON.stringify(body)})});
    return {status:r.status,body:await r.json().catch(()=>({}))};
  },{method,url,body});
  if(result.status>=400) throw new Error(`${method} ${url}: HTTP ${result.status} ${JSON.stringify(result.body).slice(0,300)}`);
  return result.body;
}
async function login(browser,email){
  const context=await browser.newContext();const page=await context.newPage();
  await page.goto(base+'/stitch/ng_nh_p_ng_k_safebox_storage.html',{waitUntil:'domcontentloaded'});
  const ok=await page.evaluate(async ({email,password})=>{
    const r=await fetch('/api/auth/login',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({email,password})});
    const b=await r.json();if(r.ok&&b.token)SafeBoxAuth.saveAuth(b.token,b,false);return r.ok&&!!b.token;
  },{email,password});
  assert(ok,`Login failed: ${email}`);return {context,page};
}
(async()=>{
  identity();const browser=await chromium.launch({executablePath:'C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe',headless:true});
  const sessions=[];const out={database:db,renewal:{},overdue:{},return:{}};
  try{
    const customer=await login(browser,'customer3@safebox.vn');sessions.push(customer);
    const manager=await login(browser,'manager.q7@safebox.vn');sessions.push(manager);
    const staff=await login(browser,'staff.q7@safebox.vn');sessions.push(staff);
    const r=await call(customer.page,'POST',`/api/customer/contracts/${C.renew}/renewals`,{months:1,note:'Lịch gia hạn chuẩn bị',discountAmount:0});
    assert.equal(r.contractNumber,'DEMO-RENEW-01');out.renewal.request={status:r.status,oldEndDate:r.oldEndDate,newEndDate:r.newEndDate,amount:r.renewalAmount};
    const a=await call(manager.page,'POST',`/api/manager/renewals/${r.renewalId}/approve`,{});
    assert.equal(a.status,'APPROVED');out.renewal.approval={status:a.status,invoiceStatus:a.invoiceStatus};
    const p=await call(customer.page,'POST',`/api/customer/renewals/${r.renewalId}/pay`,{paymentMethod:'BANK_TRANSFER',amount:a.renewalAmount,idempotencyKey:'REHEARSAL-DEMO-RENEW-01'});
    assert.equal(p.status,'PENDING');out.renewal.payment={status:p.status,transactionId:p.transactionId};
    const pr=await call(customer.page,'POST',`/api/customer/renewals/${r.renewalId}/pay`,{paymentMethod:'BANK_TRANSFER',amount:a.renewalAmount,idempotencyKey:'REHEARSAL-DEMO-RENEW-01'});
    assert.equal(pr.transactionId,p.transactionId);out.renewal.idempotent=true;

    const fee=await call(manager.page,'POST',`/api/manager/overdue/contracts/${C.overdue}/calculate-fee`);
    assert.equal(fee.contractNumber,'DEMO-OVERDUE-01');out.overdue.fee={status:fee.status,overdueDays:fee.overdueDays,billableDays:fee.billableDays,amount:fee.penaltyAmount};
    const feeAgain=await call(manager.page,'POST',`/api/manager/overdue/contracts/${C.overdue}/calculate-fee`);
    assert.equal(feeAgain.penaltyId,fee.penaltyId);out.overdue.idempotent=true;
    const debt=await call(customer.page,'POST',`/api/customer/overdue/penalties/${fee.penaltyId}/pay`,{paymentMethod:'BANK_TRANSFER',amount:fee.penaltyAmount,idempotencyKey:'REHEARSAL-DEMO-OVERDUE-01'});
    assert.equal(debt.status,'PENDING_APPROVAL');out.overdue.payment={status:debt.status,paidAmount:debt.paidAmount};

    const s=await call(customer.page,'POST',`/api/customer/rentals/${C.returning}/return-schedule`,{contractId:C.returning,scheduledAt:'2026-11-06T10:00:00',note:'Lịch trả kho theo kịch bản',checklistEmptyConfirmed:true,checklistAssetsConfirmed:true});
    assert.equal(s.status,'SCHEDULED');assert.equal(s.staffId,null);out.return.schedule={status:s.status,staffId:s.staffId,scheduledAt:s.scheduledAt};
    const eligible=await call(manager.page,'GET',`/api/manager/returns/${s.handoverId}/eligible-staff`);
    assert(eligible.some(x=>x.staffId?.toLowerCase()===staffId.toLowerCase()),'Q7 staff not eligible');out.return.eligibleStaff=eligible.length;
    const assigned=await call(manager.page,'PUT',`/api/manager/returns/${s.handoverId}/assignment`,{staffId});
    assert.equal(assigned.staffId.toLowerCase(),staffId.toLowerCase());out.return.assignment={status:assigned.status,staffAssigned:true};
    const repeat=await call(manager.page,'PUT',`/api/manager/returns/${s.handoverId}/assignment`,{staffId});
    assert.equal(repeat.staffId.toLowerCase(),staffId.toLowerCase());out.return.idempotent=true;
    const tasks=await call(staff.page,'GET','/api/facility/returns?status=SCHEDULED');
    out.return.staffTaskVisible=tasks.some(x=>x.handoverId?.toLowerCase()===s.handoverId.toLowerCase());
    assert(out.return.staffTaskVisible,'Assigned staff cannot see return');
    console.log(JSON.stringify(out,null,2));
  }finally{await Promise.all(sessions.map(x=>x.context.close()));await browser.close()}
})().catch(e=>{console.error(e.message);process.exitCode=1});
