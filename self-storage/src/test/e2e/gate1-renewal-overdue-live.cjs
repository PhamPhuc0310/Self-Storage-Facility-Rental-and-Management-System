// Focused isolated-database acceptance. Run only with an identity-verified app.
const {chromium} = require('playwright');
const bcrypt = require('bcryptjs');
const {randomUUID, randomBytes} = require('node:crypto');
const {execFileSync} = require('node:child_process');
const fs = require('node:fs');
const os = require('node:os');
const path = require('node:path');
const assert = require('node:assert/strict');

const db='SelfStorageFacilityDB_Test', pid=Number(process.env.SAFEBOX_GATE_APP_PID), base='http://127.0.0.1:18082';
if (!pid) throw new Error('SAFEBOX_GATE_APP_PID is required');
const out=path.resolve(__dirname,'../../../../docs/testing/evidence/gate1-renewal-overdue');
fs.mkdirSync(out,{recursive:true});
const users=JSON.parse(fs.readFileSync(path.resolve(__dirname,'../../../../docs/testing/evidence/phase46-live/246dd488-results.json'),'utf8')).fixtureIds;
const tag=randomBytes(4).toString('hex'), password=randomBytes(18).toString('base64url'), hash=bcrypt.hashSync(password,10);
const dates={today:new Intl.DateTimeFormat('en-CA',{timeZone:'Asia/Ho_Chi_Minh',year:'numeric',month:'2-digit',day:'2-digit'}).format(new Date())};
const plus=(s,n)=>{const d=new Date(`${s}T00:00:00Z`);d.setUTCDate(d.getUTCDate()+n);return d.toISOString().slice(0,10)};
dates.renewalEnd=plus(dates.today,5); dates.overdueEnd=plus(dates.today,-4); dates.graceEnd=plus(dates.overdueEnd,3);
const ids={renewalContract:randomUUID(),renewalReservation:randomUUID(),renewalUnit:randomUUID(),overdueContract:randomUUID(),overdueReservation:randomUUID(),overdueUnit:randomUUID(),overdueManager:randomUUID()};
const evidence={run:tag,database:db,appPid:pid,dates,ids,http:[],sql:[],screenshots:[],checks:[],failures:[]};
function sql(q){const f=path.join(os.tmpdir(),`gate1-${tag}-${randomBytes(4).toString('hex')}.sql`);fs.writeFileSync(f,q);try{return execFileSync('sqlcmd',['-b','-S','localhost','-E','-C','-d',db,'-l','5','-W','-h','-1','-i',f],{encoding:'utf8',timeout:12000,maxBuffer:2e6}).trim()}finally{fs.rmSync(f,{force:true})}}
function identity(){const s=sql(`SET NOCOUNT ON; IF DB_NAME()<>'${db}' THROW 51000,'Wrong database',1; IF NOT EXISTS(SELECT 1 FROM sys.dm_exec_sessions WHERE host_process_id=${pid} AND DB_NAME(database_id)='${db}') THROW 51001,'Wrong app database',1; IF EXISTS(SELECT 1 FROM sys.dm_exec_sessions WHERE host_process_id=${pid} AND DB_NAME(database_id)<>'${db}') THROW 51002,'Mixed app databases',1; SELECT DB_NAME();`);assert(s.includes(db));}
function snapshot(label){identity();const result=sql(`SET NOCOUNT ON;IF DB_NAME()<>'${db}' THROW 51000,'Wrong database',1;
 SELECT 'contract',contract_number,status,CONVERT(varchar(10),end_date,23) FROM RENTAL_CONTRACTS WHERE contract_id IN ('${ids.renewalContract}','${ids.overdueContract}');
 SELECT 'renewal',renewal_id,status,renewal_amount,pricing_id FROM RENEWALS WHERE contract_id='${ids.renewalContract}';
 SELECT 'penalty',penalty_id,status,penalty_amount,paid_amount,idempotency_key FROM CONTRACT_PENALTIES WHERE contract_id='${ids.overdueContract}' ORDER BY period_date;
 SELECT 'invoice',invoice_id,invoice_type,status,total_amount,paid_amount FROM INVOICES WHERE contract_id IN ('${ids.renewalContract}','${ids.overdueContract}') ORDER BY invoice_type;
 SELECT 'payment',transaction_id,payment_type,payment_method,status,amount FROM PAYMENT_TRANSACTIONS WHERE contract_id IN ('${ids.renewalContract}','${ids.overdueContract}');`);evidence.sql.push({label,result});return result}
