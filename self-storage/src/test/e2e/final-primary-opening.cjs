// Authenticated browser read-only opening-state check on the existing rehearsal DB.
const { chromium } = require('playwright');
const fs = require('node:fs');
const path = require('node:path');
const { execFileSync } = require('node:child_process');
const assert = require('node:assert/strict');

const root = path.resolve(__dirname, '../../../..');
const envPath = path.join(root, '.env.codex.local');
const local = Object.fromEntries(fs.readFileSync(envPath, 'utf8').split(/\r?\n/)
  .filter(line => /^[A-Za-z_][A-Za-z0-9_]*=/.test(line))
  .map(line => { const pos = line.indexOf('='); return [line.slice(0, pos), line.slice(pos + 1).replace(/^['"]|['"]$/g, '')]; }));
const password = local.SAFEBOX_DEMO_PASSWORD;
if (!password) throw new Error('Local demo password is unavailable');
const db = 'SafeBox_FinalRehearsal_20261030';
const pid = Number(process.env.SAFEBOX_REHEARSAL_APP_PID);
if (!pid) throw new Error('SAFEBOX_REHEARSAL_APP_PID required');
const base = 'http://127.0.0.1:18083';
const contracts = {
  renewal: '00000000-0000-4000-8000-00000000D401',
  overdue: '00000000-0000-4000-8000-00000000D402',
  returning: '00000000-0000-4000-8000-00000000D403'
};
function identity() {
  const q = `SET NOCOUNT ON; IF DB_NAME()<>'${db}' THROW 51970,'Wrong DB',1;
    IF NOT EXISTS(SELECT 1 FROM sys.dm_exec_sessions WHERE host_process_id=${pid} AND database_id=DB_ID('${db}')) THROW 51971,'No rehearsal app session',1;
    IF EXISTS(SELECT 1 FROM sys.dm_exec_sessions WHERE host_process_id=${pid} AND database_id<>DB_ID('${db}')) THROW 51972,'App has other DB session',1;
    SELECT DB_NAME();`;
  assert(execFileSync('sqlcmd', ['-S','tcp:127.0.0.1,1433','-E','-C','-d',db,'-b','-W','-h','-1','-Q',q],
    {encoding:'utf8',timeout:15000}).includes(db));
}
const roles = [
  ['customer','customer3@safebox.vn','/stitch/kho_dang_thue_safebox_storage.html'],
  ['staff','staff.q7@safebox.vn','/staff-tasks.html'],
  ['manager','manager.q7@safebox.vn','/stitch/quan_ly_qua_han_safebox_manager.html'],
  ['business','business@safebox.vn','/role-home.html'],
  ['admin','admin@safebox.vn','/role-home.html']
];

(async () => {
  identity();
  const browser = await chromium.launch({executablePath:'C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe',headless:true});
  const out = {database:db,appPid:pid,roles:{},opening:{},pageErrors:{}};
  const sessions = {};
  try {
    for (const [role,email,pagePath] of roles) {
      const context = await browser.newContext({viewport:{width:1366,height:900}});
      const page = await context.newPage();
      const pageErrors = [];
      page.on('pageerror', e => pageErrors.push(e.message));
      await page.goto(base + '/stitch/ng_nh_p_ng_k_safebox_storage.html',{waitUntil:'domcontentloaded'});
      const login = await page.evaluate(async values => {
        const r=await fetch('/api/auth/login',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify(values)});
        const body=await r.json().catch(()=>({}));
        if (r.ok && body.token) SafeBoxAuth.saveAuth(body.token,body,false);
        return {status:r.status,hasToken:!!body.token};
      },{email,password});
      out.roles[role]={login:login.status,tokenIssued:login.hasToken};
      if (login.status!==200 || !login.hasToken) { await context.close(); continue; }
      await page.goto(base+pagePath,{waitUntil:'domcontentloaded'});
      out.roles[role].pageStatus=(await page.locator('body').count())?200:0;
      out.roles[role].path=new URL(page.url()).pathname;
      out.pageErrors[role]=pageErrors.slice(0,3);
      sessions[role]={context,page};
    }
    const customer=sessions.customer?.page;
    if (customer) {
      identity();
      out.opening.customerContracts=await customer.evaluate(async ()=>{
        const r=await SafeBoxAuth.authFetch('/api/customer/rentals?page=0&size=50');const b=await r.json();
        return {status:r.status,contracts:(b.content||[]).filter(x=>x.contractNumber?.startsWith('DEMO-')).map(x=>({number:x.contractNumber,isOverdue:x.isOverdue}))};
      });
      for(const [key,url] of Object.entries({
        renewal:`/api/customer/contracts/${contracts.renewal}/renewals/preview?months=1`,
        overdue:`/api/customer/overdue/contracts/${contracts.overdue}`,
        returnSchedule:`/api/customer/rentals/${contracts.returning}/return-schedule`
      })) {
        identity();
        out.opening[key]=await customer.evaluate(async url=>{
          const r=await SafeBoxAuth.authFetch(url);const body=await r.json().catch(()=>({}));
          return {status:r.status,canRenew:body.canRenew??null,contractNumber:body.contractNumber??null,
            endDate:body.endDate??null,overdueDays:body.overdueDays??null,isOverdue:body.isOverdue??null,
            graceDays:body.graceDays??null,billableDays:body.billableDays??null,message:body.message??null};
        },url);
      }
    }
    if(sessions.manager){
      identity();
      out.opening.noPolicyFee=await sessions.manager.page.evaluate(async id=>{
        const r=await SafeBoxAuth.authFetch(`/api/manager/overdue/contracts/${id}/calculate-fee`,{method:'POST'});
        return {status:r.status};
      },contracts.overdue);
    }
    if (process.env.SAFEBOX_LATE_RETURN === 'YES') {
      identity();
      const scheduled=await customer.evaluate(async id=>{
        const r=await SafeBoxAuth.authFetch(`/api/customer/rentals/${id}/return-schedule`,{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({contractId:id,scheduledAt:'2026-10-31T10:00:00',checklistEmptyConfirmed:true,checklistAssetsConfirmed:true})});
        return {status:r.status,body:await r.json()};
      },contracts.overdue);
      assert.equal(scheduled.status,200);assert.equal(scheduled.body.staffId,null);
      identity();
      const assigned=await sessions.manager.page.evaluate(async id=>{
        const r=await SafeBoxAuth.authFetch(`/api/manager/returns/${id}/assignment`,{method:'PUT',headers:{'Content-Type':'application/json'},body:JSON.stringify({staffId:'384E5607-B0B4-F111-A965-932C2C6ADDB7'})});
        return {status:r.status,body:await r.json()};
      },scheduled.body.handoverId);
      assert.equal(assigned.status,200);
      identity();
      const visible=await sessions.staff.page.evaluate(async id=>{
        const r=await SafeBoxAuth.authFetch('/api/facility/returns?status=SCHEDULED');return (await r.json()).some(x=>x.handoverId===id);
      },scheduled.body.handoverId);
      assert(visible,'Assigned Staff cannot see late return');
      identity();
      const cancelled=await customer.evaluate(async id=>{
        const r=await SafeBoxAuth.authFetch(`/api/customer/rentals/${id}/return-schedule`,{method:'DELETE'});return r.status;
      },contracts.overdue);
      assert.equal(cancelled,200);
      out.lateReturn={scheduled:scheduled.status,initiallyUnassigned:true,assignment:assigned.status,staffVisible:visible,cancelled};
    }
    if (process.env.SAFEBOX_ESCALATION === 'YES') {
      identity();
      const result=await sessions.manager.page.evaluate(async id=>{
        const r=await SafeBoxAuth.authFetch(`/api/manager/overdue/contracts/${id}`);const b=await r.json();
        const deny=await SafeBoxAuth.authFetch(`/api/manager/overdue/contracts/${id}/recovery-confirmation`,{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({possessionConfirmed:false,emptyConfirmed:false})});
        return {status:r.status,days:b.overdueDays,requiresRecovery:b.requiresRecovery,incompleteConfirmation:deny.status};
      },contracts.overdue);
      assert.equal(result.status,200);assert.equal(result.requiresRecovery,true);assert.equal(result.incompleteConfirmation,400);
      out.escalation=result;
    }
    if (process.env.SAFEBOX_NORMAL_RETURN === 'YES') {
      identity();
      const detail=await customer.evaluate(async id=>{
        const r=await SafeBoxAuth.authFetch(`/api/customer/rentals/${id}`);return {status:r.status,body:await r.json()};
      },contracts.returning);
      assert.equal(detail.status,200,`Return detail: ${JSON.stringify(detail.body)}`);
      const scheduled=await customer.evaluate(async ({id,endDate})=>{
        const r=await SafeBoxAuth.authFetch(`/api/customer/rentals/${id}/return-schedule`,{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({contractId:id,scheduledAt:`${endDate}T10:00:00`,checklistEmptyConfirmed:true,checklistAssetsConfirmed:true})});
        return {status:r.status,body:await r.json()};
      },{id:contracts.returning,endDate:detail.body.contract.endDate});
      assert.equal(scheduled.status,200,`Schedule: ${JSON.stringify(scheduled.body)}`);assert.equal(scheduled.body.staffId,null);
      identity();
      const options=await sessions.manager.page.evaluate(async id=>{const r=await SafeBoxAuth.authFetch(`/api/manager/returns/${id}/eligible-staff`);return {status:r.status,body:await r.json()}},scheduled.body.handoverId);
      assert.equal(options.status,200,`Eligible staff: ${JSON.stringify(options.body)}`);assert(options.body.some(x=>x.staffId?.toLowerCase()==='384e5607-b0b4-f111-a965-932c2c6addb7'));
      const assigned=await sessions.manager.page.evaluate(async id=>{const r=await SafeBoxAuth.authFetch(`/api/manager/returns/${id}/assignment`,{method:'PUT',headers:{'Content-Type':'application/json'},body:JSON.stringify({staffId:'384E5607-B0B4-F111-A965-932C2C6ADDB7'})});return {status:r.status,body:await r.json()}},scheduled.body.handoverId);
      assert.equal(assigned.status,200,`Assignment: ${JSON.stringify(assigned.body)}`);
      const visible=await sessions.staff.page.evaluate(async id=>{const r=await SafeBoxAuth.authFetch('/api/facility/returns?status=SCHEDULED');return (await r.json()).some(x=>x.handoverId===id)},scheduled.body.handoverId);
      assert(visible,'Assigned Staff cannot see return');
      identity();
      const cancel=await customer.evaluate(async id=>(await SafeBoxAuth.authFetch(`/api/customer/rentals/${id}/return-schedule`,{method:'DELETE'})).status,contracts.returning);
      assert.equal(cancel,200);out.normalReturn={scheduled:scheduled.status,initiallyUnassigned:true,eligible:options.status,assigned:assigned.status,staffVisible:visible,cancelled:cancel};
    }
    console.log(JSON.stringify(out,null,2));
  } finally { await Promise.all(Object.values(sessions).map(s=>s.context.close())); await browser.close(); }
})().catch(e=>{console.error(e.message);process.exitCode=1});
