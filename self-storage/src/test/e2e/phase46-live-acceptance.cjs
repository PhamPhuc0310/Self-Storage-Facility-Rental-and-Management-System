// Live acceptance uses the local profile on port 18080 and ONLY the named SQL test database.
// Run once per isolated fixture. Never point DB_URL at the primary database.
const {chromium} = require('playwright');
const bcrypt = require('bcryptjs');
const {randomUUID, randomBytes} = require('node:crypto');
const {execFileSync} = require('node:child_process');
const fs = require('node:fs');
const os = require('node:os');
const path = require('node:path');

const DB = 'SelfStorageFacilityDB_Test';
const BASE = 'http://127.0.0.1:18080';
const out = path.resolve(__dirname, '../../../../docs/testing/evidence/phase46-live');
fs.mkdirSync(out, {recursive:true});
const run = process.env.SAFEBOX_LIVE_RESUME || randomBytes(4).toString('hex');
const password = randomBytes(18).toString('base64url');
const hash = bcrypt.hashSync(password, 10);
const ids = process.env.SAFEBOX_LIVE_RESUME
  ? JSON.parse(fs.readFileSync(path.join(out,`${run}-results.json`),'utf8')).fixtureIds
  : Object.fromEntries(['customer','manager','crossManager','staff','otherStaff','admin','cash','bank','return','cashUnit','bankUnit','returnUnit','cashReservation','bankReservation','returnReservation'].map(k => [k,randomUUID()]));
const prior = process.env.SAFEBOX_LIVE_RESUME ? JSON.parse(fs.readFileSync(path.join(out,`${run}-results.json`),'utf8')) : null;
const email = role => `phase46-live-${run}-${role}@example.invalid`;
const today = new Intl.DateTimeFormat('en-CA',{timeZone:'Asia/Ho_Chi_Minh',year:'numeric',month:'2-digit',day:'2-digit'}).format(new Date());
const plus = (date,days) => {const d=new Date(`${date}T00:00:00Z`);d.setUTCDate(d.getUTCDate()+days);return d.toISOString().slice(0,10)};
const tomorrow = plus(today,1);
const evidence = {run, database:DB, fixtureIds:ids, dates:{today,tomorrow}, http:prior?.http||[], sql:prior?.sql||[], screenshots:prior?.screenshots||[], defects:[]};

