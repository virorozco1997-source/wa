(function () {
  if (window.__waInjected) return;
  window.__waInjected = true;

  // 1. Español
  try {
    Object.defineProperty(navigator, 'language', { get: function () { return 'es-AR'; } });
    Object.defineProperty(navigator, 'languages', { get: function () { return ['es-AR', 'es-419', 'es']; } });
  } catch (e) {}

  // 2. Notificaciones: WebView no soporta la API, la reemplazamos por un puente nativo
  try {
    var N = function (title, opts) {
      opts = opts || {};
      try { AndroidBridge.notify(String(title), String(opts.body || ''), String(opts.tag || '')); } catch (e) {}
      this.title = title;
      this.onclick = null;
      this.close = function () {};
      this.addEventListener = function () {};
    };
    N.permission = 'granted';
    N.requestPermission = function (cb) { if (cb) cb('granted'); return Promise.resolve('granted'); };
    Object.defineProperty(window, 'Notification', { value: N, configurable: true, writable: true });
  } catch (e) {}

  // 3. Ocultar Estados / Novedades, Canales y Comunidades
  var re = /^(status|estados?|updates|novedades|actualizaciones|channels|canales|communities|comunidades)$/i;
  function hideTabs() {
    var els = document.querySelectorAll('[aria-label],[title]');
    for (var i = 0; i < els.length; i++) {
      var el = els[i];
      var label = (el.getAttribute('aria-label') || el.getAttribute('title') || '').trim();
      if (!re.test(label)) continue;
      var target = el.closest('[data-navbar-item]') || el.closest('button') || el.closest('[role="tab"]') || el;
      target.style.setProperty('display', 'none', 'important');
    }
  }
  new MutationObserver(hideTabs).observe(document.documentElement, { childList: true, subtree: true });
  hideTabs();

  // 4. Diseño simple + colores del chat
  var cfg = __CONFIG__;

  function lum(hex) {
    var n = parseInt(hex.slice(1), 16);
    return (0.299 * ((n >> 16) & 255) + 0.587 * ((n >> 8) & 255) + 0.114 * (n & 255)) / 255;
  }

  // La burbuja es el primer descendiente de la fila con fondo y bordes redondeados
  function findBubble(row) {
    var q = [row], seen = 0;
    while (q.length && seen < 60) {
      var el = q.shift();
      seen++;
      if (el !== row) {
        var cs = getComputedStyle(el);
        var bg = cs.backgroundColor;
        if (bg && bg !== 'transparent' && bg !== 'rgba(0, 0, 0, 0)' && parseFloat(cs.borderTopLeftRadius) > 0) return el;
      }
      for (var i = 0; i < el.children.length; i++) q.push(el.children[i]);
    }
    return null;
  }

  function paint() {
    var rows = document.querySelectorAll('.message-out,.message-in');
    for (var i = 0; i < rows.length; i++) {
      var row = rows[i];
      var color = row.classList.contains('message-out') ? cfg.out : cfg.inn;
      var el = row.__wb;
      if (!el) { el = findBubble(row); if (el) row.__wb = el; }
      if (!el) continue;
      if (color) {
        el.style.setProperty('background-color', color, 'important');
        el.setAttribute('data-wa-bubble', lum(color) < 0.55 ? 'dark' : 'light');
      } else {
        el.style.removeProperty('background-color');
        el.removeAttribute('data-wa-bubble');
      }
    }
  }

  function css() {
    var s = '';
    if (cfg.simple) {
      s += '[data-asset-chat-background-light],[data-asset-chat-background-dark]{display:none!important}';
      s += '#main{background-image:none!important}';
      s += '*{text-shadow:none!important}';
    }
    var vars = '';
    if (cfg.out) vars += '--outgoing-background:' + cfg.out + '!important;';
    if (cfg.inn) vars += '--incoming-background:' + cfg.inn + '!important;';
    if (cfg.bg) {
      vars += '--conversation-panel-background:' + cfg.bg + '!important;';
      s += '#main{background-color:' + cfg.bg + '!important}';
    }
    if (vars) s += '*{' + vars + '}';
    s += '[data-wa-bubble="dark"] *:not([data-icon]):not([data-icon] *){color:#fff!important}';
    s += '[data-wa-bubble="light"] *:not([data-icon]):not([data-icon] *){color:#111b21!important}';
    return s;
  }

  window.__applyConfig = function (c) {
    cfg = c;
    var s = document.getElementById('__wa_style');
    if (!s) {
      s = document.createElement('style');
      s.id = '__wa_style';
      (document.head || document.documentElement).appendChild(s);
    }
    s.textContent = css();
    paint();
  };

  var pending = false;
  new MutationObserver(function () {
    if (pending) return;
    pending = true;
    requestAnimationFrame(function () { pending = false; paint(); });
  }).observe(document.documentElement, { childList: true, subtree: true });

  window.__applyConfig(cfg);
})();
