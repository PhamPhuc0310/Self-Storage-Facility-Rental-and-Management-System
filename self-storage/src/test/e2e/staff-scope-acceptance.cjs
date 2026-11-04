const {chromium}=require('playwright');
const assert=require('node:assert/strict');
const fs=require('node:fs');
const path=require('node:path');
const base=process.env.SAFEBOX_E2E_BASE_URL||'http://localhost:18080';
const password=process.env.SAFEBOX_E2E_PASSWORD;
if(!password)throw new Error('SAFEBOX_E2E_PASSWORD is required');
const staffId='394e5607-b0b4-f111-a965-932c2c6addb7';
const facilityId='404e5607-b0b4-f111-a965-932c2c6addb7';
const evidence=path.resolve(__dirname,'../../../../docs/testing/evidence');
const result={staffId,facilityId,events:[]};
async function login(browser,email){
  const context=await browser.newContext({viewport:{width:1440,height:900}});
  const page=await context.newPage();
  page.on('response',r=>{if(r.url().startsWith(base+'/api/'))result.events.push({actor:email,path:new URL(r.url()).pathname,method:r.request().method(),status:r.status()});});
  await page.goto(base+'/stitch/ng_nh_p_ng_k_safebox_storage.html',{waitUntil:'domcontentloaded'});
  await page.locator('#login-email').fill(email);await page.locator('#login-password').fill(password);await page.locator('#btn-login-submit').click();
  await page.waitForURL(url=>!url.pathname.endsWith('/ng_nh_p_ng_k_safebox_storage.html'));
  return {context,page};
}
async function main(){
  const browser=await chromium.launch({executablePath:'C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe',headless:true});
  try{
    const admin=await login(browser,'admin@safebox.vn');
    await admin.page.goto(base+'/stitch/quan_ly_pham_vi_nhan_vien.html',{waitUntil:'domcontentloaded'});
    const add=admin.page.locator(`[data-add="${staffId}"]`);
    await add.waitFor();
    admin.page.once('dialog',d=>d.accept(facilityId));
    const grant=admin.page.waitForResponse(r=>new URL(r.url()).pathname===`/api/admin/staff/${staffId}/facilities`&&r.request().method()==='POST');
    await add.click();result.grantStatus=(await grant).status();assert.equal(result.grantStatus,200);
    const remove=admin.page.locator(`[data-remove="${staffId}|${facilityId}"]`);
    await remove.waitFor();
    await admin.page.screenshot({path:path.join(evidence,'staff-admin-granted.png'),fullPage:true});
    const staff=await login(browser,'staff.q8@safebox.vn');
    await staff.page.goto(base+'/staff-tasks.html',{waitUntil:'domcontentloaded'});
    await staff.page.locator('#task-facilities').getByText('Phạm Thế Hiển',{exact:false}).waitFor();
    await staff.page.screenshot({path:path.join(evidence,'staff-q8-scope-granted.png'),fullPage:true});
    admin.page.once('dialog',d=>d.accept());
    const revoke=admin.page.waitForResponse(r=>new URL(r.url()).pathname===`/api/admin/staff/${staffId}/facilities/${facilityId}`&&r.request().method()==='DELETE');
    await remove.click();result.revokeStatus=(await revoke).status();assert.equal(result.revokeStatus,200);
    await admin.page.screenshot({path:path.join(evidence,'staff-admin-revoked.png'),fullPage:true});
    await staff.page.reload({waitUntil:'domcontentloaded'});
    await staff.page.locator('#task-facilities').getByText('chưa được phân công',{exact:false}).waitFor();
    await staff.page.screenshot({path:path.join(evidence,'staff-q8-scope-revoked.png'),fullPage:true});
    await staff.context.close();await admin.context.close();
  }finally{fs.writeFileSync(path.join(evidence,'staff-scope-results.json'),JSON.stringify(result,null,2));await browser.close();}
  console.log(JSON.stringify({staffId,facilityId,grantStatus:result.grantStatus,revokeStatus:result.revokeStatus}));
}
main().catch(e=>{console.error(e.stack||String(e));process.exitCode=1;});
