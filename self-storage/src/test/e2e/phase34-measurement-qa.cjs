const {chromium}=require('playwright');
const fs=require('node:fs');const path=require('node:path');const assert=require('node:assert/strict');
const base=process.env.SAFEBOX_E2E_BASE_URL,password=process.env.SAFEBOX_E2E_PASSWORD;
if(base!=='http://127.0.0.1:18081'||!password)throw Error('Isolated test URL and password required');
const output=path.resolve(__dirname,'../../../../docs/testing/evidence/phase34-visual');
async function main(){const browser=await chromium.launch({executablePath:'C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe',headless:true});
try{const page=await browser.newPage({viewport:{width:390,height:900}});page.setDefaultTimeout(12000);
await page.goto(base+'/stitch/ng_nh_p_ng_k_safebox_storage.html');await page.locator('#login-email').fill('manager.q7@safebox.vn');
await page.locator('#login-password').fill(password);await page.locator('#btn-login-submit').click();
await page.waitForURL(u=>!u.pathname.endsWith('/ng_nh_p_ng_k_safebox_storage.html'));
await page.goto(base+'/stitch/qu_n_l_lo_i_kho_kho_v_t_l_safebox_manager.html');
await page.locator('#catalog-loading').waitFor({state:'hidden'});
const incomplete=page.locator('.catalog-measure-unit');assert((await incomplete.count())>0);
await incomplete.first().click();await page.locator('#catalog-measure-dialog').waitFor({state:'visible'});
for(const key of ['length','width','height'])assert.equal(await page.locator('#catalog-measure-'+key).inputValue(),'');
assert.equal(await page.locator('#catalog-measure-confirm').isChecked(),false);
const label=await page.locator('#catalog-measure-unit').innerText();
assert.match(await page.locator('#catalog-measure-form').innerText(),/đo trực tiếp/);
const files=[];for(const width of [390,768,1366,1920]){await page.setViewportSize({width,height:900});
const overflow=await page.evaluate(()=>document.documentElement.scrollWidth>innerWidth+2);assert.equal(overflow,false);
const file=`manager-measure-dialog-${width}.png`;await page.screenshot({path:path.join(output,file),fullPage:false});files.push(file);}
console.log(JSON.stringify({incompleteButtons:await incomplete.count(),label,files}));
}finally{await browser.close();}}
main().catch(e=>{console.error(e.stack||String(e));process.exitCode=1;});
