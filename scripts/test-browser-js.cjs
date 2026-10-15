// Optional: node scripts/test-browser-js.cjs (no npm dependencies).
const fs = require('node:fs');
const vm = require('node:vm');
const assert = require('node:assert/strict');
const listeners = {};
const timers = new Map();
let nextTimer = 1, style = null, queries = 0, skipped = 0;
const ad = {};
const skip = { offsetWidth: 30, disabled: false, click() { skipped++; } };
const parent = { appendChild(el) { style = el; el.parentNode = parent; }, removeChild() { style = null; } };
const document = {
    hidden: false, head: parent, documentElement: parent,
    createElement() { return {}; },
    getElementById() { return style; },
    querySelectorAll(selector) { if (selector === 'video,audio') return []; queries++; return selector.includes('skip-ad') ? [skip] : [ad]; },
    addEventListener(name, handler) { listeners[name] = handler; }
};
const context = {
    document, window: { __bbtvConfig: { enabled: true, host: 'www.youtube.com' } }, Date,
    setTimeout(fn, delay) { const id = nextTimer++; timers.set(id, { fn, delay }); return id; },
    clearTimeout(id) { timers.delete(id); }
};
vm.runInNewContext(fs.readFileSync('tv/src/main/assets/browser.js', 'utf8'), context);
function flushDebounce() {
    for (const [id, timer] of [...timers]) if (timer.delay === 350) { timers.delete(id); timer.fn(); }
}
assert(style.textContent.includes('ytd-ad-slot-renderer'), 'per-domain CSS');
for (let i = 0; i < 20; i++) listeners.click({});
assert.equal([...timers.values()].filter(t => t.delay === 350).length, 1, 'bursts are debounced');
flushDebounce();
assert.equal(queries, 1);
assert.equal(context.window.__bbtv.metrics().count, 1);
listeners.play({ target: { tagName: 'VIDEO' } });
flushDebounce();
assert.equal(skipped, 1, 'media mode clicks available skip button');
assert.equal(context.window.__bbtv.metrics().count, 1, 'same ad counted once');
context.window.__bbtv.configure({ enabled: false });
assert.equal(style, null, 'disabling restores CSS visibility');
const before = queries;
listeners.click({}); flushDebounce();
assert.equal(queries, before, 'disabled mode does no scans');
console.log('Browser JS: debounce, media skip, counts, per-domain CSS and disable passed.');

const fullscreenSource = fs.readFileSync('tv/src/main/java/pl/bbtv/tv/MediaFullscreen.kt', 'utf8');
function fullscreenScript(name) {
    return fullscreenSource.match(new RegExp('val ' + name + ' = """([\\s\\S]*?)"""'))[1];
}
function mediaNode(original, parentElement) {
    return {
        parentElement, style: { cssText: original || '' },
        getAttribute() { return original; },
        setAttribute(name, value) { this.style.cssText = value; },
        removeAttribute() { this.style.cssText = ''; }
    };
}
const body = mediaNode(null, null);
const video = Object.assign(mediaNode('width:320px', body), {
    currentTime: 47.5, paused: false, volume: 0.4,
    requestFullscreen() { throw new Error('No user activation on old WebView'); }
});
const mediaContext = { window: {}, document: { querySelector() { return video; } } };
vm.runInNewContext(fullscreenScript('FORCE'), mediaContext);
assert(video.style.cssText.includes('position:fixed'), 'CSS fallback when native fullscreen is rejected');
assert.equal(video.parentElement, body, 'video is never moved');
vm.runInNewContext(fullscreenScript('RESTORE'), mediaContext);
assert.equal(video.style.cssText, 'width:320px', 'original video styles restored');
assert.equal(body.style.cssText, '', 'missing ancestor style restored');
assert.equal(video.currentTime, 47.5, 'playback position preserved');
assert.equal(video.paused, false, 'playback state preserved');
assert.equal(video.volume, 0.4, 'audio state preserved');
console.log('Fullscreen JS: rejected native request, style restoration, position and audio passed.');
