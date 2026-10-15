/* ES5 only. No JavaScript bridge, intervals, or DOM-wide MutationObserver. */
(function () {
    'use strict';
    try {
        if (window.__bbtv) { window.__bbtv.configure(window.__bbtvConfig || {}); return; }
        var state = { enabled: false, host: '', count: 0, media: false, timer: null };
        var mediaNodes = [];
        function rememberMedia(node) {
            if (!node || !/^(VIDEO|AUDIO)$/.test(node.tagName)) return;
            for (var i = 0; i < mediaNodes.length; i++) if (mediaNodes[i] === node) return;
            if (mediaNodes.length >= 8) mediaNodes.shift();
            mediaNodes.push(node);
        }
        function isPlaying() {
            for (var i = 0; i < mediaNodes.length; i++) if (!mediaNodes[i].paused && !mediaNodes[i].ended) return true;
            return false;
        }
        var generic = '.adsbygoogle,ins.adsbygoogle,[id^="google_ads_"],[id^="div-gpt-ad"],[id^="ad-slot"],[class~="ad-banner"],[class~="advertisement"],[class~="ad-overlay"],.popup-ad,.ad-container';
        var perDomain = {
            'youtube.com': 'ytd-ad-slot-renderer,ytd-promoted-sparkles-web-renderer,ytd-display-ad-renderer,.ytp-ad-overlay-container',
            'vod.tvp.pl': '.advertisement,.ad-banner',
            'pluto.tv': '.ad-banner,.advertisement'
        };
        var overlays = '.ad-overlay,.ads-overlay,.video-ad-overlay,.adblock-overlay,[id^="ad-overlay"],.ytp-ad-overlay-container';
        var skip = '.ytp-ad-skip-button,.ytp-ad-skip-button-modern,.ytp-skip-ad-button,[class~="skip-ad"],[aria-label="Pomiń reklamę"],[aria-label="Skip ad"]';
        function selectors() {
            var css = generic;
            for (var domain in perDomain) if (Object.prototype.hasOwnProperty.call(perDomain, domain) && (state.host === domain || state.host.slice(-(domain.length + 1)) === '.' + domain)) css += ',' + perDomain[domain];
            if (state.media) css += ',' + overlays;
            return css;
        }
        function style() {
            var el = document.getElementById('__bbtv_ad_css');
            if (!state.enabled) { if (el && el.parentNode) el.parentNode.removeChild(el); return; }
            if (!el) { el = document.createElement('style'); el.id = '__bbtv_ad_css'; (document.head || document.documentElement).appendChild(el); }
            el.textContent = selectors() + '{display:none!important;visibility:hidden!important;pointer-events:none!important;}';
        }
        function scan() {
            state.timer = null;
            if (!state.enabled || document.hidden) return;
            style();
            var nodes = document.querySelectorAll(selectors());
            for (var i = 0; i < nodes.length && i < 200; i++) if (!nodes[i].__bbtvCounted) { nodes[i].__bbtvCounted = true; state.count++; }
            if (state.media) {
                var buttons = document.querySelectorAll(skip);
                for (var j = 0; j < buttons.length && j < 8; j++) if (buttons[j].offsetWidth && !buttons[j].disabled) { try { buttons[j].click(); } catch (e) {} }
            }
        }
        function schedule() { if (!state.timer && state.enabled) state.timer = setTimeout(function () { try { scan(); } catch (e) { state.timer = null; } }, 350); }
        function playing(event) { if (event.target && /^(VIDEO|AUDIO)$/.test(event.target.tagName)) { rememberMedia(event.target); state.media = true; style(); schedule(); } }
        function configure(config) {
            state.enabled = !!config.enabled; state.host = config.host || '';
            var media = document.querySelectorAll('video,audio');
            for (var k = 0; k < media.length && k < 8; k++) rememberMedia(media[k]);
            if (isPlaying()) state.media = true;
            if (!state.enabled && state.timer) { clearTimeout(state.timer); state.timer = null; }
            style(); schedule();
        }
        window.__bbtv = { configure: configure, metrics: function () { return { count: state.count, media: state.media, playing: isPlaying() }; } };
        document.addEventListener('play', playing, true);
        document.addEventListener('playing', playing, true);
        document.addEventListener('loadedmetadata', schedule, true);
        document.addEventListener('click', schedule, true);
        document.addEventListener('scroll', schedule, true);
        document.addEventListener('visibilitychange', schedule, false);
        // At most one targeted scan per 5 seconds while media advances, driven by native media events.
        var lastMediaScan = 0;
        document.addEventListener('timeupdate', function () { var now = new Date().getTime(); if (now - lastMediaScan > 5000) { lastMediaScan = now; schedule(); } }, true);
        // Bounded startup retries catch SPA ad slots without a permanent DOM observer.
        setTimeout(schedule, 1000); setTimeout(schedule, 3000); setTimeout(schedule, 7000);
        configure(window.__bbtvConfig || {});
    } catch (e) {}
}());
