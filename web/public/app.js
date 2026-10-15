(() => {
  "use strict";
  const $ = (id) => document.getElementById(id);
  const store = {
    get(key) { try { return localStorage.getItem(key) || ""; } catch { return ""; } },
    set(key, value) { try { value ? localStorage.setItem(key, value) : localStorage.removeItem(key); } catch {} },
  };
  const icons = {
    link: '<svg class="icon" viewBox="0 0 24 24"><path d="M10 13a5 5 0 0 0 7.54.54l3-3a5 5 0 0 0-7.07-7.07l-1.72 1.71"/><path d="M14 11a5 5 0 0 0-7.54-.54l-3 3a5 5 0 0 0 7.07 7.07l1.71-1.71"/></svg>',
    click: '<svg class="icon" viewBox="0 0 24 24"><path d="M14 4.1 12 6"/><path d="m5.1 8-2.9-.8"/><path d="m6 12-1.9 2"/><path d="M7.2 2.2 8 5.1"/><path d="M9.037 9.69a.498.498 0 0 1 .653-.653l11 4.5a.5.5 0 0 1-.074.949l-4.349 1.041a1 1 0 0 0-.74.739l-1.04 4.35a.5.5 0 0 1-.95.074z"/></svg>',
    field: '<svg class="icon" viewBox="0 0 24 24"><path d="M17 22h-1a4 4 0 0 1-4-4V6a4 4 0 0 1 4-4h1"/><path d="M7 22h1a4 4 0 0 0 4-4v-1"/><path d="M7 2h1a4 4 0 0 1 4 4v1"/></svg>',
    globe: '<svg class="icon" viewBox="0 0 24 24"><circle cx="12" cy="12" r="10"/><path d="M12 2a14.5 14.5 0 0 0 0 20 14.5 14.5 0 0 0 0-20"/><path d="M2 12h20"/></svg>',
    trash: '<svg class="icon" viewBox="0 0 24 24"><path d="M3 6h18"/><path d="M19 6v14c0 1-1 2-2 2H7c-1 0-2-1-2-2V6"/><path d="M8 6V4c0-1 1-2 2-2h4c1 0 2 1 2 2v2"/></svg>',
  };

  const params = new URLSearchParams(location.hash.slice(1));
  if (/^[0-9a-f]{64}$/.test(params.get("r") || "")) store.set("room", params.get("r"));
  const pendingUrl = /^https?:\/\//i.test(params.get("u") || "") ? params.get("u") : "";
  if (location.hash) history.replaceState(null, "", location.pathname);

  let ws = null, tvOnline = false, attempts = 0, retryTimer = 0, state = {}, elements = [], lastUrl = null, tab = store.get("tab") || "page", toastTimer = 0, pending = pendingUrl;

  function toast(text) {
    const el = $("toast");
    el.textContent = text;
    el.classList.add("show");
    clearTimeout(toastTimer);
    toastTimer = setTimeout(() => el.classList.remove("show"), 2200);
  }

  function screen() {
    const paired = !!store.get("room");
    $("pair").hidden = paired;
    $("app").hidden = !paired;
    if (paired) connect(); else { ws?.close(); ws = null; }
  }

  function badge() {
    const online = ws?.readyState === 1 && tvOnline;
    $("badge").classList.toggle("online", online);
    $("badgeText").textContent = online ? "TV online" : ws?.readyState === 1 ? "TV offline" : "Łączenie";
  }

  function connect() {
    clearTimeout(retryTimer);
    if (ws && ws.readyState < 2) return;
    const socket = new WebSocket(`${location.protocol === "https:" ? "wss" : "ws"}://${location.host}/ws/phone?room=${store.get("room")}`);
    ws = socket;
    socket.onopen = () => { attempts = 0; badge(); };
    socket.onmessage = (event) => {
      let msg; try { msg = JSON.parse(event.data); } catch { return; }
      if (msg.type === "presence") {
        tvOnline = !!msg.tv;
        badge();
        if (tvOnline) { send("state", {}, true); if (pending) { send("open", { url: pending }); toast("Wysłano na TV"); pending = ""; } }
      } else if (msg.type === "state") {
        tvOnline = true; badge(); render(msg);
      } else if (msg.type === "elements") {
        elements = Array.isArray(msg.items) ? msg.items : []; renderElements();
      } else if (msg.type === "history") {
        renderList($("history"), msg.items || [], false);
      } else if (msg.type === "error") {
        toast(msg.message || "Błąd telewizora");
      }
    };
    socket.onclose = () => {
      if (ws !== socket) return;
      tvOnline = false; badge();
      if (!store.get("room")) return;
      retryTimer = setTimeout(connect, Math.min(15000, 800 * 2 ** attempts++));
    };
  }

  function send(name, data = {}, quiet = false) {
    if (ws?.readyState !== 1 || !tvOnline) { if (!quiet) toast("Telewizor jest offline"); return false; }
    ws.send(JSON.stringify({ v: 1, type: "command", name, data }));
    return true;
  }

  function host(url) { try { return new URL(url).host.replace(/^www\./, ""); } catch { return url; } }

  function render(s) {
    state = s;
    const home = !s.url || s.url === "about:blank" || s.url.includes("home.bbtv.invalid");
    $("nowTitle").textContent = home ? (s.page === "history" ? "Historia" : "Ekran startowy") : s.title || host(s.url);
    $("nowUrl").textContent = home ? "BB TV" : s.url;
    $("addFav").disabled = home;
    renderList($("favs"), s.bookmarks || [], true);
    if (s.url !== lastUrl) {
      lastUrl = s.url;
      elements = []; renderElements();
      if (tab === "page") setTimeout(() => send("elements", {}, true), 600);
    }
  }

  function renderElements() {
    const box = $("elements");
    const query = $("filter").value.trim().toLowerCase();
    box.dataset.empty = lastUrl && lastUrl !== "about:blank" ? "Ładowanie elementów strony…" : "Otwórz stronę, aby zobaczyć jej linki i przyciski";
    box.replaceChildren(...elements.filter((e) => !query || `${e.text} ${e.href}`.toLowerCase().includes(query)).slice(0, 200).map((e) => {
      if (e.kind === "input") {
        const row = document.createElement("div");
        row.className = "item field";
        const form = document.createElement("form");
        form.className = "row";
        const input = document.createElement("input");
        input.className = "input";
        input.placeholder = e.text || "Pole tekstowe";
        input.value = e.value || "";
        input.enterKeyHint = "go";
        const btn = document.createElement("button");
        btn.className = "btn btn-primary";
        btn.textContent = "Wpisz";
        form.append(input, btn);
        form.onsubmit = (ev) => { ev.preventDefault(); if (send("fill", { id: e.id, text: input.value, submit: true })) { toast("Wpisano na TV"); input.blur(); } };
        row.append(form);
        return row;
      }
      const btn = document.createElement("button");
      btn.className = `item${e.visible ? " visible" : ""}`;
      btn.innerHTML = `<span class="kind">${e.href ? icons.link : icons.click}</span><span class="text"><b></b><span></span></span>`;
      btn.querySelector("b").textContent = e.text || host(e.href);
      btn.querySelector(".text span").textContent = e.href ? host(e.href) + new URL(e.href, "https://x").pathname.replace(/\/$/, "") : e.visible ? "Widoczne na ekranie" : "Przycisk";
      btn.onclick = () => { if (send("activate", { id: e.id })) toast(`Klik: ${e.text || host(e.href)}`); };
      return btn;
    }));
  }

  function renderList(box, items, favorites) {
    box.dataset.empty = favorites ? "Brak ulubionych" : "Historia jest pusta";
    box.replaceChildren(...items.map((item, index) => {
      const row = document.createElement("div");
      row.className = "item";
      row.innerHTML = `<span class="kind">${icons.globe}</span><span class="text"><b></b><span></span></span>`;
      row.querySelector("b").textContent = item.title || host(item.url);
      row.querySelector(".text span").textContent = host(item.url);
      row.onclick = () => { if (send("open", { url: item.url })) toast("Otwieram na TV"); };
      if (favorites) {
        const del = document.createElement("button");
        del.className = "btn btn-ghost btn-icon";
        del.setAttribute("aria-label", "Usuń z ulubionych");
        del.innerHTML = icons.trash;
        del.onclick = (ev) => { ev.stopPropagation(); if (confirm(`Usunąć „${item.title || host(item.url)}” z ulubionych?`)) send("bookmark_delete", { index }); };
        row.append(del);
      }
      return row;
    }));
  }

  function selectTab(name) {
    tab = name;
    store.set("tab", name);
    document.querySelectorAll(".tab").forEach((t) => t.setAttribute("aria-selected", String(t.dataset.tab === name)));
    document.querySelectorAll(".panel").forEach((p) => { p.hidden = p.dataset.panel !== name; });
    if (name === "page") send("elements", {}, true);
    if (name === "history") send("history", {}, true);
  }

  $("codeForm").onsubmit = async (event) => {
    event.preventDefault();
    const code = $("code").value.replace(/\D/g, "");
    if (code.length !== 6) { toast("Kod ma 6 cyfr"); return; }
    try {
      const res = await fetch(`/api/code/${code}`, { method: "POST" });
      const body = await res.json();
      if (!res.ok) throw new Error(body.error);
      store.set("room", body.room);
      $("code").value = "";
      screen();
    } catch (e) { toast(e.message || "Nie udało się połączyć"); }
  };
  $("go").onsubmit = (event) => {
    event.preventDefault();
    const text = $("address").value.trim();
    if (!text) return;
    if (send("search", { text: text.slice(0, 2048) })) { toast("Otwieram na TV"); $("address").value = ""; $("address").blur(); }
  };
  $("filter").oninput = renderElements;
  $("refresh").onclick = () => send("elements");
  $("addFav").onclick = () => { if (send("bookmark_add")) toast("Dodano do ulubionych"); };
  $("clearHistory").onclick = () => { if (confirm("Wyczyścić historię na TV?")) send("history_clear"); };
  $("forget").onclick = () => { if (confirm("Odłączyć ten telewizor? Trzeba będzie znowu zeskanować kod.")) { store.set("room", ""); screen(); } };
  $("typeForm").onsubmit = (event) => { event.preventDefault(); if (send("type", { text: $("typeText").value })) { $("typeText").value = ""; toast("Wpisano na TV"); } };
  document.querySelectorAll(".tab").forEach((t) => { t.onclick = () => selectTab(t.dataset.tab); });
  document.querySelectorAll("[data-cmd]").forEach((b) => { b.onclick = () => send(b.dataset.cmd); });
  document.querySelectorAll("[data-volume]").forEach((b) => { b.onclick = () => send("volume", { delta: Number(b.dataset.volume) }); });

  document.querySelectorAll("[data-dir]").forEach((b) => {
    const [dx, dy] = b.dataset.dir.split(",").map(Number);
    let timer = 0, count = 0;
    const stop = () => { clearTimeout(timer); timer = 0; };
    const repeat = () => { const speed = Math.min(45, 12 + count++ * 2); send("move", { dx: dx * speed, dy: dy * speed }, true); timer = setTimeout(repeat, 80); };
    b.onpointerdown = (e) => { e.preventDefault(); count = 0; send("move", { dx: dx * 16, dy: dy * 16 }); timer = setTimeout(repeat, 350); };
    b.onpointerup = b.onpointercancel = b.onpointerleave = stop;
    b.oncontextmenu = (e) => e.preventDefault();
  });

  const pad = $("pad"), points = new Map();
  let mx = 0, my = 0, sy = 0, travel = 0, multi = false, held = false, hold = 0, flush = 0;
  const clamp = (v, m) => Math.max(-m, Math.min(m, v));
  const pump = () => {
    flush = 0;
    if (mx || my) { send("move", { dx: clamp(mx * 2, 500), dy: clamp(my * 2, 500) }, true); mx = my = 0; }
    if (sy) { send("scroll", { dx: 0, dy: clamp(-sy * 3, 4000) }, true); sy = 0; }
  };
  pad.onpointerdown = (e) => {
    pad.setPointerCapture(e.pointerId);
    points.set(e.pointerId, { x: e.clientX, y: e.clientY });
    if (points.size === 1) {
      travel = 0; multi = false; held = false;
      hold = setTimeout(() => { if (travel < 10 && !multi) { held = true; send("fullscreen"); navigator.vibrate?.(30); } }, 550);
    } else { multi = true; clearTimeout(hold); }
  };
  pad.onpointermove = (e) => {
    const p = points.get(e.pointerId); if (!p) return;
    const dx = e.clientX - p.x, dy = e.clientY - p.y;
    p.x = e.clientX; p.y = e.clientY;
    travel += Math.abs(dx) + Math.abs(dy);
    if (multi) sy += dy / 2; else { mx += dx; my += dy; }
    if (!flush) flush = setTimeout(pump, 33);
  };
  pad.onpointerup = pad.onpointercancel = (e) => {
    if (!points.delete(e.pointerId) || points.size) return;
    clearTimeout(hold);
    if (e.type === "pointerup" && !held && !multi && travel < 10) send("click");
  };
  pad.oncontextmenu = (e) => e.preventDefault();

  document.addEventListener("visibilitychange", () => { if (!document.hidden && store.get("room")) { attempts = 0; connect(); } });
  selectTab(["page", "remote", "favs", "history"].includes(tab) ? tab : "page");
  screen();
})();
