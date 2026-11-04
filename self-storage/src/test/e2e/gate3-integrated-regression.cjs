// One bounded synthetic Gate 3 campaign on the disposable Gate 2 restore.
const {chromium}=require('playwright');
const bcrypt=require('bcryptjs');
const {createHash,randomBytes}=require('node:crypto');
const {execFileSync}=require('node:child_process');
const fs=require('node:fs'),os=require('node:os'),path=require('node:path'),assert=require('node:assert/strict');
const db='SafeBox_Gate2_Restore_20261030',base='http://127.0.0.1:18083',pid=Number(process.env.SAFEBOX_GATE3_APP_PID);
if(!pid)throw Error('SAFEBOX_GATE3_APP_PID required');
const run='gate3-20261030-a',facility='3F4E5607-B0B4-F111-A965-932C2C6ADDB7',outside='414E5607-B0B4-F111-A965-932C2C6ADDB7';
const unit='FA88D008-0594-4EF0-911E-4348A976499C',start='2026-11-15',end='2026-12-15';
const out=path.resolve(__dirname,'../../../../docs/testing/evidence/gate3');fs.mkdirSync(out,{recursive:true});
const password=randomBytes(18).toString('base64url'),hash=bcrypt.hashSync(password,10);
const roles=['customerA','customerB','manager','crossManager','staff','business','admin'];
function id(name){const h=createHash('sha256').update(run+':'+name).digest('hex');return `${h.slice(0,8)}-${h.slice(8,12)}-4${h.slice(13,16)}-a${h.slice(17,20)}-${h.slice(20,32)}`}
const ids=Object.fromEntries(roles.map(r=>[r,id(r)]));
const evidence={run,database:db,appPid:pid,unit,period:{start,end},ids,http:[],sql:[],screenshots:[],failures:[]};
function sql(q){const file=path.join(os.tmpdir(),`gate3-${randomBytes(5).toString('hex')}.sql`);fs.writeFileSync(file,q);try{return execFileSync('sqlcmd',['-b','-S','localhost','-E','-C','-d',db,'-l','5','-W','-h','-1','-i',file],{encoding:'utf8',timeout:12000,maxBuffer:2e6}).trim()}catch(e){throw Error(`sqlcmd: ${e.stdout||''} ${e.stderr||''} ${e.message}`)}finally{fs.rmSync(file,{force:true})}}
function identity(){const x=sql(`SET NOCOUNT ON;IF DB_NAME()<>N'${db}' THROW 51900,'Wrong DB',1;IF NOT EXISTS(SELECT 1 FROM sys.dm_exec_sessions WHERE host_process_id=${pid} AND DB_NAME(database_id)=N'${db}') THROW 51901,'App DB mismatch',1;IF EXISTS(SELECT 1 FROM sys.dm_exec_sessions WHERE host_process_id=${pid} AND database_id>0 AND DB_NAME(database_id)<>N'${db}') THROW 51902,'App has other DB sessions',1;SELECT DB_NAME(),DB_ID();`);assert(x.includes(db));return x}
function snapshot(label){evidence.sql.push({label,result:sql(`SET NOCOUNT ON;IF DB_NAME()<>N'${db}' THROW 51900,'Wrong DB',1;
SELECT 'financial', (SELECT COUNT(*) FROM dbo.RENTAL_CONTRACTS),(SELECT COUNT(*) FROM dbo.INVOICES),(SELECT COUNT(*) FROM dbo.PAYMENT_TRANSACTIONS),(SELECT SUM(CAST(total_amount AS decimal(38,2))) FROM dbo.INVOICES),(SELECT SUM(CAST(paid_amount AS decimal(38,2))) FROM dbo.INVOICES),(SELECT SUM(CAST(amount AS decimal(38,2))) FROM dbo.PAYMENT_TRANSACTIONS WHERE status='SUCCESS');
SELECT 'unit',unit_id,status,readiness_verified,dimensions_verified,measurement_source FROM dbo.STORAGE_UNITS WHERE unit_id='${unit}';
SELECT 'newReservations',reservation_id,customer_id,status,facility_id,type_id,start_date,end_date FROM dbo.RESERVATIONS WHERE customer_id IN ('${ids.customerA}','${ids.customerB}');
SELECT 'newContracts',contract_id,reservation_id,unit_id,status FROM dbo.RENTAL_CONTRACTS WHERE customer_id IN ('${ids.customerA}','${ids.customerB}');
SELECT 'newInvoices',invoice_id,contract_id,invoice_type,status,total_amount,paid_amount FROM dbo.INVOICES WHERE contract_id IN (SELECT contract_id FROM dbo.RENTAL_CONTRACTS WHERE customer_id IN ('${ids.customerA}','${ids.customerB}'));`)})}
function fixture(){identity();const rows=roles.map((role)=>{const roleId={customerA:1,customerB:1,manager:3,crossManager:3,staff:2,business:4,admin:5}[role];return `('${ids[role]}',${roleId},'${run}-${role.toLowerCase()}@example.invalid','${hash}','Gate 3 ${role}','0000000000','ACTIVE',SYSDATETIME(),1)`}).join(',\n');
const q=`SET QUOTED_IDENTIFIER ON;SET ANSI_NULLS ON;SET ANSI_PADDING ON;SET ANSI_WARNINGS ON;SET ARITHABORT ON;SET CONCAT_NULL_YIELDS_NULL ON;SET NUMERIC_ROUNDABORT OFF;SET XACT_ABORT ON;SET NOCOUNT ON;
IF DB_NAME()<>N'${db}' THROW 51900,'Wrong DB',1;
IF EXISTS(SELECT 1 FROM dbo.USERS WHERE user_id IN (${roles.map(r=>`'${ids[r]}'`).join(',')})) THROW 51903,'Fixture already exists',1;
IF NOT EXISTS(SELECT 1 FROM dbo.STORAGE_UNITS WHERE unit_id='${unit}' AND status='AVAILABLE' AND readiness_verified=1 AND dimensions_verified=1 AND length_m>0 AND width_m>0 AND height_m>0 AND measurement_source='SYNTHETIC_DEMO') THROW 51904,'Verified demo unit unavailable',1;
IF NOT EXISTS(SELECT 1 FROM dbo.PRICING_POLICIES WHERE facility_id='${facility}' AND type_id=1 AND status='ACTIVE' AND effective_from<='${start}' AND (effective_to IS NULL OR effective_to>='${start}') AND monthly_price>0) THROW 51905,'Effective price absent',1;
IF EXISTS(SELECT 1 FROM dbo.RENTAL_CONTRACTS WHERE unit_id='${unit}' AND status NOT IN ('CANCELLED','TERMINATED') AND start_date<'${end}' AND end_date>'${start}') THROW 51906,'Contract conflict',1;
IF EXISTS(SELECT 1 FROM dbo.RESERVATIONS WHERE facility_id='${facility}' AND type_id=1 AND status='PENDING' AND start_date<'${end}' AND end_date>'${start}' AND (hold_expires_at IS NULL OR hold_expires_at>SYSDATETIME())) THROW 51907,'Existing hold',1;
BEGIN TRAN;
INSERT dbo.USERS(user_id,role_id,email,password_hash,full_name,phone,status,created_at,email_verified) VALUES ${rows};
INSERT dbo.USER_FACILITY_ASSIGNMENTS(assignment_id,user_id,facility_id,assigned_by,start_date,status) VALUES
('${id('manager-scope')}','${ids.manager}','${facility}','${ids.admin}','2026-01-01','ACTIVE'),
('${id('cross-scope')}','${ids.crossManager}','${outside}','${ids.admin}','2026-01-01','ACTIVE'),
('${id('staff-scope')}','${ids.staff}','${facility}','${ids.admin}','2026-01-01','ACTIVE');
COMMIT;SELECT 'FIXTURE_CREATED';`;
assert(sql(q).includes('FIXTURE_CREATED'));evidence.sql.push({label:'fixture',result:'Seven isolated synthetic role users and three scope assignments created; no unit measurement or financial row changed.'})}
async function main(){identity();fixture();snapshot('before concurrent requests');const browser=await chromium.launch({executablePath:'C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe',headless:true});const sessions={};try{
 for(const role of roles){const context=await browser.newContext({viewport:{width:1366,height:900}}),page=await context.newPage();page.setDefaultTimeout(12000);await page.goto(base+'/stitch/ng_nh_p_ng_k_safebox_storage.html',{waitUntil:'domcontentloaded',timeout:12000});const r=await page.evaluate(async x=>{const c=new AbortController(),t=setTimeout(()=>c.abort(),10000);try{const v=await fetch('/api/auth/login',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify(x),signal:c.signal});return{status:v.status,body:await v.json().catch(()=>null)}}finally{clearTimeout(t)}},{email:`${run}-${role.toLowerCase()}@example.invalid`,password});evidence.http.push({step:`login ${role}`,status:r.status});assert.equal(r.status,200,`login ${role}`);await page.evaluate(({token,user})=>SafeBoxAuth.saveAuth(token,user,false),r.body);sessions[role]={context,page}}
 async function api(role,step,method,url,body,at){if(method!=='GET')identity();const r=await sessions[role].page.evaluate(async({method,url,body,at})=>{if(at){const wait=at-Date.now();if(wait>0)await new Promise(done=>setTimeout(done,wait))}const c=new AbortController(),t=setTimeout(()=>c.abort(),12000);try{const token=SafeBoxAuth.getToken();const v=await fetch(url,{method,headers:{Authorization:`Bearer ${token}`,...(body?{'Content-Type':'application/json'}:{})},body:body?JSON.stringify(body):undefined,signal:c.signal});const raw=await v.text();let data;try{data=JSON.parse(raw)}catch{data=raw.slice(0,250)}return{status:v.status,body:data}}finally{clearTimeout(t)}},{method,url,body,at});evidence.http.push({role,step,method,url,status:r.status,body:r.body});return r}
 const discovery=await api('customerA','verified demo discovery','GET',`/api/discovery/units?facilityId=${facility}&typeId=1&startDate=${start}&months=1`);assert.equal(discovery.status,200);assert(discovery.body.some(x=>x.unitId.toLowerCase()===unit.toLowerCase()&&x.bookable));
 const legacy=await api('customerA','legacy availability','GET',`/api/availability?facilityId=${facility}&typeId=3&startDate=${start}&endDate=${end}`);assert.equal(legacy.status,200);assert.equal(legacy.body.available,false);
 const body={facilityId:facility,typeId:1,startDate:start,months:1};const at=Date.now()+400;
 const [a,b]=await Promise.all([api('customerA','concurrent reservation A','POST','/api/reservations',body,at),api('customerB','concurrent reservation B','POST','/api/reservations',body,at)]);
 const winners=[a,b].filter(x=>x.status===201);assert.equal(winners.length,1,'exactly one successful hold');const losingResponse=a.status===201?b:a;assert(losingResponse.status>=400&&losingResponse.status<500,'loser must receive a controlled client conflict');const loser=a.status===201?'customerB':'customerA',winner=a.status===201?'customerA':'customerB';const reservationId=winners[0].body.reservationId;evidence.concurrent={winner,loser,statuses:[a.status,b.status],reservationId};snapshot('after concurrent requests');
 const legacyPost=await api(loser,'direct legacy unverified reservation','POST','/api/reservations',{facilityId:facility,typeId:3,startDate:start,months:1});evidence.legacyDirectStatus=legacyPost.status;assert(legacyPost.status>=400&&legacyPost.status<500);
 const approveUrl=`/api/manager/reservations/${reservationId}/confirm`;
 for(const role of ['crossManager','staff','business','admin',winner]){const r=await api(role,`${role} approval denied`,'POST',approveUrl,{});assert.equal(r.status,403,`${role} approval`)}
 const approved=await api('manager','assigned Manager approval','POST',approveUrl,{});assert.equal(approved.status,200);evidence.contractId=approved.body.contractId;snapshot('after manager approval');
 const repeat=await api('manager','approval retry','POST',approveUrl,{});assert.equal(repeat.status,200);assert.equal(repeat.body.contractId,evidence.contractId);snapshot('after approval retry');
 const pages={customerA:'/stitch/y_u_c_u_c_a_t_i_safebox_storage.html',manager:'/stitch/x_l_y_u_c_u_t_kho_safebox_manager.html',staff:'/staff-tasks.html',business:'/reports.html',admin:'/management-admin.html'};
 for(const [role,url] of Object.entries(pages)){const page=sessions[role].page;const nav=await page.goto(base+url,{waitUntil:'domcontentloaded',timeout:12000});await page.waitForTimeout(800);evidence.http.push({role,step:'role page',url,status:nav.status()});const file=`${run}-${role}.png`;await page.screenshot({path:path.join(out,file),fullPage:false,timeout:12000});evidence.screenshots.push(file)}
 const summary=await api('business','business financial summary','GET',`/api/reports/financial-summary?facilityId=${facility}&startDate=2026-10-01&endDate=2026-10-30`);evidence.businessReportStatus=summary.status;
 const denied=await api('customerA','customer financial summary denied','GET',`/api/reports/financial-summary?facilityId=${facility}&startDate=2026-10-01&endDate=2026-10-30`);evidence.customerReportStatus=denied.status;
 }finally{for(const s of Object.values(sessions))await s.context.close().catch(()=>{});await browser.close()}}
main().catch(e=>{evidence.failures.push({message:e.message,stack:e.stack});process.exitCode=1}).finally(()=>{fs.writeFileSync(path.join(out,`${run}-results.json`),JSON.stringify(evidence,null,2));console.log(JSON.stringify({run,concurrent:evidence.concurrent,steps:evidence.http.length,failures:evidence.failures.map(x=>x.message)}))});
