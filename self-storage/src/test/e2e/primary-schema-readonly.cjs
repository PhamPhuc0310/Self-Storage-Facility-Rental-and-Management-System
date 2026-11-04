// Explicitly authorized primary startup verification. No business writes.
const {chromium}=require('playwright');
const fs=require('node:fs'),path=require('node:path'),assert=require('node:assert/strict');
const {execFileSync}=require('node:child_process');
const root=path.resolve(__dirname,'../../../..');
const env=Object.fromEntries(fs.readFileSync(path.join(root,'.env.codex.local'),'utf8').split(/\r?\n/)
  .filter(x=>/^[A-Za-z_][A-Za-z0-9_]*=/.test(x)).map(x=>{const i=x.indexOf('=');return [x.slice(0,i),x.slice(i+1).replace(/^['"]|['"]$/g,'')]}));
const pid=Number(process.env.SAFEBOX_PRIMARY_APP_PID);if(!pid)throw Error('Primary app PID required');
const q=`SET NOCOUNT ON; IF DB_NAME()<>'SelfStorageFacilityDB' OR DB_ID()<>5 THROW 51999,'Wrong primary',1;
IF NOT EXISTS(SELECT 1 FROM sys.dm_exec_sessions WHERE host_process_id=${pid} AND database_id=DB_ID()) THROW 51998,'App not on primary',1;
IF EXISTS(SELECT 1 FROM sys.dm_exec_sessions WHERE host_process_id=${pid} AND database_id<>DB_ID()) THROW 51997,'App connected elsewhere',1; SELECT DB_NAME();`;
assert(execFileSync('sqlcmd',['-S','tcp:127.0.0.1,1433','-E','-C','-d','SelfStorageFacilityDB','-b','-W','-h','-1','-Q',q],{encoding:'utf8'}).includes('SelfStorageFacilityDB'));
const roles=[['customer','customer3@safebox.vn','/stitch/kho_dang_thue_safebox_storage.html','/api/customer/rentals?page=0&size=5'],
 ['staff','staff.q7@safebox.vn','/staff-tasks.html','/api/facility/returns?status=SCHEDULED'],
 ['manager','manager.q7@safebox.vn','/stitch/quan_ly_qua_han_safebox_manager.html','/api/manager/overdue/contracts?page=0&size=5'],
 ['business','business@safebox.vn','/role-home.html',null],['admin','admin@safebox.vn','/role-home.html',null]];
(async()=>{const browser=await chromium.launch({executablePath:'C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe',headless:true});const out={database:'SelfStorageFacilityDB',appPid:pid,roles:{}};
try{for(const [role,email,pagePath,api] of roles){const context=await browser.newContext(),page=await context.newPage(),errors=[];page.on('pageerror',e=>errors.push(e.message));
 await page.goto('http://127.0.0.1:18083/stitch/ng_nh_p_ng_k_safebox_storage.html',{waitUntil:'domcontentloaded'});
 const login=await page.evaluate(async ({email,password})=>{const r=await fetch('/api/auth/login',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({email,password})});const b=await r.json().catch(()=>({}));if(r.ok&&b.token)SafeBoxAuth.saveAuth(b.token,b,false);return r.status},{email,password:env.SAFEBOX_DEMO_PASSWORD});
 const entry={login};out.roles[role]=entry;if(login===200){const response=await page.goto('http://127.0.0.1:18083'+pagePath,{waitUntil:'domcontentloaded'});entry.page=response.status();entry.path=new URL(page.url()).pathname;
 if(api)entry.get=await page.evaluate(async url=>(await SafeBoxAuth.authFetch(url)).status,api);
 if(role==='customer'){
   entry.fixtures=await page.evaluate(async ()=>{const r=await SafeBoxAuth.authFetch('/api/customer/rentals?page=0&size=50');const b=await r.json();return (b.content||[]).filter(x=>x.contractNumber?.startsWith('DEMO-')).map(x=>({code:x.contractNumber,overdue:x.isOverdue,canScheduleReturn:x.canScheduleReturn}))});
   entry.renewal=await page.evaluate(async ()=>{const r=await SafeBoxAuth.authFetch('/api/customer/contracts/00000000-0000-4000-8000-00000000D401/renewals/preview?months=1');const b=await r.json();return {status:r.status,canRenew:b.canRenew}});
   entry.overdue=await page.evaluate(async ()=>{const r=await SafeBoxAuth.authFetch('/api/customer/overdue/contracts/00000000-0000-4000-8000-00000000D402');const b=await r.json();return {status:r.status,isOverdue:b.isOverdue,days:b.overdueDays,graceDays:b.graceDays}});
   entry.returnSchedule=await page.evaluate(async ()=>(await SafeBoxAuth.authFetch('/api/customer/rentals/00000000-0000-4000-8000-00000000D403/return-schedule')).status);
   assert.equal(entry.fixtures.length,3);assert.equal(entry.renewal.status,200);assert.equal(entry.renewal.canRenew,true);assert.equal(entry.overdue.isOverdue,true);assert.equal(entry.returnSchedule,404);
   assert(entry.fixtures.some(x=>x.code==='DEMO-RETURN-01'&&x.canScheduleReturn));
 }
 if(role==='admin'){
   entry.scope=await page.evaluate(async ()=>{const r=await SafeBoxAuth.authFetch('/api/admin/access/users');const users=await r.json();const staff=users.find(x=>x.userId?.toLowerCase()==='384e5607-b0b4-f111-a965-932c2c6addb7');const manager=users.find(x=>x.userId?.toLowerCase()==='3b4e5607-b0b4-f111-a965-932c2c6addb7');return {status:r.status,staffRegionQ7:staff?.regionCodes?.includes('Q7'),staffAtFacility:staff?.facilityIds?.some(x=>x.toLowerCase()==='414e5607-b0b4-f111-a965-932c2c6addb7'),managerAtFacility:manager?.facilityIds?.some(x=>x.toLowerCase()==='414e5607-b0b4-f111-a965-932c2c6addb7')}});
   assert.equal(entry.scope.status,200);assert(entry.scope.staffRegionQ7&&entry.scope.staffAtFacility&&entry.scope.managerAtFacility);
 }
 entry.errors=errors.slice(0,3)} await context.close()}
 console.log(JSON.stringify(out,null,2));}finally{await browser.close()}})().catch(e=>{console.error(e.message);process.exitCode=1});
