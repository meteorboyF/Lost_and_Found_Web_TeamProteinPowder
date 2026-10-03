/** Add campus reports locally without rewriting existing reports. */
const base = new URL(process.env.SEED_BASE_URL || 'http://localhost:8080');
if (!['localhost', '127.0.0.1', '[::1]'].includes(base.hostname)) {
  throw new Error('Reports may only be added to a local preview server.');
}
const username = process.env.SEED_USERNAME || 'student';
const password = process.env.SEED_PASSWORD || 'student123';
const fixtures = [
  {
    "kind": "LOST",
    "category": "BAGS",
    "title": "Black backpack",
    "description": "Black backpack with a front zip pocket. Last seen on the west side of campus. Please share any sightings near the marked area.",
    "location": "UIU campus — west side",
    "latitude": 23.79791,
    "longitude": 90.44921,
    "searchRadiusMeters": 25
  },
  {
    "kind": "FOUND",
    "category": "KEYS",
    "title": "Keys with a blue keyring",
    "description": "A set of keys with a blue keyring found near the south approach. The owner can identify a concealed detail before collection.",
    "location": "UIU campus — south approach",
    "latitude": 23.7973,
    "longitude": 90.44966,
    "searchRadiusMeters": 25
  },
  {
    "kind": "LOST",
    "category": "BOOKS",
    "title": "Blue notebook",
    "description": "Blue notebook containing handwritten class notes. Last seen in the northern outdoor area. Please check around the reported location.",
    "location": "UIU campus — northern outdoor area",
    "latitude": 23.79864,
    "longitude": 90.44963,
    "searchRadiusMeters": 50
  },
  {
    "kind": "FOUND",
    "category": "ELECTRONICS",
    "title": "Wireless earbuds",
    "description": "Wireless earbuds found on the east side of campus. Identifying details are being kept private to verify the owner.",
    "location": "UIU campus — east side",
    "latitude": 23.79792,
    "longitude": 90.45021,
    "searchRadiusMeters": 25
  },
  {
    "kind": "LOST",
    "category": "OTHER",
    "title": "Steel water bottle",
    "description": "Steel water bottle last seen in the northwest area of campus. Please leave a clue if you have seen it nearby.",
    "location": "UIU campus — northwest area",
    "latitude": 23.7983,
    "longitude": 90.44928,
    "searchRadiusMeters": 50
  },
  {
    "kind": "FOUND",
    "category": "CLOTHING",
    "title": "Grey umbrella",
    "description": "Grey folding umbrella found in the southeast area after rain. The owner can describe its identifying marking before pickup.",
    "location": "UIU campus — southeast area",
    "latitude": 23.7975,
    "longitude": 90.45006,
    "searchRadiusMeters": 25
  }
];
async function json(response) {
  const body = await response.json();
  if (!response.ok) throw new Error(body.message || `Request failed (${response.status})`);
  return body;
}
const login = await fetch(new URL('/api/auth/login', base), {
  method:'POST', headers:{'Content-Type':'application/json'},
  body:JSON.stringify({identifier:username, password}),
});
const user = await json(login);
const cookie = login.headers.getSetCookie().find(value => value.startsWith('JSESSIONID='))?.split(';')[0];
if (!cookie) throw new Error('The preview did not return a login session.');
const existing = [];
for (let page=0; ; page++) {
  const result = await json(await fetch(new URL(`/api/items?size=60&page=${page}`, base)));
  existing.push(...result.content);
  if (result.last) break;
}
for (const fixture of fixtures) {
  const previous = existing.find(item => item.title === fixture.title && item.reporterName === user.username);
  if (previous) { console.log(`Already present: ${previous.reference} ${fixture.title}`); continue; }
  const item = {...fixture,
    reporterName:user.username, reporterEmail:user.email,
    securityQuestion:'What concealed marking identifies this item?', securityAnswer:'blue star',
  };
  const multipart = new FormData();
  multipart.append('item', new Blob([JSON.stringify(item)], {type:'application/json'}));
  const saved = await json(await fetch(new URL('/api/items', base), {
    method:'POST', headers:{Cookie:cookie}, body:multipart,
  }));
  if (saved.latitude !== fixture.latitude || saved.longitude !== fixture.longitude) {
    throw new Error(`Coordinates did not round-trip for ${saved.reference}`);
  }
  console.log(`Added: ${saved.reference} ${saved.title}`);
}
console.log(`Map: ${new URL('/map.html', base)}`);