function sql(query) {
  const file=path.join(os.tmpdir(),`phase46-${run}-${randomBytes(4).toString('hex')}.sql`);
  fs.writeFileSync(file,query,'utf8');
  try { return execFileSync('sqlcmd',['-b','-S','localhost','-E','-C','-d',DB,'-l','5','-W','-h','-1','-i',file],{encoding:'utf8',timeout:12000,maxBuffer:1024*1024}).trim(); }
  catch(error) {throw new Error(`SQL command failed: ${String(error.stdout||'').trim()} ${String(error.stderr||'').trim()}`)}
  finally {fs.rmSync(file,{force:true})}
}
function identity() {
  const result=sql(`SET NOCOUNT ON; IF DB_NAME() <> '${DB}' THROW 51000, 'Wrong database', 1;
    IF NOT EXISTS(SELECT 1 FROM sys.dm_exec_sessions WHERE host_process_id=24684 AND DB_NAME(database_id)='${DB}') THROW 51001, 'App is not connected to isolated database', 1;
    SELECT DB_NAME();`);
  if (!result.includes(DB)) throw new Error('Database identity not verified');
  return result;
}
function snapshot(label) {
  const result=sql(`SET NOCOUNT ON; IF DB_NAME() <> '${DB}' THROW 51000, 'Wrong database', 1;
    SELECT 'payments',payment_type,payment_method,status,COUNT(*),COALESCE(SUM(amount),0) FROM PAYMENT_TRANSACTIONS WHERE contract_id IN ('${ids.cash}','${ids.bank}') GROUP BY payment_type,payment_method,status;
    SELECT 'invoices',invoice_type,status,COUNT(*),COALESCE(SUM(paid_amount),0) FROM INVOICES WHERE contract_id IN ('${ids.cash}','${ids.bank}') GROUP BY invoice_type,status;
    SELECT 'pickup',status,COUNT(*) FROM PICKUP_APPOINTMENTS WHERE contract_id='${ids.cash}' GROUP BY status;
    SELECT 'returns',status,COUNT(*) FROM HANDOVER_RECORDS WHERE contract_id='${ids.return}' AND handover_type='RETURN' GROUP BY status;
    SELECT 'audit',COUNT(*) FROM ACTIVITY_LOGS WHERE entity_id IN ('${ids.cash}','${ids.bank}') OR description LIKE '%${run}%';`);
  evidence.sql.push({label,result}); return result;
}
function fixture() {
  identity();
  if(process.env.SAFEBOX_LIVE_RESUME) {
    const result=sql(`SET QUOTED_IDENTIFIER ON; SET ANSI_NULLS ON; SET ANSI_PADDING ON; SET ANSI_WARNINGS ON; SET ARITHABORT ON; SET CONCAT_NULL_YIELDS_NULL ON; SET NUMERIC_ROUNDABORT OFF;
      SET XACT_ABORT ON; SET NOCOUNT ON; IF DB_NAME() <> '${DB}' THROW 51000,'Wrong database',1;
      BEGIN TRAN; IF (SELECT COUNT(*) FROM USERS WHERE email LIKE 'phase46-live-${run}-%')<>6 THROW 51003,'Fixture identity mismatch',1;
      UPDATE USERS SET password_hash='${hash}' WHERE email LIKE 'phase46-live-${run}-%';
      UPDATE RESERVATIONS SET end_date=DATEADD(month,1,start_date) WHERE reservation_id IN ('${ids.cashReservation}','${ids.bankReservation}');
      UPDATE RENTAL_CONTRACTS SET end_date=DATEADD(month,1,start_date) WHERE contract_id IN ('${ids.cash}','${ids.bank}');
      COMMIT; SELECT 'FIXTURE_RESUMED';`);
    evidence.sql.push({label:'fixture correction; DB_NAME guarded',result});return;
  }
  const roles={customer:'CUSTOMER',manager:'FACILITY_MANAGER',crossManager:'FACILITY_MANAGER',staff:'FACILITY_STAFF',otherStaff:'FACILITY_STAFF',admin:'SYSTEM_ADMIN'};
  const userRows=Object.entries(roles).map(([k,r])=>`('${ids[k]}',(SELECT role_id FROM ROLES WHERE role_name='${r}'),'${email(k)}','${hash}','Phase46 Live ${k}','0000000000','ACTIVE',1)`).join(',\n');
  const q=`SET QUOTED_IDENTIFIER ON; SET ANSI_NULLS ON; SET ANSI_PADDING ON; SET ANSI_WARNINGS ON; SET ARITHABORT ON; SET CONCAT_NULL_YIELDS_NULL ON; SET NUMERIC_ROUNDABORT OFF;
    SET XACT_ABORT ON; SET NOCOUNT ON; IF DB_NAME() <> '${DB}' THROW 51000,'Wrong database',1;
    BEGIN TRAN;
    DECLARE @f1 uniqueidentifier='3F4E5607-B0B4-F111-A965-932C2C6ADDB7', @f2 uniqueidentifier='404E5607-B0B4-F111-A965-932C2C6ADDB7';
    DECLARE @pricing uniqueidentifier=(SELECT TOP 1 pricing_id FROM PRICING_POLICIES WHERE facility_id=@f1 AND type_id=1 AND status='ACTIVE');
    IF @pricing IS NULL OR EXISTS(SELECT 1 FROM USERS WHERE email LIKE 'phase46-live-${run}-%') THROW 51002,'Fixture precondition failed',1;
    INSERT USERS(user_id,role_id,email,password_hash,full_name,phone,status,email_verified) VALUES ${userRows};
    INSERT USER_FACILITY_ASSIGNMENTS(assignment_id,user_id,facility_id,assigned_by,start_date,status) VALUES
      (NEWID(),'${ids.manager}',@f1,'${ids.admin}',DATEADD(day,-1,CAST(GETDATE() AS date)),'ACTIVE'),
      (NEWID(),'${ids.crossManager}',@f2,'${ids.admin}',DATEADD(day,-1,CAST(GETDATE() AS date)),'ACTIVE'),
      (NEWID(),'${ids.staff}',@f1,'${ids.admin}',DATEADD(day,-1,CAST(GETDATE() AS date)),'ACTIVE'),
      (NEWID(),'${ids.otherStaff}',@f1,'${ids.admin}',DATEADD(day,-1,CAST(GETDATE() AS date)),'ACTIVE');
    INSERT STORAGE_UNITS(unit_id,facility_id,type_id,unit_number,status,readiness_verified,dimensions_verified) VALUES
      ('${ids.cashUnit}',@f1,1,'P46-${run}-CASH','RESERVED',1,1),
      ('${ids.bankUnit}',@f1,1,'P46-${run}-BANK','RESERVED',1,1),
      ('${ids.returnUnit}',@f1,1,'P46-${run}-RETURN','OCCUPIED',1,1);
    INSERT RESERVATIONS(reservation_id,customer_id,facility_id,type_id,pricing_id,start_date,end_date,estimated_rental_amount,estimated_deposit_amount,status,quoted_monthly_price,pricing_version) VALUES
      ('${ids.cashReservation}','${ids.customer}',@f1,1,@pricing,'${today}',DATEADD(month,1,CAST('${today}' AS date)),1000000,100000,'CONFIRMED',1000000,'v2'),
      ('${ids.bankReservation}','${ids.customer}',@f1,1,@pricing,'${today}',DATEADD(month,1,CAST('${today}' AS date)),1000000,100000,'CONFIRMED',1000000,'v2'),
      ('${ids.returnReservation}','${ids.customer}',@f1,1,@pricing,'${plus(today,-1)}','${tomorrow}',1000000,100000,'CONFIRMED',1000000,'v2');
    INSERT RENTAL_CONTRACTS(contract_id,contract_number,reservation_id,customer_id,unit_id,start_date,end_date,actual_start_date,monthly_rental_fee,deposit_amount,billing_cycle,terms_version,status) VALUES
      ('${ids.cash}','P46-${run}-CASH','${ids.cashReservation}','${ids.customer}','${ids.cashUnit}','${today}',DATEADD(month,1,CAST('${today}' AS date)),NULL,1000000,100000,'MONTHLY','v2','CONFIRMED'),
      ('${ids.bank}','P46-${run}-BANK','${ids.bankReservation}','${ids.customer}','${ids.bankUnit}','${today}',DATEADD(month,1,CAST('${today}' AS date)),NULL,1000000,100000,'MONTHLY','v2','CONFIRMED'),
      ('${ids.return}','P46-${run}-RETURN','${ids.returnReservation}','${ids.customer}','${ids.returnUnit}','${plus(today,-1)}','${tomorrow}','${plus(today,-1)}',1000000,100000,'MONTHLY','v2','ACTIVE');
    COMMIT; SELECT 'FIXTURE_OK' AS result;`;
  const result=sql(q);if(!result.includes('FIXTURE_OK')) throw new Error(result);
  evidence.sql.push({label:'fixture creation; DB_NAME guarded',result});
}

