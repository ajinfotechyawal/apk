/* Connects the web app to Android (saving files, sharing, WhatsApp links, printing, theme). Runs after the page loads. */
(function () {
    if (window.__ajBridge || !window.AndroidApp) return;
    window.__ajBridge = true;
    var A = window.AndroidApp;

    function blobToB64(blob) {
        return new Promise(function (res, rej) {
            var r = new FileReader();
            r.onload = function () { var s = String(r.result); res(s.slice(s.indexOf(',') + 1)); };
            r.onerror = rej;
            r.readAsDataURL(blob);
        });
    }
    function dataUrlParts(u) {
        var m = /^data:([^;,]*)(;base64)?,(.*)$/s.exec(u) || [];
        var mime = m[1] || 'application/octet-stream', body = m[3] || '';
        if (!m[2]) body = btoa(unescape(encodeURIComponent(decodeURIComponent(body))));
        return { mime: mime, b64: body };
    }

    // 1) Every "download" link (PDF, JPG, Word, Excel, backups) is saved to Downloads/AJ Billing
    var origClick = HTMLAnchorElement.prototype.click;
    HTMLAnchorElement.prototype.click = function () {
        var a = this;
        if (a.hasAttribute('download')) {
            var href = a.href, name = a.getAttribute('download') || 'file';
            if (/^data:/.test(href)) {
                var p = dataUrlParts(href);
                A.saveFile(p.b64, name, p.mime);
                return;
            }
            if (/^blob:/.test(href)) {
                fetch(href).then(function (r) { return r.blob(); }).then(function (b) {
                    return blobToB64(b).then(function (s) { A.saveFile(s, name, b.type || 'application/octet-stream'); });
                });
                return;
            }
        }
        return origClick.apply(a, arguments);
    };

    // 2) Share (WhatsApp etc.) - the app already calls navigator.share({files, text})
    Object.defineProperty(navigator, 'canShare', { configurable: true, value: function (d) { return !!(d && (d.files || d.text || d.url)); } });
    Object.defineProperty(navigator, 'share', {
        configurable: true,
        value: function (d) {
            d = d || {};
            if (d.files && d.files.length) {
                var f = d.files[0];
                return blobToB64(f).then(function (s) {
                    if (!A.shareFile(s, f.name || 'file', f.type || 'application/octet-stream', d.text || '')) throw new Error('share failed');
                });
            }
            A.shareText([d.title, d.text, d.url].filter(Boolean).join('\n'));
            return Promise.resolve();
        }
    });

    // 3) Links the app opens in a new tab (wa.me etc.) open in the matching Android app
    window.open = function (u) {
        if (u && u !== 'about:blank') A.openUrl(String(u));
        return null;
    };

    // 4) Print: the app prints bills through a hidden iframe; send that page to Android's print service
    window.print = function () { A.printPage(); };
    function hookFrame(f) {
        var n = 0;
        var iv = setInterval(function () {
            try {
                var w = f.contentWindow;
                if (w) w.print = function () { A.printHtml('<!doctype html>' + f.contentDocument.documentElement.outerHTML); };
            } catch (e) {}
            if (++n > 100 || !f.isConnected) clearInterval(iv);
        }, 50);
    }
    new MutationObserver(function (ms) {
        ms.forEach(function (m) { m.addedNodes.forEach(function (n) { if (n.tagName === 'IFRAME') hookFrame(n); }); });
    }).observe(document.body, { childList: true });

    // 5) Status bar follows the app's light/dark mode
    function pushTheme() { A.setTheme(document.documentElement.classList.contains('dark')); }
    new MutationObserver(pushTheme).observe(document.documentElement, { attributes: true, attributeFilter: ['class'] });
    pushTheme();

    // the browser "Install app" button makes no sense inside the app
    var st = document.createElement('style');
    st.textContent = '#installAppBtn{display:none!important}';
    document.head.appendChild(st);
})();
