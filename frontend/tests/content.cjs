/** Read-only checks against the main local application. No reports or claims are created. */
const { chromium } = require('playwright');
const assert = require('node:assert/strict');
const baseURL = process.env.CONTENT_BASE_URL || 'http://localhost:8080';
if (!['localhost','127.0.0.1','[::1]'].includes(new URL(baseURL).hostname)) {
  throw new Error('Content checks are restricted to local servers.');
}
const presentationCopy = /\b(faculty|demo|demonstration|prototype|fictional|simulated)\b|example spot/i;
(async () => {
  const browser = await chromium.launch({headless:true,
    ...(process.env.PLAYWRIGHT_CHROMIUM_EXECUTABLE ? {executablePath:process.env.PLAYWRIGHT_CHROMIUM_EXECUTABLE} : {})});
  try {
    const context = await browser.newContext({baseURL});
    const page = await context.newPage(), errors = [];
    page.on('pageerror', error => errors.push(error.message));
    // Tile availability is unrelated to interface copy and layout.
    await page.route('https://tile.openstreetmap.org/**', route => route.abort());
    for (const width of [1360,390]) {
      await page.setViewportSize({width,height:1000});
      for (const route of ['/','/browse.html','/login.html','/map.html','/help.html','/desk.html']) {
        const response = await page.goto(route);
        assert.equal(response.status(),200,route);
        if (route === '/browse.html') await page.locator('[data-results] a[href^="/item.html"]').first().waitFor();
        if (route === '/map.html') await page.locator('[data-map-list] [data-map-report]').first().waitFor();
        const visible = await page.locator('body').innerText();
        assert.doesNotMatch(visible,presentationCopy,route);
        assert.doesNotMatch(visible,/admin123|student123|campus123|DP 4821|blue star/i,'No published credentials or security answers');
        assert.equal(await page.evaluate(() => document.documentElement.scrollWidth > innerWidth),false,'No horizontal overflow: '+route);
      }
    }
    await page.goto('/prototype.html');
    await page.waitForURL('**/help.html');
    assert.equal(await page.getByRole('heading',{name:'Claim with confidence. Collect safely.'}).count(),1);
    const items = await (await context.request.get('/api/items?size=60')).json();
    for (const item of items.content) {
      assert.doesNotMatch([item.title,item.description,item.location,item.reporterName,item.securityQuestion].join(' '),presentationCopy,item.reference);
    }
    assert.deepEqual(errors,[],'No browser script errors');
    await context.close();
    console.log('Content checks passed: public wording, private credentials, guide redirects, desktop and mobile layouts.');
  } finally { await browser.close(); }
})().catch(error => { console.error(error); process.exitCode=1; });
