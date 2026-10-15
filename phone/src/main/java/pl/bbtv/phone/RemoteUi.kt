@file:OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package pl.bbtv.phone

import android.os.SystemClock
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Fullscreen
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Keyboard
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.Tv
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONObject

interface Remote {
    fun send(name: String, data: JSONObject = JSONObject(), quiet: Boolean = false)
    fun connect(pair: Boolean)
    fun discover()
    fun go(text: String)
    fun voice()
    fun search(template: String): Boolean
    fun language(value: String): Boolean
}

class RemoteState {
    var host by mutableStateOf("")
    var pin by mutableStateOf("")
    var address by mutableStateOf("")
    var status by mutableStateOf("Połącz telefon i TV z tą samą siecią")
    var connected by mutableStateOf(false)
    var listening by mutableStateOf(false)
    var language by mutableStateOf("pl-PL")
    var tv by mutableStateOf(JSONObject())
    val found = mutableStateMapOf<String, String>()
}

private val Ink = Color(0xFF0A0A0C)
private val Card = Color(0xFF1C1C1E)
private val Raised = Color(0xFF2C2C2E)
private val Pressed = Color(0xFF3A3A3C)
private val Hairline = Color(0x1FFFFFFF)
private val Label = Color(0xFFF5F5F7)
private val Secondary = Color(0xFF98989F)
private val Tertiary = Color(0xFF5E5E63)
private val Accent = Color(0xFF0A84FF)
private val Live = Color(0xFF30D158)
private val Waiting = Color(0xFFFF9F0A)
private val Danger = Color(0xFFFF453A)

private enum class Sheet { None, Connect, Favorites, Settings, Type }

@Composable
fun RemoteScreen(state: RemoteState, remote: Remote) {
    MaterialTheme(colorScheme = darkColorScheme(primary = Accent, background = Ink, surface = Card, onSurface = Label, surfaceContainer = Card, surfaceContainerHigh = Raised)) {
        var sheet by rememberSaveable { mutableStateOf(Sheet.None) }
        var dpad by rememberSaveable { mutableStateOf(false) }
        LaunchedEffect(Unit) { if (state.host.isBlank()) sheet = Sheet.Connect }
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFF16161A), Ink, Ink)))) {
            Column(Modifier.fillMaxSize().safeDrawingPadding().padding(horizontal = 20.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Header(state, onDevice = { sheet = Sheet.Connect }, onFavorites = { sheet = Sheet.Favorites }, onSettings = { sheet = Sheet.Settings })
                NowPlaying(state.tv)
                Box(Modifier.fillMaxWidth().weight(1f).clip(RoundedCornerShape(32.dp)).background(Brush.verticalGradient(listOf(Color(0xFF2A2A2E), Color(0xFF17171A)))).border(1.dp, Hairline, RoundedCornerShape(32.dp))) {
                    AnimatedContent(dpad, transitionSpec = { fadeIn() togetherWith fadeOut() }, label = "surface") { arrows -> if (arrows) DPad(remote) else Touchpad(remote) }
                    Segmented(dpad, { dpad = it }, Modifier.align(Alignment.TopCenter).padding(top = 14.dp))
                }
                Controls(remote, state.tv.optBoolean("mediaActive"))
                AddressBar(state, remote, onType = { sheet = Sheet.Type })
            }
        }
        if (sheet != Sheet.None) {
            val close = { sheet = Sheet.None }
            ModalBottomSheet(onDismissRequest = close, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = Card, contentColor = Label, scrimColor = Color(0x99000000)) {
                Column(Modifier.fillMaxWidth().navigationBarsPadding().imePadding().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    when (sheet) {
                        Sheet.Connect -> ConnectSheet(state, remote)
                        Sheet.Favorites -> FavoritesSheet(state.tv, remote, close)
                        Sheet.Settings -> SettingsSheet(state, remote)
                        Sheet.Type -> TypeSheet(remote, close)
                        Sheet.None -> Unit
                    }
                }
            }
        }
    }
}

