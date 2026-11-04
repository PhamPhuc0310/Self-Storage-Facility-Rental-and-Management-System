const {chromium}=require('playwright');
const fs=require('node:fs');
const path=require('node:path');
const assert=require('node:assert/strict');
const password=process.env.SAFEBOX_E2E_PASSWORD;
if(!password)throw Error('SAFEBOX_E2E_PASSWORD required');
const base='http://127.0.0.1:18080';
const facility='3f4e5607-b0b4-f111-a965-932c2c6addb7';
const outside='414e5607-b0b4-f111-a965-932c2c6addb7';
const contract='39899ab6-bcc1-4434-af7d-9a99e886b5da';
const syntheticUser='00000000-0000-4000-8000-00000000b402';
const priceDate=new Date(Date.now()+86400000).toISOString().slice(0,10);
const rows=[];
const cases=[
 ['customer','GET','/api/contracts/my',200],['customer','GET','/api/staff/tasks',403],
 ['customer','POST',`/api/manager/reservations/89fc69ae-9d24-4fa6-bc6a-69a68bdd65c3/confirm`,403,{}],
 ['staff','GET','/api/staff/tasks',200],['staff','POST',`/api/staff/contracts/${contract}/payments/0c227397-6192-4f8e-b852-5290b260b21a/confirm-cash`,200],
 ['staff','POST',`/api/business/facilities/${facility}/prices`,403,{}],
 ['manager','GET','/api/manager/facilities',200],['manager','GET',`/api/manager/facilities/${outside}/units`,200],
 ['manager','POST',`/api/business/facilities/${facility}/prices`,403,{}],
 ['crossManager','GET',`/api/manager/facilities/${facility}/units`,403],
 ['business','GET','/api/business/facilities',200],['business','POST',`/api/admin/access/managers/3b4e5607-b0b4-f111-a965-932c2c6addb7/facilities/${facility}`,403,{}],
 ['business','POST',`/api/manager/reservations/89fc69ae-9d24-4fa6-bc6a-69a68bdd65c3/confirm`,403,{}],
 ['admin','GET','/api/admin/access/users',200],['admin','POST',`/api/business/facilities/${facility}/prices`,403,{}],
 ['admin','POST',`/api/manager/reservations/89fc69ae-9d24-4fa6-bc6a-69a68bdd65c3/confirm`,403,{}],
 ['business','POST',`/api/business/facilities/${facility}/prices`,200,{typeId:1,monthlyPrice:910000,effectiveFrom:priceDate,effectiveTo:priceDate}],
 ['admin','PUT',`/api/admin/access/users/${syntheticUser}/status`,200,{status:'INACTIVE'}],
 ['admin','PUT',`/api/admin/access/users/${syntheticUser}/status`,200,{status:'ACTIVE'}]
];
async function main(){const browser=await chromium.launch({executablePath:'C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe',headless:true});const pages={};try{
 for(const [role,email] of Object.entries({customer:'customer3@safebox.vn',staff:'staff.q7@safebox.vn',manager:'manager.q7@safebox.vn',crossManager:'manager.thuduc@safebox.vn',business:'business@safebox.vn',admin:'admin@safebox.vn'})){
  const context=await browser.newContext(),page=await context.newPage();await page.goto(base+'/stitch/ng_nh_p_ng_k_safebox_storage.html');
  const login=await page.evaluate(async({email,password})=>{const r=await fetch('/api/auth/login',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({email,password})});return{status:r.status,body:await r.json()}},{email,password});assert.equal(login.status,200,role);await page.evaluate(x=>SafeBoxAuth.saveAuth(x.token,x.user,false),login.body);pages[role]={context,page};
 }
 for(const [role,method,url,expected,body] of cases){const response=await pages[role].page.evaluate(async({method,url,body})=>{const r=await fetch(url,{method,headers:{Authorization:`Bearer ${SafeBoxAuth.getToken()}`,...(body?{'Content-Type':'application/json'}:{})},body:body?JSON.stringify(body):undefined});return{status:r.status,body:(await r.text()).slice(0,250)}},{method,url,body});rows.push({role,method,url,expected,actual:response.status,detail:response.status===expected?'':response.body});}
}finally{for(const x of Object.values(pages))await x.context.close();await browser.close();fs.writeFileSync(path.resolve(__dirname,'../../../../docs/testing/evidence/recovery-phase3-rbac.json'),JSON.stringify(rows,null,2))}
console.log(JSON.stringify({checks:rows.length,passed:rows.filter(x=>x.expected===x.actual).length,failed:rows.filter(x=>x.expected!==x.actual)}));if(rows.some(x=>x.expected!==x.actual))process.exitCode=1}
main().catch(e=>{console.error(e.stack||String(e));process.exitCode=1});
