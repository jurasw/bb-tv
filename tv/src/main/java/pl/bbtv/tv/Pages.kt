package pl.bbtv.tv

import org.json.JSONArray
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object Pages {
    const val BASE = "https://home.bbtv.invalid/"
    fun escape(s: String) = s.replace("&", "&amp;").replace("\"", "&quot;").replace("'", "&#39;").replace("<", "&lt;").replace(">", "&gt;")
    private fun host(url: String) = runCatching { java.net.URI(url).host.orEmpty().removePrefix("www.") }.getOrDefault("")
    private const val CSS = "html,body{margin:0;min-height:100%}body{background:#242424;color:#ededed;font-family:-apple-system,BlinkMacSystemFont,'Helvetica Neue',Arial,sans-serif;box-sizing:border-box;-webkit-font-smoothing:antialiased}" +
        "*{box-sizing:border-box}a{color:inherit;text-decoration:none;outline:none}.m{color:#a1a1a1}" +
        ".k{-webkit-transition:-webkit-transform .15s ease,background .15s ease;transition:transform .15s ease,background .15s ease}" +
        ".r.k:focus{background:#505050;box-shadow:0 0 0 .15vw #ededed}"
    private const val NAV = "<script>(function(){var a=[].slice.call(document.querySelectorAll('.k'));function go(p){location.href='https://home.bbtv.invalid/'+p;}" +
        "if(a.length)a[0].focus();document.addEventListener('keydown',function(e){var k=e.keyCode,c=document.activeElement;" +
        "if(c&&c.tagName==='INPUT'){if(k===13&&c.value.trim()){e.preventDefault();go('go?q='+encodeURIComponent(c.value.trim()));return;}if(k===37||k===39)return;}" +
        "if(k<37||k>40)return;e.preventDefault();if(a.indexOf(c)<0){if(a.length)a[0].focus();return;}" +
        "var r=c.getBoundingClientRect(),cx=r.left+r.width/2,cy=r.top+r.height/2,best=null,bd=1e9;a.forEach(function(el){if(el===c)return;var q=el.getBoundingClientRect(),x=q.left+q.width/2-cx,y=q.top+q.height/2-cy;" +
        "if(!(k==37?x<-5:k==39?x>5:k==38?y<-5:y>5))return;var d=(k==37||k==39)?Math.abs(x)+Math.abs(y)*3:Math.abs(y)+Math.abs(x)*3;if(d<bd){bd=d;best=el;}});" +
        "if(best){best.focus();best.scrollIntoView(false);}else if(k===38)go('up');});})();</script>"
    private fun page(body: String) = "<!doctype html><html lang='pl'><head><meta charset='utf-8'><meta name='viewport' content='width=device-width,initial-scale=1'><title>BB TV</title><style>$CSS" +
        ".w{min-height:100vh;display:-webkit-flex;display:flex;-webkit-flex-direction:column;flex-direction:column;-webkit-justify-content:center;justify-content:center;-webkit-align-items:center;align-items:center;padding:6vh 6vw 10vh}" +
        ".start{width:73vw;margin:0 auto;padding:9.5vh 0 8vh}.start h1,.start h2{margin:0 0 1.1vw;font-size:1.45vw;line-height:1.2;font-weight:700;letter-spacing:-.035em}" +
        ".p{display:-webkit-flex;display:flex;-webkit-flex-wrap:wrap;flex-wrap:wrap;margin:0 -0.7vw 4.1vw}" +
        ".t{display:-webkit-flex;display:flex;-webkit-flex-direction:column;flex-direction:column;-webkit-align-items:center;align-items:center;width:4.85vw;margin:0 .7vw 1.1vw;text-align:center}" +
        ".t i{display:-webkit-flex;display:flex;-webkit-align-items:center;align-items:center;-webkit-justify-content:center;justify-content:center;width:4.85vw;height:4.85vw;border-radius:1.3vw;background:#626262;font-style:normal;font-size:2.9vw;font-weight:400;transition:box-shadow .15s,transform .15s}" +
        ".t img{display:block;width:100%;height:100%;object-fit:contain;border-radius:inherit}.t i.light,.t i.claude,.t i.chatgpt{background:#fff}.t i.chatgpt img,.t i.google img,.t i.claude img{width:72%;height:72%}.t i.youtube img{width:84%;height:84%}" +
        ".t span{display:-webkit-box;-webkit-box-orient:vertical;-webkit-line-clamp:2;overflow:hidden;overflow-wrap:break-word;width:6.1vw;margin-top:.55vw;font-size:.78vw;line-height:1.25;font-weight:500;letter-spacing:-.015em}" +
        ".t:focus i{box-shadow:0 0 0 .13vw #242424,0 0 0 .28vw #c3c3c3;transform:scale(1.035)}.t:hover i{transform:scale(1.035)}" +
        ".s{display:block;width:100%;height:14.4vh;min-height:5vw;padding:0 3vw 0 5.1vw;border-radius:1.35vw;border:0;background:#505050 url(\"data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' viewBox='0 0 24 24' fill='none' stroke='%23c7c7c7' stroke-width='1.7' stroke-linecap='round'%3E%3Cpath d='m21 21-4.34-4.34'/%3E%3Ccircle cx='11' cy='11' r='8'/%3E%3C/svg%3E\") 2.4vw 50%/1.45vw no-repeat;color:#ededed;font:inherit;font-size:1.2vw;letter-spacing:-.02em;outline:none}" +
        ".s::-webkit-input-placeholder{color:#c7c7c7}.s:focus{box-shadow:0 0 0 .18vw #6895bc;background-color:#565656}" +
        ".q{position:fixed;right:2.5vw;bottom:3vh;display:-webkit-flex;display:flex;-webkit-align-items:center;align-items:center;padding:1vw;border-radius:1.35vw;background:#383838}" +
        ".q svg{width:9vw;height:9vw;border-radius:.5vw;background:#fff}.q div{margin-left:1.2vw;font-size:1vw;line-height:1.5}.q b{display:block;font-size:1.25vw}.q em{font-style:normal;font-weight:700;color:#fafafa;letter-spacing:.12em;font-size:1.6vw}" +
        ".h{width:70vw}.h h1{font-size:2.4vw;margin:0 0 3vh;letter-spacing:-.02em}.r{display:-webkit-flex;display:flex;-webkit-align-items:center;align-items:center;padding:2vh 1.6vw;margin-bottom:1vh;border-radius:.8vw;border:1px solid rgba(255,255,255,.1);background:#171717}" +
        ".r span{-webkit-flex:1;flex:1;min-width:0;overflow:hidden;white-space:nowrap;text-overflow:ellipsis;font-size:1.4vw;font-weight:500}.r small{margin-left:2vw;font-size:1.1vw}.e{font-size:1.4vw}" +
        "</style></head><body>$body$NAV</body></html>"
    fun home(bookmarks: JSONArray, pair: String, icons: Map<String, String> = emptyMap()): String {
        val tiles = (0 until bookmarks.length()).joinToString("") { i ->
            val item = bookmarks.getJSONObject(i)
            val url = item.optString("url"); val title = item.optString("title").ifBlank { host(url) }
            val domain = host(url)
            val icon = icons[domain]?.let { "<img src=\"${escape(it)}\" alt='' aria-hidden='true'>" } ?: escape(title.take(1).uppercase())
            val style = when (domain) { "google.com" -> "light google"; "chatgpt.com" -> "chatgpt"; "claude.ai" -> "claude"; "youtube.com" -> "youtube"; else -> "" }
            "<a class='k t' href=\"${escape(url)}\" aria-label=\"${escape(title)}\"><i class='$style'>$icon</i><span>${escape(title)}</span></a>"
        }
        return page("<main class='start'><section aria-labelledby='favorites-title'><h1 id='favorites-title'>Ulubione</h1><div class='p'>$tiles</div></section><section aria-labelledby='search-title'><h2 id='search-title'>Szukaj lub wpisz adres</h2><input class='k s' aria-labelledby='search-title' type='text' inputmode='url' enterkeyhint='go' autocapitalize='off' autocomplete='off' spellcheck='false' placeholder='Szukaj w Google lub wpisz adres'></section></main>$pair")
    }
    fun history(items: JSONArray): String {
        val format = SimpleDateFormat("d MMM, HH:mm", Locale("pl", "PL"))
        val rows = (0 until items.length()).joinToString("") { i ->
            val item = items.getJSONObject(i); val url = item.optString("url")
            "<a class='k r' href=\"${escape(url)}\"><span>${escape(item.optString("title").ifBlank { url })}</span><small class='m'>${escape(host(url))} · ${format.format(Date(item.optLong("time")))}</small></a>"
        }
        val clear = if (items.length() > 0) "<a class='k r' href='${BASE}clear-history'><span>Wyczyść historię</span></a>" else "<p class='m e'>Historia jest pusta.</p>"
        return page("<div class='w' style='-webkit-justify-content:flex-start;justify-content:flex-start'><div class='h'><h1>Historia</h1>$rows$clear</div></div>")
    }
}