function fixture(){identity();const q=`SET QUOTED_IDENTIFIER ON;SET ANSI_NULLS ON;SET ANSI_PADDING ON;SET ANSI_WARNINGS ON;SET ARITHABORT ON;SET CONCAT_NULL_YIELDS_NULL ON;SET NUMERIC_ROUNDABORT OFF;SET XACT_ABORT ON;SET NOCOUNT ON;
 IF DB_NAME()<>'${db}' THROW 51000,'Wrong database',1;BEGIN TRAN;
 IF (SELECT COUNT(*) FROM USERS WHERE user_id IN ('${users.customer}','${users.manager}','${users.crossManager}'))<>3 THROW 51003,'Synthetic users missing',1;
 UPDATE USERS SET password_hash='${hash}' WHERE user_id IN ('${users.customer}','${users.manager}','${users.crossManager}');
 DECLARE @facility uniqueidentifier='3F4E5607-B0B4-F111-A965-932C2C6ADDB7',@overdueFacility uniqueidentifier='414E5607-B0B4-F111-A965-932C2C6ADDB7',@pricing uniqueidentifier='63292355-68BE-F111-A965-932C2C6ADDB7',@overduePricing uniqueidentifier='6E292355-68BE-F111-A965-932C2C6ADDB7';
 IF NOT EXISTS(SELECT 1 FROM PRICING_POLICIES WHERE pricing_id=@pricing AND facility_id=@facility AND status='ACTIVE') THROW 51004,'Price missing',1;
 IF NOT EXISTS(SELECT 1 FROM RENTAL_POLICIES WHERE facility_id=@overdueFacility AND status='ACTIVE' AND effective_from<='${dates.today}' AND overdue_approved_at IS NOT NULL) THROW 51005,'Approved overdue policy missing',1;
 INSERT USERS(user_id,role_id,email,password_hash,full_name,phone,status,email_verified) VALUES ('${ids.overdueManager}',(SELECT role_id FROM ROLES WHERE role_name='FACILITY_MANAGER'),'gate1-${tag}-overdue-manager@example.invalid','${hash}','Gate 1 Overdue Manager','0000000000','ACTIVE',1);
 INSERT USER_FACILITY_ASSIGNMENTS(assignment_id,user_id,facility_id,assigned_by,start_date,status) VALUES (NEWID(),'${ids.overdueManager}',@overdueFacility,'${users.admin}',DATEADD(day,-1,CAST(GETDATE() AS date)),'ACTIVE');
 INSERT STORAGE_UNITS(unit_id,facility_id,type_id,unit_number,status,readiness_verified,dimensions_verified) VALUES
 ('${ids.renewalUnit}',@facility,1,'G1-R-${tag}','OCCUPIED',1,1),('${ids.overdueUnit}',@overdueFacility,1,'G1-O-${tag}','OCCUPIED',1,1);
 INSERT RESERVATIONS(reservation_id,customer_id,facility_id,type_id,pricing_id,start_date,end_date,estimated_rental_amount,estimated_deposit_amount,status,quoted_monthly_price,pricing_version) VALUES
 ('${ids.renewalReservation}','${users.customer}',@facility,1,@pricing,'${plus(dates.today,-25)}','${dates.renewalEnd}',1000000,100000,'CONFIRMED',1000000,'v2'),
 ('${ids.overdueReservation}','${users.customer}',@overdueFacility,1,@overduePricing,'${plus(dates.today,-34)}','${dates.overdueEnd}',1000000,100000,'CONFIRMED',1000000,'v2');
 INSERT RENTAL_CONTRACTS(contract_id,contract_number,reservation_id,customer_id,unit_id,start_date,end_date,monthly_rental_fee,deposit_amount,billing_cycle,terms_version,status) VALUES
 ('${ids.renewalContract}','G1-R-${tag}','${ids.renewalReservation}','${users.customer}','${ids.renewalUnit}','${plus(dates.today,-25)}','${dates.renewalEnd}',1000000,100000,'MONTHLY','v2','ACTIVE'),
 ('${ids.overdueContract}','G1-O-${tag}','${ids.overdueReservation}','${users.customer}','${ids.overdueUnit}','${plus(dates.today,-34)}','${dates.overdueEnd}',1000000,100000,'MONTHLY','v2','ACTIVE');
 COMMIT;SELECT 'GATE1_FIXTURE_OK';`;
 const s=sql(q);assert(s.includes('GATE1_FIXTURE_OK'));evidence.sql.push({label:'guarded synthetic fixture',result:s})}
