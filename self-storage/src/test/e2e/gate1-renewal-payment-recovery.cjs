// Focused UC17 recovery against existing synthetic Gate 1 invoices, never real payment data.
const {chromium}=require('playwright'),bcrypt=require('bcryptjs'),{randomBytes}=require('node:crypto');
const {execFileSync}=require('node:child_process'),fs=require('node:fs'),os=require('node:os'),path=require('node:path'),assert=require('node:assert/strict');
const db='SelfStorageFacilityDB_Test',pid=Number(process.env.SAFEBOX_GATE_APP_PID),base='http://127.0.0.1:18082';
if(!pid)throw new Error('SAFEBOX_GATE_APP_PID required');
const out=path.resolve(__dirname,'../../../../docs/testing/evidence/gate1-renewal-recovery');fs.mkdirSync(out,{recursive:true});
const priorDir=path.resolve(__dirname,'../../../../docs/testing/evidence/gate1-renewal-overdue');
const cashRun=JSON.parse(fs.readFileSync(path.join(priorDir,'bce7ac8e-results.json'),'utf8'));
const bankRun=JSON.parse(fs.readFileSync(path.join(priorDir,'a83cc8f1-results.json'),'utf8'));
const cash={contractId:cashRun.ids.renewalContract,...cashRun.http.find(x=>x.step==='renewal approve').body};
const bank={contractId:bankRun.ids.renewalContract,...bankRun.http.find(x=>x.step==='renewal approve').body};
const users=JSON.parse(fs.readFileSync(path.resolve(__dirname,'../../../../docs/testing/evidence/phase46-live/246dd488-results.json'),'utf8')).fixtureIds;
const tag=randomBytes(4).toString('hex'),password=randomBytes(18).toString('base64url'),hash=bcrypt.hashSync(password,10);
const evidence={run:tag,database:db,appPid:pid,cash,bank,http:[],sql:[],screenshots:[],dom:[],failures:[]};
function sql(q){const f=path.join(os.tmpdir(),`gate1-recovery-${tag}-${randomBytes(4).toString('hex')}.sql`);fs.writeFileSync(f,q);try{return execFileSync('sqlcmd',['-b','-S','localhost','-E','-C','-d',db,'-l','5','-W','-h','-1','-i',f],{encoding:'utf8',timeout:12000,maxBuffer:2e6}).trim()}finally{fs.rmSync(f,{force:true})}}
function identity(){const s=sql(`SET NOCOUNT ON;IF DB_NAME()<>'${db}' THROW 51000,'Wrong database',1;IF NOT EXISTS(SELECT 1 FROM sys.dm_exec_sessions WHERE host_process_id=${pid} AND DB_NAME(database_id)='${db}') THROW 51001,'Wrong app database',1;IF EXISTS(SELECT 1 FROM sys.dm_exec_sessions WHERE host_process_id=${pid} AND DB_NAME(database_id)<>'${db}') THROW 51002,'Mixed app databases',1;SELECT DB_NAME();`);assert(s.includes(db))}
function snapshot(label){identity();evidence.sql.push({label,result:sql(`SET NOCOUNT ON;IF DB_NAME()<>'${db}' THROW 51000,'Wrong database',1;
 SELECT 'contract',contract_id,status,CONVERT(varchar(10),end_date,23),monthly_rental_fee FROM RENTAL_CONTRACTS WHERE contract_id IN ('${cash.contractId}','${bank.contractId}') ORDER BY contract_id;
 SELECT 'renewal',renewal_id,status,old_end_date,new_end_date,renewal_amount,pricing_id FROM RENEWALS WHERE renewal_id IN ('${cash.renewalId}','${bank.renewalId}') ORDER BY renewal_id;
 SELECT 'invoice',invoice_id,renewal_id,status,total_amount,paid_amount FROM INVOICES WHERE invoice_id IN ('${cash.invoiceId}','${bank.invoiceId}') ORDER BY invoice_id;
 SELECT 'payment',transaction_id,invoice_id,payment_method,status,amount,created_by,confirmed_by,confirmed_at,failure_reason FROM PAYMENT_TRANSACTIONS WHERE invoice_id IN ('${cash.invoiceId}','${bank.invoiceId}') ORDER BY invoice_id,transaction_date;
 SELECT 'audit',action_type,entity_id,user_id,description,created_at FROM ACTIVITY_LOGS WHERE action_type IN ('CONFIRM_RENEWAL_PAYMENT','REJECT_RENEWAL_PAYMENT') AND entity_id IN (SELECT transaction_id FROM PAYMENT_TRANSACTIONS WHERE invoice_id IN ('${cash.invoiceId}','${bank.invoiceId}')) ORDER BY created_at;`)})}
