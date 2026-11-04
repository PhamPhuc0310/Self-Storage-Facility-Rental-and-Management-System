// Focused live checks for cash confirmation audit and pickup reschedule fields.
// The app must already be running on port 18080 against SelfStorageFacilityDB_Test.
const {chromium} = require('playwright');
const bcrypt = require('bcryptjs');
const {randomUUID, randomBytes} = require('node:crypto');
const {execFileSync} = require('node:child_process');
const fs = require('node:fs');
const os = require('node:os');
const path = require('node:path');

const db='SelfStorageFacilityDB_Test', appPid=18872, base='http://127.0.0.1:18080';
const out=path.resolve(__dirname,'../../../../docs/testing/evidence/phase46-defect-fix');
fs.mkdirSync(out,{recursive:true});
const previous=JSON.parse(fs.readFileSync(path.resolve(__dirname,'../../../../docs/testing/evidence/phase46-live/246dd488-results.json'),'utf8')).fixtureIds;
const tag=randomBytes(4).toString('hex');
const password=randomBytes(18).toString('base64url');
const hash=bcrypt.hashSync(password,10);
const ids={contract:randomUUID(),reservation:randomUUID(),unit:randomUUID()};
const today=new Intl.DateTimeFormat('en-CA',{timeZone:'Asia/Ho_Chi_Minh',year:'numeric',month:'2-digit',day:'2-digit'}).format(new Date());
const plus=(date,days)=>{const d=new Date(`${date}T00:00:00Z`);d.setUTCDate(d.getUTCDate()+days);return d.toISOString().slice(0,10)};
const tomorrow=plus(today,1),later=plus(today,2);
const evidence={database:db,appPid,tag,ids,dates:{today,tomorrow,later},http:[],sql:[],forms:[],screenshots:[],failures:[]};
function sql(query) {
  const f=path.join(os.tmpdir(),`phase46-fix-${tag}-${randomBytes(4).toString('hex')}.sql`);
  fs.writeFileSync(f,query,'utf8');
  try{return execFileSync('sqlcmd',['-b','-S','localhost','-E','-C','-d',db,'-l','5','-W','-h','-1','-i',f],{encoding:'utf8',timeout:12000,maxBuffer:1024*1024}).trim()}
  catch(e){throw new Error(`SQL failed: ${String(e.stdout||'').trim()} ${String(e.stderr||'').trim()}`)}
  finally{fs.rmSync(f,{force:true})}
}
function identity(){const x=sql(`SET NOCOUNT ON; IF DB_NAME()<>'${db}' THROW 51000,'Wrong database',1;
  IF NOT EXISTS(SELECT 1 FROM sys.dm_exec_sessions WHERE host_process_id=${appPid} AND DB_NAME(database_id)='${db}') THROW 51001,'Wrong app database',1;
  SELECT DB_NAME();`);if(!x.includes(db))throw new Error('Identity failed')}
function state(label){const result=sql(`SET NOCOUNT ON; IF DB_NAME()<>'${db}' THROW 51000,'Wrong database',1;
  SELECT 'payments',payment_type,status,COUNT(*),SUM(amount) FROM PAYMENT_TRANSACTIONS WHERE contract_id='${ids.contract}' GROUP BY payment_type,status;
  SELECT 'invoices',invoice_type,status,COUNT(*),SUM(paid_amount) FROM INVOICES WHERE contract_id='${ids.contract}' GROUP BY invoice_type,status;
  SELECT 'cash_audit',action_type,entity_id,user_id,description,created_at FROM ACTIVITY_LOGS WHERE entity_type='PAYMENT_TRANSACTION' AND entity_id IN (SELECT transaction_id FROM PAYMENT_TRANSACTIONS WHERE contract_id='${ids.contract}') ORDER BY created_at;
  SELECT 'pickup',appointment_id,appointment_time,status FROM PICKUP_APPOINTMENTS WHERE contract_id='${ids.contract}';`);evidence.sql.push({label,result});return result}