@Composable
private fun Header(state: RemoteState, onDevice: () -> Unit, onFavorites: () -> Unit, onSettings: () -> Unit) {
    val dot by animateColorAsState(if (state.connected) Live else if (state.host.isBlank()) Tertiary else Waiting, label = "dot")
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Pressable(onDevice, Modifier.weight(1f).height(48.dp), RoundedCornerShape(24.dp), Card) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Icon(Icons.Rounded.Tv, null, tint = Label, modifier = Modifier.size(20.dp))
                Column(Modifier.weight(1f)) {
                    Text(if (state.connected) "BB TV" else "Telewizor", color = Label, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                    Text(state.status, color = Secondary, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Box(Modifier.size(8.dp).clip(CircleShape).background(dot))
            }
        }
        RoundButton(Icons.Rounded.Star, "Ulubione", onFavorites, size = 48.dp, background = Card)
        RoundButton(Icons.Rounded.Settings, "Ustawienia", onSettings, size = 48.dp, background = Card)
    }
}

@Composable
private fun NowPlaying(tv: JSONObject) {
    val url = tv.optString("url")
    if (url.isBlank() || url.contains("home.bbtv.invalid")) return
    val host = runCatching { android.net.Uri.parse(url).host.orEmpty().removePrefix("www.") }.getOrDefault("")
    val blocked = tv.optLong("networkBlocked") + tv.optInt("cosmeticBlocked")
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Card).padding(10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Monogram(host.ifBlank { "?" }, 40.dp)
        Column(Modifier.weight(1f)) {
            Text(tv.optString("title").ifBlank { host }, color = Label, fontSize = 15.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(host, color = Secondary, fontSize = 12.sp, maxLines = 1)
        }
        if (blocked > 0) Row(Modifier.clip(RoundedCornerShape(10.dp)).background(Raised).padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.Shield, null, tint = Live, modifier = Modifier.size(14.dp))
            Spacer(Modifier.width(4.dp))
            Text("$blocked", color = Secondary, fontSize = 12.sp)
        }
    }
}

@Composable
private fun Segmented(dpad: Boolean, onChange: (Boolean) -> Unit, modifier: Modifier) {
    Row(modifier.clip(RoundedCornerShape(12.dp)).background(Color(0x66000000)).padding(3.dp)) {
        listOf(false to "Touchpad", true to "Strzałki").forEach { (value, label) ->
            val selected = dpad == value
            Box(Modifier.clip(RoundedCornerShape(9.dp)).background(if (selected) Raised else Color.Transparent).combinedClickable(onClick = { onChange(value) }).padding(horizontal = 14.dp, vertical = 6.dp)) {
                Text(label, color = if (selected) Label else Secondary, fontSize = 13.sp, fontWeight = FontWeight.Medium)
            }
        }
    }
}

@Composable
private fun Touchpad(remote: Remote) {
    val haptic = LocalHapticFeedback.current
    var touch by remember { mutableStateOf<Offset?>(null) }
    var last by remember { mutableStateOf(Offset.Zero) }
    val glow by animateFloatAsState(if (touch != null) 1f else 0f, label = "glow")
    Box(Modifier.fillMaxSize().drawBehind {
        if (glow > 0f) drawCircle(Brush.radialGradient(listOf(Color.White.copy(alpha = 0.12f * glow), Color.Transparent), center = last, radius = 260f), radius = 260f, center = last)
    }.pointerInput(remote) {
        awaitEachGesture {
            val down = awaitFirstDown()
            val started = SystemClock.uptimeMillis()
            touch = down.position; last = down.position
            var travel = 0f; var multi = false; var held = false
            var move = Offset.Zero; var scroll = 0f; var sent = started
            fun flush() {
                if (move != Offset.Zero) { remote.send("move", JSONObject().put("dx", (move.x * 2).coerceIn(-500f, 500f)).put("dy", (move.y * 2).coerceIn(-500f, 500f)), true); move = Offset.Zero }
                if (scroll != 0f) { remote.send("scroll", JSONObject().put("dx", 0).put("dy", (-scroll * 3).coerceIn(-4000f, 4000f)), true); scroll = 0f }
            }
            while (true) {
                val idle = !held && !multi && travel < viewConfiguration.touchSlop
                val event = if (idle) withTimeoutOrNull((650 - (SystemClock.uptimeMillis() - started)).coerceAtLeast(1)) { awaitPointerEvent() } else awaitPointerEvent()
                if (event == null) { held = true; haptic.performHapticFeedback(HapticFeedbackType.LongPress); remote.send("fullscreen"); continue }
                val pressed = event.changes.filter { it.pressed }
                if (pressed.isEmpty()) break
                if (pressed.size > 1) multi = true
                var delta = Offset.Zero
                pressed.forEach { delta += it.position - it.previousPosition }
                delta /= pressed.size.toFloat()
                travel += delta.getDistance()
                if (multi) scroll += delta.y else if (travel >= viewConfiguration.touchSlop) move += delta
                last = pressed[0].position; touch = last
                event.changes.forEach { it.consume() }
                val now = SystemClock.uptimeMillis()
                if (now - sent >= 33) { flush(); sent = now }
            }
            flush()
            touch = null
            if (!held && !multi && travel < viewConfiguration.touchSlop) { haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove); remote.send("click") }
        }
    }, contentAlignment = Alignment.Center) {
        Text("Przesuń, by sterować kursorem\nDotknij: klik · Przytrzymaj: pełny ekran\nDwa palce: przewijanie", color = Tertiary, fontSize = 13.sp, lineHeight = 20.sp, textAlign = TextAlign.Center, modifier = Modifier.graphicsLayer { alpha = 1f - glow })
    }
}