function prepare(){identity();const q=`SET QUOTED_IDENTIFIER ON;SET ANSI_NULLS ON;SET ANSI_PADDING ON;SET ANSI_WARNINGS ON;SET ARITHABORT ON;SET CONCAT_NULL_YIELDS_NULL ON;SET NUMERIC_ROUNDABORT OFF;SET XACT_ABORT ON;SET NOCOUNT ON;IF DB_NAME()<>'${db}' THROW 51000,'Wrong database',1;BEGIN TRAN;
 IF (SELECT COUNT(*) FROM RENEWALS WHERE renewal_id IN ('${cash.renewalId}','${bank.renewalId}') AND status='APPROVED')<>2 THROW 51003,'Renewal fixtures no longer approved',1;
 IF (SELECT COUNT(*) FROM INVOICES WHERE invoice_id IN ('${cash.invoiceId}','${bank.invoiceId}') AND status='UNPAID' AND paid_amount=0)<>2 THROW 51004,'Invoices no longer unpaid',1;
 IF EXISTS(SELECT 1 FROM PAYMENT_TRANSACTIONS WHERE invoice_id IN ('${cash.invoiceId}','${bank.invoiceId}')) THROW 51005,'Fixtures already have payments',1;
 UPDATE USERS SET password_hash='${hash}' WHERE user_id IN ('${users.customer}','${users.manager}','${users.crossManager}','${users.staff}','${users.admin}');
 IF @@ROWCOUNT<>5 THROW 51006,'Synthetic users missing',1;COMMIT;SELECT 'RECOVERY_FIXTURES_READY';`;
 assert(sql(q).includes('RECOVERY_FIXTURES_READY'))}