async function main() {
  identity(); fixture(); snapshot('before API actions');
  const browser=await chromium.launch({executablePath:'C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe',headless:true});
  const sessions={};
  try {
    for(const role of ['customer','manager','crossManager','staff','otherStaff','admin']) {
      const context=await browser.newContext({viewport:{width:1366,height:900}});
      const page=await context.newPage();
      await page.goto(BASE+'/stitch/ng_nh_p_ng_k_safebox_storage.html',{waitUntil:'domcontentloaded',timeout:15000});
      const login=await page.evaluate(async({email,password})=>{const r=await fetch('/api/auth/login',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({email,password})});return {status:r.status,body:await r.json().catch(()=>null)}},{email:email(role),password});
      evidence.http.push({step:`login ${role}`,status:login.status});
      if(login.status!==200||!login.body?.token) throw new Error(`Login ${role}: HTTP ${login.status}`);
      await page.evaluate(({token,user})=>SafeBoxAuth.saveAuth(token,user,false),login.body);
      sessions[role]={context,page};
    }
    async function api(role,step,method,url,body) {
      if(method!=='GET') identity();
      const result=await sessions[role].page.evaluate(async({method,url,body})=>{
        const token=localStorage.getItem('safebox.token')||sessionStorage.getItem('safebox.token');
        const r=await fetch(url,{method,headers:{Authorization:`Bearer ${token}`,...(body?{'Content-Type':'application/json'}:{})},body:body?JSON.stringify(body):undefined});
        return {status:r.status,body:await r.json().catch(()=>null)};
      },{method,url,body});
      evidence.http.push({step,role,method,url,status:result.status,body:result.body});
      return result;
    }
    if(process.env.SAFEBOX_LIVE_HANDOVER) {
      identity();
      const prep=sql(`SET QUOTED_IDENTIFIER ON; SET ANSI_NULLS ON; SET ANSI_PADDING ON; SET ANSI_WARNINGS ON; SET ARITHABORT ON; SET CONCAT_NULL_YIELDS_NULL ON; SET NUMERIC_ROUNDABORT OFF;
        SET XACT_ABORT ON; SET NOCOUNT ON; IF DB_NAME() <> '${DB}' THROW 51000,'Wrong database',1;
        BEGIN TRAN;
        UPDATE PICKUP_APPOINTMENTS SET appointment_time='${today}T06:00:00',updated_at=GETDATE()
        WHERE contract_id='${ids.cash}' AND status='SCHEDULED' AND assigned_staff_id='${ids.staff}';
        IF @@ROWCOUNT<>1 THROW 51004,'Synthetic appointment precondition failed',1;
        COMMIT; SELECT 'SYNTHETIC_DUE_TIME';`);
      evidence.sql.push({label:'synthetic due-time fixture; DB_NAME guarded',result:prep});
      const body={conditionStatus:'GOOD',accessMethod:'KEY',accessIdentifier:`P46-${run}`,customerSignature:'Phase46 synthetic customer',staffSignature:'Phase46 synthetic staff',estimatedRepairCost:0};
      await api('staff','handover after all conditions','POST',`/api/staff/contracts/${ids.cash}/handover`,body);
      await api('staff','handover idempotency retry','POST',`/api/staff/contracts/${ids.cash}/handover`,body);
      evidence.sql.push({label:'handover final state',result:sql(`SET NOCOUNT ON; IF DB_NAME() <> '${DB}' THROW 51000,'Wrong database',1;
        SELECT status FROM RENTAL_CONTRACTS WHERE contract_id='${ids.cash}';
        SELECT status FROM STORAGE_UNITS WHERE unit_id='${ids.cashUnit}';
        SELECT status,COUNT(*) FROM PICKUP_APPOINTMENTS WHERE contract_id='${ids.cash}' GROUP BY status;
        SELECT status,COUNT(*) FROM HANDOVER_RECORDS WHERE contract_id='${ids.cash}' AND handover_type='CHECK_IN' GROUP BY status;
        SELECT COUNT(*) FROM INSPECTION_REPORTS WHERE contract_id='${ids.cash}' AND inspection_type='CHECK_IN';`)});
      snapshot('after synthetic due-time handover');
      return;
    }
    if(process.env.SAFEBOX_LIVE_FOLLOWUP) {
      const cash=ids.cash,ret=ids.return;
      const pickup=`/api/contracts/${cash}/pickup-appointment`;
      const returnUrl=`/api/customer/rentals/${ret}/return-schedule`;
      const schedule=(date,time)=>({appointmentTime:`${date}T${time}:00+07:00`});
      await sessions.customer.page.goto(BASE+`/stitch/uc11-customer-payments-appointments.html?contractId=${cash}`,{waitUntil:'networkidle'});
      await sessions.customer.page.locator('#appointment-change').click();
      await sessions.customer.page.locator('#appointment-dialog').screenshot({path:path.join(out,`${run}-pickup-form.png`)});
      evidence.screenshots.push(`${run}-pickup-form.png`);
      await sessions.customer.page.goto(BASE+`/stitch/dat_lich_tra_kho_safebox_storage.html?contractId=${ret}`,{waitUntil:'networkidle'});
      await sessions.customer.page.locator('#form-view').waitFor({state:'visible',timeout:10000});
      await sessions.customer.page.locator('#schedule-form').screenshot({path:path.join(out,`${run}-return-form.png`)});
      evidence.screenshots.push(`${run}-return-form.png`);
      await sessions.customer.page.goto(BASE+`/stitch/uc11-customer-payments-appointments.html?contractId=${ids.bank}`,{waitUntil:'networkidle'});
      await sessions.customer.page.locator('.pay-button[data-type="DEPOSIT"]').click();
      await sessions.customer.page.locator('#payment-method').selectOption('BANK_TRANSFER');
      const qrImg=sessions.customer.page.locator('#payment-bank-qr');
      await qrImg.waitFor({state:'visible',timeout:10000});
      evidence.qrDetail=await qrImg.evaluate(img=>({complete:img.complete,naturalWidth:img.naturalWidth,naturalHeight:img.naturalHeight}));
      await qrImg.screenshot({path:path.join(out,`${run}-qr-image.png`)});
      evidence.screenshots.push(`${run}-qr-image.png`);
      await api('customer','conflict setup pickup tomorrow','PUT',pickup,schedule(tomorrow,'17:00'));
      await api('manager','conflict setup assign staff','PUT',`/api/manager/contracts/${cash}/pickup-appointment/assignment`,{staffId:ids.staff,reason:'Phase46 staff conflict check'});
      const body={contractId:ret,scheduledAt:`${tomorrow}T17:00:00`,note:`Phase46 ${run} conflict check`,checklistEmptyConfirmed:true,checklistAssetsConfirmed:true};
      const first=await api('customer','return with staff conflict','POST',returnUrl,body);
      const duplicate=await api('customer','duplicate active return denied','POST',returnUrl,body);
      const sqlBefore=sql(`SET NOCOUNT ON; IF DB_NAME() <> '${DB}' THROW 51000,'Wrong database',1; SELECT COUNT(*) FROM HANDOVER_RECORDS WHERE contract_id='${ret}' AND handover_type='RETURN' AND status='SCHEDULED'; SELECT COUNT(*) FROM PICKUP_APPOINTMENTS WHERE contract_id='${cash}' AND status='SCHEDULED';`);
      evidence.sql.push({label:'conflict and duplicate appointment state',result:sqlBefore});
      evidence.followup={assignedPickupStaff:ids.staff,returnStaff:first.body?.staffId,returnStatus:first.status,duplicateStatus:duplicate.status,notSameStaff:first.body?.staffId!==ids.staff};
      await api('customer','cancel conflict return','DELETE',returnUrl);
      await api('customer','restore pickup today','PUT',pickup,schedule(today,'17:00'));
      await api('manager','restore assigned pickup staff','PUT',`/api/manager/contracts/${cash}/pickup-appointment/assignment`,{staffId:ids.staff,reason:'Restore Phase46 fixture'});
      snapshot('after conflict follow-up');
      return;
    }
    const cash=ids.cash,bank=ids.bank,ret=ids.return;
    const payment=(id)=>`/api/contracts/${id}/payments`;
    const pickup=(id)=>`/api/contracts/${id}/pickup-appointment`;
    const schedule=(date,time)=>({appointmentTime:`${date}T${time}:00+07:00`});
    const cashSummary=await api('customer','cash summary','GET',payment(cash)+'/summary');
    if(cashSummary.status!==200) throw new Error('Cash summary unavailable');
    if(!prior?.http.some(x=>x.step==='pickup blocked before deposit'))
      await api('customer','pickup blocked before deposit','POST',pickup(cash),schedule(tomorrow,'17:00'));
    const depAmount=cashSummary.body.deposit.remainingAmount;
    const dep=prior?.http.find(x=>x.step==='cash deposit request') ||
      await api('customer','cash deposit request','POST',payment(cash),{paymentType:'DEPOSIT',amount:depAmount,paymentMethod:'CASH',idempotencyKey:`p46-${run}-deposit`});
    if(dep.status!==201) throw new Error('Cash deposit request failed');
    snapshot('cash deposit pending');
    const confirm=(id,pid)=>`/api/staff/contracts/${id}/payments/${pid}/confirm-cash`;
    for(const role of ['staff','customer','admin','crossManager']) await api(role,`deposit denial ${role}`,'POST',confirm(cash,dep.body.id));
    const managerQueue=await api('manager','manager pending queue','GET','/api/staff/payments/pending');
    const crossQueue=await api('crossManager','cross-facility pending queue','GET','/api/staff/payments/pending');
    await sessions.manager.page.goto(BASE+'/staff-tasks.html',{waitUntil:'networkidle'});
    await sessions.manager.page.screenshot({path:path.join(out,`${run}-manager-deposit.png`),fullPage:true});evidence.screenshots.push(`${run}-manager-deposit.png`);
    await api('manager','deposit confirmation','POST',confirm(cash,dep.body.id));
    await api('manager','deposit confirmation retry','POST',confirm(cash,dep.body.id));
    snapshot('cash deposit confirmed and retried');
    for(const time of ['05:59','06:00','17:00','20:00','22:00','23:00','23:01']) {
      const state=await api('customer',`pickup ${time}`,'GET',pickup(cash));
      const method=state.body?.status==='SCHEDULED'?'PUT':'POST';
      await api('customer',`pickup boundary ${time}`,method,pickup(cash),schedule(tomorrow,time));
    }
    await api('customer','late pickup reschedule','PUT',pickup(cash),schedule(today,'17:00'));
    await api('manager','assign staff','PUT',`/api/manager/contracts/${cash}/pickup-appointment/assignment`,{staffId:ids.staff,reason:'Phase 4.6 live acceptance'});
    snapshot('pickup scheduled once and assigned');
    const returnUrl=`/api/customer/rentals/${ret}/return-schedule`;
    for(const time of ['05:59','06:00','17:00','20:00','22:00','23:00','23:01']) {
      const body={contractId:ret,scheduledAt:`${tomorrow}T${time}:00`,note:`Phase46 ${run}`,checklistEmptyConfirmed:true,checklistAssetsConfirmed:true};
      const result=await api('customer',`return boundary ${time}`,'POST',returnUrl,body);
      if(result.status===200) await api('customer',`cancel return ${time}`,'DELETE',returnUrl);
    }
    snapshot('return boundaries');
    const rentSummary=await api('customer','remaining rent summary','GET',payment(cash)+'/summary');
    const handoverBody={conditionStatus:'GOOD',accessMethod:'KEY',accessIdentifier:'P46-DEMO',customerSignature:'Phase46 test',estimatedRepairCost:0};
    await api('staff','handover blocked before rent','POST',`/api/staff/contracts/${cash}/handover`,handoverBody);
    const rent=await api('customer','cash remaining rent request','POST',payment(cash),{paymentType:'REMAINING_RENT',amount:rentSummary.body.initialRent.remainingAmount,paymentMethod:'CASH',idempotencyKey:`p46-${run}-rent`});
    if(rent.status!==201) throw new Error('Remaining rent request failed');
    snapshot('remaining rent pending');
    await api('otherStaff','unassigned staff rent denial','POST',confirm(cash,rent.body.id));
    await api('manager','manager rent denial','POST',confirm(cash,rent.body.id));
    const staffQueue=await api('staff','assigned staff pending queue','GET','/api/staff/payments/pending');
    const otherQueue=await api('otherStaff','unassigned staff pending queue','GET','/api/staff/payments/pending');
    await sessions.staff.page.goto(BASE+'/staff-tasks.html',{waitUntil:'networkidle'});
    await sessions.staff.page.screenshot({path:path.join(out,`${run}-staff-rent.png`),fullPage:true});evidence.screenshots.push(`${run}-staff-rent.png`);
    await api('staff','remaining rent confirmation','POST',confirm(cash,rent.body.id));
    await api('staff','remaining rent confirmation retry','POST',confirm(cash,rent.body.id));
    await api('staff','handover blocked before scheduled time','POST',`/api/staff/contracts/${cash}/handover`,handoverBody);
    snapshot('both cash invoices paid');
    const bankSummary=await api('customer','bank summary','GET',payment(bank)+'/summary');
    const transfer=await api('customer','bank transfer request','POST',payment(bank),{paymentType:'DEPOSIT',amount:bankSummary.body.deposit.remainingAmount,paymentMethod:'BANK_TRANSFER',idempotencyKey:`p46-${run}-bank`});
    if(transfer.status!==201) throw new Error('Bank request failed');
    await sessions.customer.page.goto(BASE+`/stitch/uc11-customer-payments-appointments.html?contractId=${bank}`,{waitUntil:'networkidle'});
    await sessions.customer.page.locator('.pay-button[data-type="DEPOSIT"]').click();
    await sessions.customer.page.locator('#payment-method').selectOption('BANK_TRANSFER');
    const qr={visible:await sessions.customer.page.locator('#payment-bank-qr').isVisible(),src:await sessions.customer.page.locator('#payment-bank-qr').getAttribute('src')};
    evidence.qr=qr;
    await sessions.customer.page.screenshot({path:path.join(out,`${run}-customer-bank-qr.png`),fullPage:true});evidence.screenshots.push(`${run}-customer-bank-qr.png`);
    snapshot('bank transfer remains pending');
    evidence.queues={managerDeposit:managerQueue.body?.some(p=>p.id===dep.body.id),crossFacilityDeposit:crossQueue.body?.some(p=>p.id===dep.body.id),assignedRent:staffQueue.body?.some(p=>p.id===rent.body.id),unassignedRent:otherQueue.body?.some(p=>p.id===rent.body.id)};
    evidence.sql.push({label:'payment confirmation detail',result:sql(`SET NOCOUNT ON; IF DB_NAME() <> '${DB}' THROW 51000,'Wrong database',1; SELECT payment_type,payment_method,status,amount,confirmed_by,confirmed_at FROM PAYMENT_TRANSACTIONS WHERE contract_id IN ('${cash}','${bank}') ORDER BY created_at; SELECT invoice_type,status,total_amount,paid_amount FROM INVOICES WHERE contract_id IN ('${cash}','${bank}') ORDER BY invoice_type; SELECT COUNT(*) AS activity_logs FROM ACTIVITY_LOGS WHERE entity_id IN ('${cash}','${bank}','${dep.body.id}','${rent.body.id}','${transfer.body.id}');`)});
  } finally {await browser.close();}
}
main().catch(e=>{evidence.defects.push({fatal:e.message,stack:e.stack});process.exitCode=1}).finally(()=>{fs.writeFileSync(path.join(out,`${run}-results.json`),JSON.stringify(evidence,null,2));console.log(JSON.stringify({run,steps:evidence.http.length,defects:evidence.defects,queues:evidence.queues,qr:evidence.qr}));});