@Composable
private fun DPad(remote: Remote) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Box(Modifier.size(248.dp).clip(CircleShape).background(Color(0xFF232326)).border(1.dp, Hairline, CircleShape)) {
            Arrow(Icons.Rounded.KeyboardArrowUp, 0, -1, remote, Modifier.align(Alignment.TopCenter))
            Arrow(Icons.Rounded.KeyboardArrowDown, 0, 1, remote, Modifier.align(Alignment.BottomCenter))
            Arrow(Icons.Rounded.KeyboardArrowLeft, -1, 0, remote, Modifier.align(Alignment.CenterStart))
            Arrow(Icons.Rounded.KeyboardArrowRight, 1, 0, remote, Modifier.align(Alignment.CenterEnd))
            Pressable({ remote.send("click") }, Modifier.align(Alignment.Center).size(104.dp), CircleShape, Raised, onLong = { remote.send("fullscreen") }) {
                Text("OK", color = Label, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun Arrow(icon: ImageVector, dx: Int, dy: Int, remote: Remote, modifier: Modifier) {
    val haptic = LocalHapticFeedback.current
    var down by remember { mutableStateOf(false) }
    Box(modifier.size(72.dp).pointerInput(remote) {
        detectTapGestures(onPress = {
            down = true
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            coroutineScope {
                val job = launch {
                    remote.send("move", JSONObject().put("dx", dx * 16).put("dy", dy * 16))
                    delay(320)
                    var count = 0
                    while (true) { val speed = (12 + count++ * 2).coerceAtMost(45); remote.send("move", JSONObject().put("dx", dx * speed).put("dy", dy * speed), true); delay(80) }
                }
                tryAwaitRelease()
                job.cancel()
            }
            down = false
        })
    }, contentAlignment = Alignment.Center) {
        Icon(icon, null, tint = if (down) Label else Secondary, modifier = Modifier.size(36.dp))
    }
}

@Composable
private fun Controls(remote: Remote, playing: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                RoundButton(Icons.Rounded.ChevronLeft, "Wstecz", { remote.send("back") })
                RoundButton(Icons.Rounded.ChevronRight, "Dalej", { remote.send("forward") })
                RoundButton(Icons.Rounded.Refresh, "Odśwież", { remote.send("reload") })
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                RoundButton(Icons.Rounded.Home, "Start", { remote.send("home") })
                RoundButton(if (playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, "Odtwarzaj lub pauzuj", { remote.send("media") })
                RoundButton(Icons.Rounded.Fullscreen, "Pełny ekran", { remote.send("fullscreen") })
            }
        }
        Column(Modifier.width(64.dp).height(132.dp).clip(RoundedCornerShape(32.dp)).background(Raised)) {
            VolumeHalf(Icons.Rounded.Add, "Głośniej", Modifier.weight(1f)) { remote.send("volume", JSONObject().put("delta", 1)) }
            Box(Modifier.fillMaxWidth().padding(horizontal = 18.dp).height(1.dp).background(Hairline))
            VolumeHalf(Icons.Rounded.Remove, "Ciszej", Modifier.weight(1f)) { remote.send("volume", JSONObject().put("delta", -1)) }
        }
    }
}

@Composable
private fun VolumeHalf(icon: ImageVector, label: String, modifier: Modifier, onClick: () -> Unit) {
    Pressable(onClick, modifier.fillMaxWidth(), RoundedCornerShape(0.dp), Raised) { Icon(icon, label, tint = Label, modifier = Modifier.size(26.dp)) }
}

@Composable
private fun AddressBar(state: RemoteState, remote: Remote, onType: () -> Unit) {
    val focus = LocalFocusManager.current
    val submit = { remote.go(state.address); focus.clearFocus() }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(Modifier.weight(1f).height(52.dp).clip(RoundedCornerShape(16.dp)).background(Raised).padding(start = 14.dp, end = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.Search, null, tint = Secondary, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            BasicTextField(state.address, { state.address = it }, Modifier.weight(1f).fillMaxHeight(), singleLine = true, textStyle = TextStyle(color = Label, fontSize = 16.sp), cursorBrush = SolidColor(Accent), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Go), keyboardActions = KeyboardActions(onGo = { submit() })) { inner ->
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.CenterStart) {
                    if (state.address.isEmpty()) Text(if (state.listening) "Słucham…" else "Szukaj lub wpisz adres", color = Secondary, fontSize = 16.sp)
                    inner()
                }
            }
            if (state.address.isNotBlank()) RoundButton(Icons.Rounded.ArrowUpward, "Wyślij na TV", submit, size = 40.dp, background = Accent)
            else RoundButton(Icons.Rounded.Mic, "Mikrofon", { remote.voice() }, size = 40.dp, background = if (state.listening) Danger else Raised, tint = if (state.listening) Label else Secondary)
        }
        RoundButton(Icons.Rounded.Keyboard, "Wpisz tekst w polu na TV", onType, size = 52.dp)
    }
}

