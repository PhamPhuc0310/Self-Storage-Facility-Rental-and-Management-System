const {chromium}=require('playwright');const fs=require('node:fs');const path=require('node:path');const assert=require('node:assert/strict');
const base=process.env.SAFEBOX_E2E_BASE_URL,password=process.env.SAFEBOX_E2E_PASSWORD;
if(base!=='http://127.0.0.1:18081'||!password)throw Error('Isolated app and test password required');
const fid='3f4e5607-b0b4-f111-a965-932c2c6addb7',uid='fa88d008-0594-4ef0-911e-4348a976499c';
async function main(){const browser=await chromium.launch({executablePath:'C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe',headless:true});const out={database:'SelfStorageFacilityDB_Test',checks:{}};try{
 for(const [email,method,url,expected] of [
  ['staff.q7@safebox.vn','POST',`/api/manager/facilities/${fid}/units/${uid}/activate`,403],
  ['admin@safebox.vn','POST',`/api/manager/facilities/${fid}/units/${uid}/activate`,403],
  ['business@safebox.vn','POST',`/api/manager/facilities/${fid}/units/${uid}/activate`,403],
  ['manager.q7@safebox.vn','GET',`/api/manager/facilities/ede766ca-d3d3-460d-b027-1220305f6e98/units/${uid}/activation-check`,403]
 ]){const context=await browser.newContext();const page=await context.newPage();page.setDefaultTimeout(12000);await page.goto(base+'/stitch/ng_nh_p_ng_k_safebox_storage.html');await page.locator('#login-email').fill(email);await page.locator('#login-password').fill(password);await page.locator('#btn-login-submit').click();await page.waitForURL(u=>!u.pathname.endsWith('/ng_nh_p_ng_k_safebox_storage.html'));const result=await page.evaluate(async({url,method})=>{const r=await fetch(url,{method,headers:{Authorization:'Bearer '+SafeBoxAuth.getToken()}});return{status:r.status,body:await r.json().catch(()=>null)}},{url,method});assert.equal(result.status,expected);out.checks[email+' '+method+' '+url]={status:result.status,message:result.body?.message};if(email==='manager.q7@safebox.vn'){await page.goto(base+'/stitch/qu_n_l_lo_i_kho_kho_v_t_l_safebox_manager.html');await page.locator('#catalog-facility').selectOption(fid);const card=page.locator('#catalog-units article').filter({hasText:'PH45-DEMO-01'});await card.waitFor();await card.scrollIntoViewIfNeeded();await page.screenshot({path:path.resolve(__dirname,'../../../../docs/testing/evidence/phase45/04-manager-activated.png'),fullPage:true})}await context.close()}
 }finally{fs.writeFileSync(path.resolve(__dirname,'../../../../docs/testing/evidence/phase45/authorization.json'),JSON.stringify(out,null,2));await browser.close()}}
main().catch(e=>{console.error(e);process.exitCode=1});
