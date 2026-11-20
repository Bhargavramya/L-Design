// Decor 3D desktop app (Windows + Mac).
// It opens the live Decor 3D site, so every site update reaches the app automatically.
// Designs are kept on this computer (and keep working offline after the first open).
const { app, BrowserWindow, shell, Menu, session } = require('electron');
const path = require('path');

const HOME = 'https://bhargavramya.github.io/L-Design/';
const ORIGIN = new URL(HOME).origin;
const isMac = process.platform === 'darwin';

let win = null;

function inApp(url) {
  try { const u = new URL(url); return u.origin === ORIGIN && u.pathname.startsWith('/L-Design'); } catch (e) { return false; }
}

function openOutside(url) {
  if (/^(https?|mailto|tel|whatsapp):/i.test(url)) shell.openExternal(url);
}

function createWindow() {
  win = new BrowserWindow({
    width: 1440, height: 900, minWidth: 900, minHeight: 600,
    backgroundColor: '#1e1f24',
    title: 'Decor 3D',
    icon: path.join(__dirname, 'build', 'icon.png'),
    autoHideMenuBar: true,
    show: false,
    webPreferences: { contextIsolation: true, nodeIntegration: false, spellcheck: false }
  });
  win.webContents.setUserAgent(win.webContents.getUserAgent() + ' Decor3DApp/desktop');
  win.once('ready-to-show', () => { win.maximize(); win.show(); });

  // links to other sites open in the normal browser
  win.webContents.setWindowOpenHandler(({ url }) => {
    if (url.startsWith('blob:') || url.startsWith('data:')) return { action: 'allow' };
    if (inApp(url)) { win.loadURL(url); return { action: 'deny' }; }
    openOutside(url); return { action: 'deny' };
  });
  win.webContents.on('will-navigate', (e, url) => {
    if (inApp(url) || url.startsWith('file:')) return;
    e.preventDefault(); openOutside(url);
  });

  // no internet on the very first open: show a friendly page with Try again
  win.webContents.on('did-fail-load', (e, code, desc, url, isMain) => {
    if (isMain && code !== -3) win.loadFile(path.join(__dirname, 'offline.html'));
  });

  win.loadURL(HOME);
}

if (!app.requestSingleInstanceLock()) {
  app.quit();
} else {
  app.on('second-instance', () => { if (win) { if (win.isMinimized()) win.restore(); win.focus(); } });

  app.whenReady().then(() => {
    // allow the page to use the clipboard for copy/paste of items
    session.defaultSession.setPermissionRequestHandler((wc, perm, cb) => cb(['clipboard-read', 'clipboard-sanitized-write', 'fullscreen', 'notifications'].includes(perm)));
    if (isMac) {
      Menu.setApplicationMenu(Menu.buildFromTemplate([
        { role: 'appMenu' }, { role: 'editMenu' },
        { label: 'View', submenu: [{ role: 'reload' }, { role: 'forceReload' }, { type: 'separator' }, { role: 'togglefullscreen' }] },
        { role: 'windowMenu' }
      ]));
    } else {
      Menu.setApplicationMenu(null);
    }
    createWindow();
    app.on('activate', () => { if (BrowserWindow.getAllWindows().length === 0) createWindow(); });
  });

  // Ctrl+R / F5 reload on Windows (menu is hidden there)
  app.on('web-contents-created', (e, wc) => {
    wc.on('before-input-event', (ev, input) => {
      if (isMac || input.type !== 'keyDown') return;
      if (input.key === 'F5' || (input.control && input.key.toLowerCase() === 'r')) { ev.preventDefault(); wc.reload(); }
      if (input.key === 'F11') { ev.preventDefault(); const w = BrowserWindow.fromWebContents(wc); if (w) w.setFullScreen(!w.isFullScreen()); }
    });
  });

  app.on('window-all-closed', () => { if (!isMac) app.quit(); });
}