function countLog(paymentId){return Number(sql(`SET NOCOUNT ON; IF DB_NAME()<>'${db}' THROW 51000,'Wrong database',1; SELECT COUNT(*) FROM ACTIVITY_LOGS WHERE action_type='CONFIRM_CASH_PAYMENT' AND entity_id='${paymentId}';`).split(/\s+/).at(-1))}
function fixture(){identity();const q=`SET QUOTED_IDENTIFIER ON;SET ANSI_NULLS ON;SET ANSI_PADDING ON;SET ANSI_WARNINGS ON;SET ARITHABORT ON;SET CONCAT_NULL_YIELDS_NULL ON;SET NUMERIC_ROUNDABORT OFF;
 SET XACT_ABORT ON;SET NOCOUNT ON;IF DB_NAME()<>'${db}' THROW 51000,'Wrong database',1;
 BEGIN TRAN;
 IF (SELECT COUNT(*) FROM USERS WHERE user_id IN ('${previous.customer}','${previous.manager}','${previous.staff}','${previous.otherStaff}'))<>4 THROW 51002,'Synthetic users missing',1;
 UPDATE USERS SET password_hash='${hash}' WHERE user_id IN ('${previous.customer}','${previous.manager}','${previous.staff}','${previous.otherStaff}');
 DECLARE @facility uniqueidentifier='3F4E5607-B0B4-F111-A965-932C2C6ADDB7';
 DECLARE @pricing uniqueidentifier=(SELECT TOP 1 pricing_id FROM PRICING_POLICIES WHERE facility_id=@facility AND type_id=1 AND status='ACTIVE');
 IF @pricing IS NULL OR EXISTS(SELECT 1 FROM RENTAL_CONTRACTS WHERE contract_number='P46-FIX-${tag}') THROW 51003,'Fixture precondition failed',1;
 INSERT STORAGE_UNITS(unit_id,facility_id,type_id,unit_number,status,readiness_verified,dimensions_verified) VALUES ('${ids.unit}',@facility,1,'P46-FIX-${tag}','RESERVED',1,1);
 INSERT RESERVATIONS(reservation_id,customer_id,facility_id,type_id,pricing_id,start_date,end_date,estimated_rental_amount,estimated_deposit_amount,status,quoted_monthly_price,pricing_version) VALUES
 ('${ids.reservation}','${previous.customer}',@facility,1,@pricing,'${today}',DATEADD(month,1,CAST('${today}' AS date)),1000000,100000,'CONFIRMED',1000000,'v2');
 INSERT RENTAL_CONTRACTS(contract_id,contract_number,reservation_id,customer_id,unit_id,start_date,end_date,monthly_rental_fee,deposit_amount,billing_cycle,terms_version,status) VALUES
 ('${ids.contract}','P46-FIX-${tag}','${ids.reservation}','${previous.customer}','${ids.unit}','${today}',DATEADD(month,1,CAST('${today}' AS date)),1000000,100000,'MONTHLY','v2','CONFIRMED');
 COMMIT;SELECT 'FOCUSED_FIXTURE_OK';`;
 const result=sql(q);if(!result.includes('FOCUSED_FIXTURE_OK'))throw new Error(result);evidence.sql.push({label:'fixture creation',result})}