async function main(){fixture();snapshot('before API');const browser=await chromium.launch({executablePath:'C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe',headless:true});const sessions={};
 try{
  for(const role of ['customer','manager','crossManager','overdueManager']){const context=await browser.newContext({viewport:{width:1366,height:900}}),page=await context.newPage();await page.goto(base+'/stitch/ng_nh_p_ng_k_safebox_storage.html',{waitUntil:'domcontentloaded',timeout:15000});const email=role==='overdueManager'?`gate1-${tag}-overdue-manager@example.invalid`:`phase46-live-246dd488-${role}@example.invalid`;
   const r=await page.evaluate(async x=>{const v=await fetch('/api/auth/login',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify(x)});return {status:v.status,body:await v.json().catch(()=>null)}},{email,password});evidence.http.push({step:`login ${role}`,status:r.status});assert.equal(r.status,200);assert(r.body.token);await page.evaluate(({token,user})=>SafeBoxAuth.saveAuth(token,user,false),r.body);sessions[role]={context,page};}
  async function api(role,step,method,url,body){if(method!=='GET')identity();const r=await sessions[role].page.evaluate(async({method,url,body})=>{const token=localStorage.getItem('safebox.token')||sessionStorage.getItem('safebox.token');const v=await fetch(url,{method,headers:{Authorization:`Bearer ${token}`,...(body?{'Content-Type':'application/json'}:{})},body:body?JSON.stringify(body):undefined});return {status:v.status,body:await v.json().catch(()=>null)}},{method,url,body});evidence.http.push({step,role,method,url,status:r.status,body:r.body});return r}
  async function shot(role,step,url){const page=sessions[role].page;await page.goto(base+url,{waitUntil:'networkidle',timeout:15000});const name=`${tag}-${step}.png`;await page.screenshot({path:path.join(out,name),fullPage:true});evidence.screenshots.push(name)}
  const cr=ids.renewalContract, co=ids.overdueContract;
  let r=await api('customer','renewal preview','GET',`/api/customer/contracts/${cr}/renewals/preview?months=1`);assert.equal(r.status,200);evidence.checks.push({step:'renewal preview',body:r.body});
  await shot('customer','renewal-preview','/stitch/gia_han_hop_dong_safebox_storage.html');
  r=await api('customer','renewal request','POST',`/api/customer/contracts/${cr}/renewals`,{months:1});assert.equal(r.status,201);const renewalId=r.body.renewalId;
  r=await api('customer','duplicate renewal request','POST',`/api/customer/contracts/${cr}/renewals`,{months:1});evidence.checks.push({step:'duplicate renewal request',status:r.status});
  r=await api('crossManager','cross facility renewal approve','POST',`/api/manager/renewals/${renewalId}/approve`,{});evidence.checks.push({step:'cross facility approval denied',status:r.status});
  r=await api('manager','renewal approve','POST',`/api/manager/renewals/${renewalId}/approve`,{});assert.equal(r.status,200);
  await shot('manager','renewal-approved','/stitch/quan_ly_gia_han_safebox_manager.html');
  r=await api('manager','repeat renewal approve','POST',`/api/manager/renewals/${renewalId}/approve`,{});evidence.checks.push({step:'repeat renewal approval',status:r.status});
  r=await api('customer','invalid renewal amount','POST',`/api/customer/renewals/${renewalId}/pay`,{paymentMethod:'BANK_TRANSFER',amount:1,idempotencyKey:`g1-invalid-${tag}`});evidence.checks.push({step:'invalid renewal amount',status:r.status});
  snapshot('renewal approved and unpaid');
  r=await api('manager','cross facility overdue calculate','POST',`/api/manager/overdue/contracts/${co}/calculate-fee?date=${dates.today}`);evidence.checks.push({step:'cross facility overdue denied',status:r.status});
  r=await api('overdueManager','overdue end day rejected','POST',`/api/manager/overdue/contracts/${co}/calculate-fee?date=${dates.overdueEnd}`);evidence.checks.push({step:'end day no overdue',status:r.status});
  r=await api('overdueManager','grace boundary','POST',`/api/manager/overdue/contracts/${co}/calculate-fee?date=${dates.graceEnd}`);assert.equal(r.status,200);evidence.checks.push({step:'grace boundary',body:r.body});
  const graceId=r.body.penaltyId;r=await api('overdueManager','grace boundary retry','POST',`/api/manager/overdue/contracts/${co}/calculate-fee?date=${dates.graceEnd}`);evidence.checks.push({step:'grace retry same id',sameId:r.body?.penaltyId===graceId,status:r.status});
  r=await api('overdueManager','first billable day','POST',`/api/manager/overdue/contracts/${co}/calculate-fee?date=${dates.today}`);assert.equal(r.status,200);evidence.checks.push({step:'first billable day',body:r.body});const penaltyId=r.body.penaltyId,amount=r.body.remainingAmount;
  r=await api('overdueManager','first billable day retry','POST',`/api/manager/overdue/contracts/${co}/calculate-fee?date=${dates.today}`);evidence.checks.push({step:'billable retry same id',sameId:r.body?.penaltyId===penaltyId,status:r.status});
  await shot('customer','overdue-customer','/stitch/qua_han_cong_no_khach_hang.html');await shot('overdueManager','overdue-manager','/stitch/quan_ly_qua_han_safebox_manager.html');
  r=await api('customer','bank transfer request pending','POST',`/api/customer/overdue/penalties/${penaltyId}/pay`,{paymentMethod:'BANK_TRANSFER',amount,idempotencyKey:`g1-pending-${tag}`,referenceCode:`G1-DEMO-${tag}`});evidence.checks.push({step:'bank request pending',status:r.status,body:r.body});
  r=await api('customer','repeat bank request denied','POST',`/api/customer/overdue/penalties/${penaltyId}/pay`,{paymentMethod:'BANK_TRANSFER',amount,idempotencyKey:`g1-pending-${tag}`,referenceCode:`G1-DEMO-${tag}`});evidence.checks.push({step:'repeat bank request',status:r.status});
  snapshot('pending bank request, no settlement');
  r=await api('manager','cross facility approval denied','POST',`/api/manager/overdue/penalties/${penaltyId}/approve-payment`,{approved:true,note:'Scope test only'});evidence.checks.push({step:'cross facility payment approval denied',status:r.status});
  r=await api('overdueManager','reject unverified bank request','POST',`/api/manager/overdue/penalties/${penaltyId}/approve-payment`,{approved:false,note:'No bank evidence; synthetic request rejected'});evidence.checks.push({step:'reject unverified bank request',status:r.status,body:r.body});
  snapshot('unverified transfer rejected');
 }finally{await browser.close()}}
main().catch(e=>{evidence.failures.push({message:e.message,stack:e.stack});process.exitCode=1}).finally(()=>{fs.writeFileSync(path.join(out,`${tag}-results.json`),JSON.stringify(evidence,null,2));console.log(JSON.stringify({run:tag,http:evidence.http.length,checks:evidence.checks.map(x=>({step:x.step,status:x.status,sameId:x.sameId})),failures:evidence.failures}));});