@Composable
private fun ColumnScope.ConnectSheet(state: RemoteState, remote: Remote) {
    SheetTitle("Połącz z telewizorem", state.status)
    if (state.found.isNotEmpty()) Group {
        state.found.forEach { (host, name) ->
            Row(Modifier.fillMaxWidth().combinedClickable(onClick = { state.host = host }).padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Icon(Icons.Rounded.Tv, null, tint = Accent, modifier = Modifier.size(22.dp))
                Column(Modifier.weight(1f)) { Text(name, color = Label, fontSize = 16.sp); Text(host, color = Secondary, fontSize = 13.sp) }
                if (state.host == host) Box(Modifier.size(8.dp).clip(CircleShape).background(Accent))
            }
        }
    }
    Field(state.host, { state.host = it }, "Adres IP telewizora, np. 192.168.1.10", KeyboardType.Uri)
    Field(state.pin, { if (it.length <= 6 && it.all(Char::isDigit)) state.pin = it }, "PIN z ekranu TV", KeyboardType.NumberPassword)
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        PillButton("Szukaj w sieci", { remote.discover() }, Modifier.weight(1f), Raised)
        PillButton("Połącz", { remote.connect(false) }, Modifier.weight(1f), Raised)
    }
    PillButton("Sparuj", { remote.connect(true) }, Modifier.fillMaxWidth(), Accent)
    Text("PIN i adres widać w prawym górnym rogu telewizora. W emulatorze wpisz 10.0.2.2:18765.", color = Secondary, fontSize = 13.sp)
}

@Composable
private fun ColumnScope.FavoritesSheet(tv: JSONObject, remote: Remote, close: () -> Unit) {
    val items = tv.optJSONArray("bookmarks")
    SheetTitle("Ulubione", "Zakładki zapisane na telewizorze")
    FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(16.dp), maxItemsInEachRow = 4) {
        for (i in 0 until (items?.length() ?: 0)) {
            val item = items!!.getJSONObject(i)
            Favorite(item.optString("title"), item.optString("url"), Modifier.weight(1f), onOpen = { remote.send("open", JSONObject().put("url", item.optString("url"))); close() }, onEarlier = { if (i > 0) remote.send("bookmark_move", JSONObject().put("from", i).put("to", i - 1)) }, onLater = { if (i < items.length() - 1) remote.send("bookmark_move", JSONObject().put("from", i).put("to", i + 1)) }, onDelete = { remote.send("bookmark_delete", JSONObject().put("index", i)) })
        }
        Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
            Pressable({ remote.send("bookmark_add") }, Modifier.size(64.dp), RoundedCornerShape(16.dp), Raised) { Icon(Icons.Rounded.Add, "Dodaj bieżącą stronę", tint = Accent, modifier = Modifier.size(28.dp)) }
            Spacer(Modifier.height(6.dp))
            Text("Dodaj", color = Secondary, fontSize = 12.sp)
        }
    }
    Text("Przytrzymaj kafelek, aby go przesunąć lub usunąć.", color = Secondary, fontSize = 13.sp)
}