async function main(){fixture();state('before requests');const browser=await chromium.launch({executablePath:'C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe',headless:true});const sessions={};
 try{
  for(const role of ['customer','manager','staff','otherStaff']){
   const context=await browser.newContext({viewport:{width:1366,height:900}}),page=await context.newPage();
   await page.goto(base+'/stitch/ng_nh_p_ng_k_safebox_storage.html',{waitUntil:'domcontentloaded',timeout:15000});
   const r=await page.evaluate(async x=>{const res=await fetch('/api/auth/login',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify(x)});return {status:res.status,body:await res.json().catch(()=>null)}},{email:`phase46-live-246dd488-${role}@example.invalid`,password});
   evidence.http.push({step:`login ${role}`,status:r.status});if(r.status!==200||!r.body?.token)throw new Error(`Login ${role}: ${r.status}`);
   await page.evaluate(({token,user})=>SafeBoxAuth.saveAuth(token,user,false),r.body);sessions[role]={context,page};
  }
  async function api(role,step,method,url,body){if(method!=='GET')identity();const r=await sessions[role].page.evaluate(async({method,url,body})=>{
   const token=localStorage.getItem('safebox.token')||sessionStorage.getItem('safebox.token');
   const response=await fetch(url,{method,headers:{Authorization:`Bearer ${token}`,...(body?{'Content-Type':'application/json'}:{})},body:body?JSON.stringify(body):undefined});
   return {status:response.status,body:await response.json().catch(()=>null)}},{method,url,body});evidence.http.push({step,role,status:r.status,body:r.body});return r}
  const pay=`/api/contracts/${ids.contract}/payments`,pickup=`/api/contracts/${ids.contract}/pickup-appointment`;
  const summary=await api('customer','summary','GET',pay+'/summary');if(summary.status!==200)throw new Error('Summary failed');
  const dep=await api('customer','cash deposit request','POST',pay,{paymentType:'DEPOSIT',paymentMethod:'CASH',amount:summary.body.deposit.remainingAmount,idempotencyKey:`p46-fix-${tag}-deposit`});if(dep.status!==201)throw new Error('Deposit request failed');
  const confirm=id=>`/api/staff/contracts/${ids.contract}/payments/${id}/confirm-cash`;
  await api('staff','denied deposit confirmation','POST',confirm(dep.body.id));
  evidence.sql.push({label:'deposit log count after denied confirmation',result:String(countLog(dep.body.id))});
  await api('manager','manager deposit confirmation','POST',confirm(dep.body.id));
  await api('manager','manager deposit retry','POST',confirm(dep.body.id));
  evidence.sql.push({label:'deposit log count after confirmation and retry',result:String(countLog(dep.body.id))});
  state('deposit paid');
  const when=(d,t)=>({appointmentTime:`${d}T${t}:00+07:00`});
  await api('customer','pickup create 17:00','POST',pickup,when(tomorrow,'17:00'));
  async function form(label,expectedDate,expectedSlot,saveUnchanged=false){
   const page=sessions.customer.page;
   await page.goto(base+`/stitch/uc11-customer-payments-appointments.html?contractId=${ids.contract}`,{waitUntil:'networkidle',timeout:15000});
   await page.locator('#appointment-change').waitFor({state:'visible',timeout:10000});
   await page.locator('#appointment-change').click();
   const date=await page.locator('#appointment-date').inputValue(),slot=await page.locator('#appointment-slot').inputValue();
   evidence.forms.push({label,expectedDate,expectedSlot,date,slot});
   await page.locator('#appointment-dialog').screenshot({path:path.join(out,`${tag}-${label}.png`)});evidence.screenshots.push(`${tag}-${label}.png`);
   if(saveUnchanged){const before=await api('customer',`${label} before save`,'GET',pickup);
    identity();await page.locator('#appointment-submit').click();await page.locator('#appointment-dialog').waitFor({state:'hidden',timeout:10000});
    const after=await api('customer',`${label} after unchanged save`,'GET',pickup);
    evidence.forms.at(-1).unchanged={sameId:before.body.id===after.body.id,sameTime:before.body.appointmentTime===after.body.appointmentTime,appointmentTime:after.body.appointmentTime};}
  }
  await form('saved-17',tomorrow,'17:00',true);
  await api('customer','reschedule 06:00','PUT',pickup,when(tomorrow,'06:00'));await form('saved-06',tomorrow,'06:00');
  await api('customer','reschedule 22:00','PUT',pickup,when(tomorrow,'22:00'));await form('saved-22',tomorrow,'22:00');
  await api('customer','reschedule cross date','PUT',pickup,when(later,'17:00'));await form('saved-cross-date',later,'17:00');
  await api('customer','restore pickup today','PUT',pickup,when(today,'17:00'));
  await api('manager','assign staff','PUT',`/api/manager/contracts/${ids.contract}/pickup-appointment/assignment`,{staffId:previous.staff,reason:'Phase46 focused audit verification'});
  const rentSummary=await api('customer','remaining summary','GET',pay+'/summary');
  const rent=await api('customer','cash remaining request','POST',pay,{paymentType:'REMAINING_RENT',paymentMethod:'CASH',amount:rentSummary.body.initialRent.remainingAmount,idempotencyKey:`p46-fix-${tag}-rent`});if(rent.status!==201)throw new Error('Remaining request failed');
  await api('otherStaff','denied unassigned rent confirmation','POST',confirm(rent.body.id));
  evidence.sql.push({label:'remaining log count after denied confirmation',result:String(countLog(rent.body.id))});
  await api('staff','assigned staff rent confirmation','POST',confirm(rent.body.id));
  await api('staff','assigned staff rent retry','POST',confirm(rent.body.id));
  evidence.sql.push({label:'remaining log count after confirmation and retry',result:String(countLog(rent.body.id))});
  state('final');
  evidence.sql.push({label:'audit and payment exactness',result:sql(`SET NOCOUNT ON;IF DB_NAME()<>'${db}' THROW 51000,'Wrong database',1;
   SELECT payment_type,status,amount,confirmed_by,confirmed_at FROM PAYMENT_TRANSACTIONS WHERE contract_id='${ids.contract}' ORDER BY payment_type;
   SELECT invoice_type,status,total_amount,paid_amount FROM INVOICES WHERE contract_id='${ids.contract}' ORDER BY invoice_type;
   SELECT action_type,entity_id,user_id,description,created_at FROM ACTIVITY_LOGS WHERE action_type='CONFIRM_CASH_PAYMENT' AND entity_id IN (SELECT transaction_id FROM PAYMENT_TRANSACTIONS WHERE contract_id='${ids.contract}') ORDER BY created_at;`)});
 }finally{await browser.close()}}
main().catch(e=>{evidence.failures.push({message:e.message,stack:e.stack});process.exitCode=1}).finally(()=>{fs.writeFileSync(path.join(out,`${tag}-results.json`),JSON.stringify(evidence,null,2));console.log(JSON.stringify({tag,steps:evidence.http.length,forms:evidence.forms,failures:evidence.failures}));});
