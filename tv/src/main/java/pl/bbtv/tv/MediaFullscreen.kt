package pl.bbtv.tv

import android.app.Activity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.widget.FrameLayout

class MediaFullscreen(private val activity: Activity, private val web: WebView, private val cursor: CursorView, private val toolbar: View) {
    private val content = activity.findViewById<ViewGroup>(android.R.id.content)
    private val originalCursorParent = cursor.parent as ViewGroup
    private var overlay: FrameLayout? = null
    private var custom: View? = null
    private var callback: WebChromeClient.CustomViewCallback? = null
    private var savedUi = 0
    private var generation = 0
    private var requesting = false
    var fullscreen = false
        private set
    private fun enter() {
        if (!fullscreen) savedUi = activity.window.decorView.systemUiVisibility
        fullscreen = true
        toolbar.visibility = View.GONE
        @Suppress("DEPRECATION")
        activity.window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_FULLSCREEN or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
    }
    fun show(view: View, cb: WebChromeClient.CustomViewCallback) {
        if (custom != null) { cb.onCustomViewHidden(); return }
        enter(); custom = view; callback = cb
        overlay = FrameLayout(activity).apply {
            setBackgroundColor(android.graphics.Color.BLACK)
            addView(view, FrameLayout.LayoutParams(-1, -1))
            (cursor.parent as ViewGroup).removeView(cursor)
            addView(cursor, FrameLayout.LayoutParams(-1, -1))
        }
        content.addView(overlay, ViewGroup.LayoutParams(-1, -1))
        // Do not pause, destroy, detach, or reload WebView: its media pipeline stays alive.
        web.visibility = View.INVISIBLE
        view.requestFocus()
    }
    fun toggle() {
        if (fullscreen || requesting) { exit(); return }
        requesting = true
        val request = ++generation
        web.evaluateJavascript("(function(){try{return !!document.querySelector('video');}catch(e){return false;}})();") { hasVideo ->
            if (request != generation) return@evaluateJavascript
            requesting = false
            if (hasVideo != "true" || activity.isFinishing || activity.isDestroyed) return@evaluateJavascript
            enter()
            web.evaluateJavascript(FORCE, null)
        }
    }
    fun exit(): Boolean {
        cancelPending()
        if (!fullscreen && custom == null) return false
        fullscreen = false
        val oldCallback = callback; callback = null; custom = null
        overlay?.let { host -> host.removeView(cursor); host.removeAllViews(); content.removeView(host) }; overlay = null
        if (cursor.parent == null) originalCursorParent.addView(cursor, ViewGroup.LayoutParams(-1, -1))
        web.visibility = View.VISIBLE
        toolbar.visibility = View.VISIBLE
        @Suppress("DEPRECATION")
        activity.window.decorView.systemUiVisibility = savedUi
        web.evaluateJavascript(RESTORE, null)
        oldCallback?.onCustomViewHidden()
        web.requestFocus()
        return true
    }
    fun cancelPending() { generation++; requesting = false }
    fun dispatchTouch(event: MotionEvent) { (custom ?: web).dispatchTouchEvent(event) }
    fun keepAwake(playing: Boolean) { if (playing) activity.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) else activity.window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
    companion object {
        // CSS fallback keeps the existing media element in place, including audio and currentTime.
        val FORCE = """
            (function(){try{
                var v=document.querySelector('video');if(!v)return;
                var old=[];var p=v;var n=0;
                while(p&&p.style&&n++<32){old.push([p,p.getAttribute('style')]);if(p===v){p.style.cssText+=';position:fixed!important;top:0!important;left:0!important;width:100vw!important;height:100vh!important;max-width:none!important;max-height:none!important;z-index:2147483646!important;object-fit:contain!important;background:black!important;';}else{p.style.cssText+=';transform:none!important;filter:none!important;perspective:none!important;overflow:visible!important;position:static!important;contain:none!important;';}p=p.parentElement;}
                window.__bbtvFullscreen=old;
                var f=v.requestFullscreen||v.webkitRequestFullscreen||v.webkitEnterFullscreen;
                if(f){try{var r=f.call(v);if(r&&r['catch'])r['catch'](function(){});}catch(x){}}
            }catch(e){}})();
        """.trimIndent()
        val RESTORE = """
            (function(){try{
                var old=window.__bbtvFullscreen||[];window.__bbtvFullscreen=null;
                for(var i=old.length-1;i>=0;i--){if(old[i][1]===null)old[i][0].removeAttribute('style');else old[i][0].setAttribute('style',old[i][1]);}
                if(document.fullscreenElement||document.webkitFullscreenElement){var f=document.exitFullscreen||document.webkitExitFullscreen;if(f){var r=f.call(document);if(r&&r['catch'])r['catch'](function(){});}}
            }catch(e){}})();
        """.trimIndent()
    }
}