@Composable
private fun Favorite(title: String, url: String, modifier: Modifier, onOpen: () -> Unit, onEarlier: () -> Unit, onLater: () -> Unit, onDelete: () -> Unit) {
    var menu by remember { mutableStateOf(false) }
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Box {
            Pressable(onOpen, Modifier.size(64.dp), RoundedCornerShape(16.dp), Raised, onLong = { menu = true }) { Monogram(runCatching { android.net.Uri.parse(url).host.orEmpty().removePrefix("www.") }.getOrDefault(title), 64.dp) }
            DropdownMenu(menu, { menu = false }, containerColor = Raised) {
                DropdownMenuItem({ Text("Przesuń wcześniej") }, { menu = false; onEarlier() })
                DropdownMenuItem({ Text("Przesuń dalej") }, { menu = false; onLater() })
                DropdownMenuItem({ Text("Usuń", color = Danger) }, { menu = false; onDelete() })
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(title, color = Label, fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
    }
}

@Composable
private fun ColumnScope.SettingsSheet(state: RemoteState, remote: Remote) {
    val tv = state.tv
    val host = runCatching { android.net.Uri.parse(tv.optString("url")).host.orEmpty() }.getOrDefault("")
    var template by remember { mutableStateOf(tv.optString("search", "https://www.google.com/search?q={query}")) }
    var language by remember { mutableStateOf(state.language) }
    SheetTitle("Ustawienia", if (state.connected) "Zmiany trafiają od razu na TV" else "Połącz z TV, aby zmieniać ustawienia")
    Caption("Przeglądanie")
    Group { Toggle("Skakanie po linkach", "D-pad przenosi fokus po linkach i polach", tv.optBoolean("linkMode")) { remote.send("link_mode", JSONObject().put("enabled", it)) } }
    Caption("Blokowanie reklam")
    Group {
        Toggle("Wszędzie", null, tv.optBoolean("adblock", true)) { remote.send("adblock", JSONObject().put("scope", "global").put("enabled", it)) }
        Divider()
        Toggle("Na tej stronie", host.ifBlank { null }, tv.optBoolean("siteAdblock", true)) { remote.send("adblock", JSONObject().put("scope", "site").put("enabled", it)) }
    }
    Text("Zablokowano: sieć ${tv.optLong("networkBlocked")} · elementy ${tv.optInt("cosmeticBlocked")}", color = Secondary, fontSize = 13.sp, modifier = Modifier.padding(horizontal = 4.dp))
    Caption("Wyszukiwarka")
    Field(template, { template = it }, "URL z {query}", KeyboardType.Uri)
    PillButton("Zapisz wyszukiwarkę", { remote.search(template) }, Modifier.fillMaxWidth(), Raised)
    Caption("Głos")
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.weight(1f)) { Field(language, { language = it }, "pl-PL", KeyboardType.Text) }
        PillButton("Zapisz", { remote.language(language) }, Modifier.width(110.dp), Raised)
    }
    Caption("Telewizor")
    Group {
        Info("Strona", tv.optString("title").ifBlank { "—" })
        Divider()
        Info("Adres", tv.optString("url").ifBlank { "—" })
        Divider()
        Info("WebView", tv.optString("webView").ifBlank { "—" })
    }
}

@Composable
private fun ColumnScope.TypeSheet(remote: Remote, close: () -> Unit) {
    var text by remember { mutableStateOf("") }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { delay(250); runCatching { focus.requestFocus() } }
    SheetTitle("Wpisz na TV", "Najpierw kliknij pole tekstowe na telewizorze")
    Field(text, { text = it }, "Tekst", KeyboardType.Text, Modifier.focusRequester(focus), ImeAction.Send) { remote.send("type", JSONObject().put("text", text)); close() }
    PillButton("Wyślij", { remote.send("type", JSONObject().put("text", text)); close() }, Modifier.fillMaxWidth(), Accent)
}

@Composable
private fun SheetTitle(title: String, subtitle: String) {
    Column {
        Text(title, color = Label, fontSize = 24.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.4).sp)
        Text(subtitle, color = Secondary, fontSize = 14.sp)
    }
}

