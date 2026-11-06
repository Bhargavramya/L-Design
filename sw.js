// Decor 3D offline support. VERSION changes with every release: the app then shows "New version ready – Reload". Designs are kept.
const VERSION='1.2';
const CACHE='decor3d-'+VERSION;
const LOCAL=['./','./index.html','./manifest.webmanifest','./icon-180.png','./icon-192.png','./icon-512.png','./icon-1024.png','./icon-maskable-512.png'];
const CDN=[
 "https://cdn.jsdelivr.net/npm/three@0.147.0/build/three.min.js",
 "https://cdn.jsdelivr.net/npm/three@0.147.0/examples/js/controls/OrbitControls.js",
 "https://cdn.jsdelivr.net/npm/three@0.147.0/examples/js/controls/TransformControls.js",
 "https://cdn.jsdelivr.net/npm/three@0.147.0/examples/js/exporters/ColladaExporter.js",
 "https://cdn.jsdelivr.net/npm/three@0.147.0/examples/js/loaders/ColladaLoader.js",
 "https://cdnjs.cloudflare.com/ajax/libs/jspdf/2.5.1/jspdf.umd.min.js",
 "https://cdn.jsdelivr.net/npm/three@0.147.0/examples/js/shaders/CopyShader.js",
 "https://cdn.jsdelivr.net/npm/three@0.147.0/examples/js/shaders/LuminosityHighPassShader.js",
 "https://cdn.jsdelivr.net/npm/three@0.147.0/examples/js/shaders/GammaCorrectionShader.js",
 "https://cdn.jsdelivr.net/npm/three@0.147.0/examples/js/postprocessing/EffectComposer.js",
 "https://cdn.jsdelivr.net/npm/three@0.147.0/examples/js/postprocessing/RenderPass.js",
 "https://cdn.jsdelivr.net/npm/three@0.147.0/examples/js/postprocessing/ShaderPass.js",
 "https://cdn.jsdelivr.net/npm/three@0.147.0/examples/js/postprocessing/UnrealBloomPass.js"
];
const OK_HOSTS=['cdn.jsdelivr.net','cdnjs.cloudflare.com','fonts.googleapis.com','fonts.gstatic.com'];
self.addEventListener('install',e=>{ e.waitUntil(caches.open(CACHE).then(async c=>{ await c.addAll(LOCAL); await Promise.all(CDN.map(u=>c.add(u).catch(()=>{}))); })); });
self.addEventListener('message',e=>{ if(e.data==='skip') self.skipWaiting(); });
self.addEventListener('activate',e=>{ e.waitUntil(caches.keys().then(keys=>Promise.all(keys.filter(k=>k.startsWith('decor3d-')&&k!==CACHE).map(k=>caches.delete(k)))).then(()=>self.clients.claim())); });
self.addEventListener('fetch',e=>{ const req=e.request; if(req.method!=='GET') return; const url=new URL(req.url);
  if(url.origin!==location.origin&&!OK_HOSTS.includes(url.hostname)) return;
  e.respondWith(caches.open(CACHE).then(cache=>cache.match(req,{ignoreSearch:url.origin===location.origin}).then(hit=>{
    const net=fetch(req).then(res=>{ if(res&&(res.ok||res.type==='opaque')) cache.put(req,res.clone()); return res; }).catch(()=>hit);
    return hit||net; }))); });