async function main(){prepare();snapshot('before customer payment requests');const browser=await chromium.launch({executablePath:'C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe',headless:true});const sessions={};
 try{
  for(const role of ['customer','manager','crossManager','staff','admin']){const context=await browser.newContext({viewport:{width:1366,height:900}}),page=await context.newPage();await page.goto(base+'/stitch/ng_nh_p_ng_k_safebox_storage.html',{waitUntil:'domcontentloaded',timeout:15000});const email=`phase46-live-246dd488-${role}@example.invalid`;
   const r=await page.evaluate(async x=>{const v=await fetch('/api/auth/login',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify(x)});return{status:v.status,body:await v.json().catch(()=>null)}},{email,password});evidence.http.push({step:`login ${role}`,status:r.status});assert.equal(r.status,200);assert(r.body.token);await page.evaluate(({token,user})=>SafeBoxAuth.saveAuth(token,user,false),r.body);sessions[role]={context,page};}
  async function api(role,step,method,url,body){if(method!=='GET')identity();const r=await sessions[role].page.evaluate(async({method,url,body})=>{const token=localStorage.getItem('safebox.token')||sessionStorage.getItem('safebox.token');const v=await fetch(url,{method,headers:{Authorization:`Bearer ${token}`,...(body?{'Content-Type':'application/json'}:{})},body:body?JSON.stringify(body):undefined});return{status:v.status,body:await v.json().catch(()=>null)}},{method,url,body});evidence.http.push({role,step,method,url,status:r.status,body:r.body});return r}
  async function shot(role,name,url){const page=sessions[role].page;await page.goto(base+url,{waitUntil:'networkidle',timeout:15000});const file=`${tag}-${name}.png`;await page.screenshot({path:path.join(out,file),fullPage:true});evidence.screenshots.push(file)}
  const payCash={paymentMethod:'CASH',amount:cash.renewalAmount,idempotencyKey:`gate1-recovery-${tag}-cash`};
  let r=await api('customer','cash renewal request','POST',`/api/customer/renewals/${cash.renewalId}/pay`,payCash);assert.equal(r.status,200);assert.equal(r.body.status,'PENDING');const cashPaymentId=r.body.transactionId;
  r=await api('customer','same cash request idempotent','POST',`/api/customer/renewals/${cash.renewalId}/pay`,payCash);assert.equal(r.status,200);assert.equal(r.body.transactionId,cashPaymentId);
  r=await api('customer','different key blocked while cash pending','POST',`/api/customer/renewals/${cash.renewalId}/pay`,{...payCash,idempotencyKey:`gate1-recovery-${tag}-cash-new`});assert.equal(r.status,409);
  r=await api('customer','renewal cancellation blocked while cash pending','POST',`/api/customer/renewals/${cash.renewalId}/cancel`);assert.equal(r.status,409);
  r=await api('customer','alternate invoice pay route blocked','POST',`/api/invoices/my/${cash.invoiceId}/pay`,{paymentMethod:'CASH'});assert.equal(r.status,409);
  snapshot('cash request pending, invoice unpaid, end unchanged');
  await shot('customer','customer-pending','/stitch/gia_han_hop_dong_safebox_storage.html');
  const confirm=`/api/manager/renewals/${cash.renewalId}/payments/${cashPaymentId}/confirm`;
  for(const role of ['customer','staff','admin','crossManager']){r=await api(role,`${role} cannot confirm cash`,'POST',confirm,{});assert.equal(r.status,403)}
  snapshot('after unauthorized confirmations');
  await shot('manager','manager-queue','/stitch/quan_ly_gia_han_safebox_manager.html');
  const queue=sessions.manager.page.locator('#renewal-payment-queue');assert(await queue.textContent().then(x=>x.includes(cashPaymentId.substring(0,8)) || x.includes(cash.renewalId.substring(0,8))));
  sessions.manager.page.once('dialog',dialog=>dialog.accept());
  const response=sessions.manager.page.waitForResponse(x=>new URL(x.url()).pathname===confirm && x.request().method()==='POST');
  identity();await sessions.manager.page.locator(`#renewal-payment-queue button[onclick*="${cashPaymentId}"]`).first().click();const confirmed=await response;assert.equal(confirmed.status(),200);assert.equal((await confirmed.json()).status,'SUCCESS');
  snapshot('after authorized cash confirmation');
  r=await api('manager','repeat confirmation idempotent','POST',confirm,{});assert.equal(r.status,200);assert.equal(r.body.status,'SUCCESS');
  snapshot('after repeat confirmation');
  await shot('customer','customer-completed','/stitch/gia_han_hop_dong_safebox_storage.html');
  await shot('manager','manager-completed','/stitch/quan_ly_gia_han_safebox_manager.html');
  const payBank={paymentMethod:'BANK_TRANSFER',amount:bank.renewalAmount,idempotencyKey:`gate1-recovery-${tag}-bank`};
  r=await api('customer','bank transfer request through alternate renewal route','POST',`/api/contracts/my/renewals/${bank.renewalId}/pay`,payBank);assert.equal(r.status,200);assert.equal(r.body.status,'PENDING');const bankPaymentId=r.body.transactionId;
  r=await api('manager','bank confirmation without independent evidence denied','POST',`/api/manager/renewals/${bank.renewalId}/payments/${bankPaymentId}/confirm`,{});assert.equal(r.status,400);
  snapshot('bank request pending without verification');
  r=await api('manager','reject unverified bank request','POST',`/api/manager/renewals/${bank.renewalId}/payments/${bankPaymentId}/reject`,{reason:'No verified bank receipt for synthetic request'});assert.equal(r.status,200);assert.equal(r.body.status,'FAILED');
  r=await api('manager','rejected payment cannot confirm','POST',`/api/manager/renewals/${bank.renewalId}/payments/${bankPaymentId}/confirm`,{verificationReference:'NOT-REAL'});assert.equal(r.status,409);
  snapshot('final: cash once, bank rejected, audit once');
 }finally{await browser.close()}}
main().catch(e=>{evidence.failures.push({message:e.message,stack:e.stack});process.exitCode=1}).finally(()=>{fs.writeFileSync(path.join(out,`${tag}-results.json`),JSON.stringify(evidence,null,2));console.log(JSON.stringify({run:tag,steps:evidence.http.length,screenshots:evidence.screenshots,failures:evidence.failures}));});