@Composable
private fun Caption(text: String) { Text(text.uppercase(), color = Secondary, fontSize = 12.sp, letterSpacing = 0.6.sp, modifier = Modifier.padding(start = 4.dp, top = 4.dp)) }

@Composable
private fun Group(content: @Composable ColumnScope.() -> Unit) { Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Raised), content = content) }

@Composable
private fun Divider() { Box(Modifier.padding(start = 16.dp).fillMaxWidth().height(1.dp).background(Hairline)) }

@Composable
private fun Toggle(title: String, subtitle: String?, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, color = Label, fontSize = 16.sp)
            if (subtitle != null) Text(subtitle, color = Secondary, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Switch(checked, onChange, colors = SwitchDefaults.colors(checkedTrackColor = Live, checkedThumbColor = Color.White, uncheckedTrackColor = Pressed, uncheckedThumbColor = Color.White, uncheckedBorderColor = Color.Transparent))
    }
}

@Composable
private fun Info(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 13.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(label, color = Label, fontSize = 16.sp)
        Text(value, color = Secondary, fontSize = 15.sp, maxLines = 2, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.End, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun Field(value: String, onChange: (String) -> Unit, placeholder: String, type: KeyboardType, modifier: Modifier = Modifier, action: ImeAction = ImeAction.Done, onAction: (() -> Unit)? = null) {
    BasicTextField(value, onChange, modifier.fillMaxWidth().height(52.dp), singleLine = true, textStyle = TextStyle(color = Label, fontSize = 16.sp), cursorBrush = SolidColor(Accent), keyboardOptions = KeyboardOptions(keyboardType = type, imeAction = action), keyboardActions = KeyboardActions(onAny = { onAction?.invoke() ?: defaultKeyboardAction(action) })) { inner ->
        Box(Modifier.fillMaxSize().clip(RoundedCornerShape(14.dp)).background(Raised).padding(horizontal = 16.dp), contentAlignment = Alignment.CenterStart) {
            if (value.isEmpty()) Text(placeholder, color = Tertiary, fontSize = 16.sp, maxLines = 1)
            inner()
        }
    }
}

@Composable
private fun PillButton(label: String, onClick: () -> Unit, modifier: Modifier, background: Color) {
    Pressable(onClick, modifier.height(50.dp), RoundedCornerShape(14.dp), background) { Text(label, color = Label, fontSize = 16.sp, fontWeight = FontWeight.SemiBold) }
}

@Composable
private fun RoundButton(icon: ImageVector, label: String, onClick: () -> Unit, size: Dp = 60.dp, background: Color = Raised, tint: Color = Label) {
    Pressable(onClick, Modifier.size(size), CircleShape, background) { Icon(icon, label, tint = tint, modifier = Modifier.size(size * 0.45f)) }
}

@Composable
private fun Monogram(host: String, size: Dp) {
    val hue = ((host.hashCode() % 360) + 360) % 360
    val base = Color.hsv(hue.toFloat(), 0.55f, 0.75f)
    Box(Modifier.size(size).clip(RoundedCornerShape(size * 0.25f)).background(Brush.linearGradient(listOf(base, Color.hsv(hue.toFloat(), 0.65f, 0.45f)))), contentAlignment = Alignment.Center) {
        Text(host.take(1).uppercase(), color = Color.White, fontSize = (size.value * 0.42f).sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun Pressable(onClick: () -> Unit, modifier: Modifier, shape: Shape, color: Color, onLong: (() -> Unit)? = null, content: @Composable BoxScope.() -> Unit) {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val haptic = LocalHapticFeedback.current
    val scale by animateFloatAsState(if (pressed) 0.93f else 1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium), label = "press")
    val tone by animateColorAsState(if (pressed) lerp(color) else color, label = "tone")
    Box(modifier.graphicsLayer { scaleX = scale; scaleY = scale }.clip(shape).background(tone).combinedClickable(interactionSource = source, indication = null, onLongClick = onLong?.let { long -> { haptic.performHapticFeedback(HapticFeedbackType.LongPress); long() } }, onClick = { haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove); onClick() }), contentAlignment = Alignment.Center, content = content)
}

private fun lerp(color: Color) = Color(color.red + (1f - color.red) * 0.12f, color.green + (1f - color.green) * 0.12f, color.blue + (1f - color.blue) * 0.12f, color.alpha)
