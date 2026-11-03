package com.kinetic.app.model

import android.graphics.Matrix
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.kinetic.app.data.AppSettings
import java.util.UUID
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt

/** One open composition. A new instance is always empty. */
class EditorState(val settings: AppSettings) {
    var resolution by mutableIntStateOf(settings.resolution)
    var duration by mutableFloatStateOf(settings.compSeconds.toFloat())
    var layers by mutableStateOf<List<Layer>>(emptyList())
        private set
    var time by mutableFloatStateOf(0f)
    var playing by mutableStateOf(false)
    var selectedId by mutableStateOf<String?>(null)
    var selectedProp by mutableStateOf<PropId?>(null)
    var selectedKey by mutableIntStateOf(-1)
    var canUndo by mutableStateOf(false)
        private set
    var canRedo by mutableStateOf(false)
        private set

    private val undoStack = ArrayDeque<List<Layer>>()
    private val redoStack = ArrayDeque<List<Layer>>()

    val fps: Int get() = settings.fps
    val frame: Float get() = 1f / settings.fps
    val selected: Layer? get() = layers.firstOrNull { it.id == selectedId }

    // ---------- history ----------
    fun beginEdit() {
        undoStack.addLast(layers)
        if (undoStack.size > 100) undoStack.removeFirst()
        redoStack.clear()
        sync()
    }

    private fun sync() {
        canUndo = undoStack.isNotEmpty()
        canRedo = redoStack.isNotEmpty()
    }

    fun undo() {
        if (undoStack.isEmpty()) return
        redoStack.addLast(layers)
        layers = undoStack.removeLast()
        fixSelection()
        sync()
    }

    fun redo() {
        if (redoStack.isEmpty()) return
        undoStack.addLast(layers)
        layers = redoStack.removeLast()
        fixSelection()
        sync()
    }

    private fun fixSelection() {
        if (layers.none { it.id == selectedId }) clearSelection()
    }

    private fun clearSelection() {
        selectedId = null; selectedProp = null; selectedKey = -1
    }

    private fun commit(f: (List<Layer>) -> List<Layer>) { beginEdit(); layers = f(layers) }
    private fun live(f: (List<Layer>) -> List<Layer>) { layers = f(layers) }

    fun updateLayer(id: String, push: Boolean = true, f: (Layer) -> Layer) {
        val fn: (List<Layer>) -> List<Layer> = { ls -> ls.map { if (it.id == id) f(it) else it } }
        if (push) commit(fn) else live(fn)
    }

    // ---------- selection / time ----------
    fun select(id: String?) {
        selectedId = id; selectedProp = null; selectedKey = -1
    }

    fun selectProp(id: PropId?) { selectedProp = id; selectedKey = -1 }

    fun selectKey(prop: PropId, index: Int, seekTo: Float?) {
        selectedProp = prop; selectedKey = index
        if (seekTo != null) time = seekTo.coerceIn(0f, duration)
    }

    fun seek(t: Float) { time = t.coerceIn(0f, duration) }

    fun seekSnap(t: Float, windowSec: Float) {
        val raw = t.coerceIn(0f, duration)
        if (settings.snap) {
            val times = selected?.props?.values?.flatMap { p -> p.keys.map { it.t } } ?: emptyList()
            val near = times.minByOrNull { abs(it - raw) }
            if (near != null && abs(near - raw) <= windowSec) { time = near; return }
        }
        time = (raw * fps).roundToInt() / fps.toFloat()
    }

    // ---------- layers ----------
    private fun remaining(): Float = (duration - time).coerceAtLeast(1f)
    private fun nextName(base: String): String = "$base ${layers.count { it.name.startsWith(base) } + 1}"

    private fun addLayer(l: Layer) {
        commit { listOf(l) + it }
        select(l.id)
    }

    fun addNull() = addLayer(newLayer(LayerType.NULL, nextName("Null"), time, remaining(), 100f, 100f))
    fun addAdjustment() =
        addLayer(newLayer(LayerType.ADJUSTMENT, nextName("Adjustment Layer"), time, remaining(), COMP_W, COMP_H))
    fun addText() = addLayer(
        newLayer(LayerType.TEXT, "Text", time, remaining(), 800f, 200f, text = "Text", color = 0xFFFFFFFF.toInt())
    )
    fun addShape() = addLayer(newLayer(LayerType.SHAPE, nextName("Shape"), time, remaining(), 400f, 400f))

    fun addMedia(type: LayerType, name: String, uri: String, w: Int, h: Int, length: Float?) {
        val lw: Float
        val lh: Float
        if (type == LayerType.AUDIO) {
            lw = 0f; lh = 0f
        } else if (w <= 0 || h <= 0) {
            lw = COMP_W; lh = COMP_H
        } else {
            val s = minOf(COMP_W / w, COMP_H / h)
            lw = w * s; lh = h * s
        }
        val isTimed = type == LayerType.VIDEO || type == LayerType.AUDIO
        val dur = if (isTimed) (length ?: 5f).coerceAtLeast(0.1f) else remaining()
        val src = if (isTimed) dur else Float.POSITIVE_INFINITY
        addLayer(newLayer(type, name, time, dur, lw, lh, uri, src))
    }

    fun deleteLayer(id: String) {
        commit { ls -> ls.filter { it.id != id }.map { if (it.parentId == id) it.copy(parentId = null) else it } }
        fixSelection()
    }

    fun duplicate(id: String) {
        val l = layers.firstOrNull { it.id == id } ?: return
        val c = l.copy(id = UUID.randomUUID().toString(), name = l.name + " copy")
        commit { ls -> ls.flatMap { if (it.id == id) listOf(c, it) else listOf(it) } }
        select(c.id)
    }

    fun splitSelected() {
        val l = selected ?: return
        val t = time
        if (t <= l.start + frame || t >= l.end - frame) return
        val a = l.copy(duration = t - l.start)
        val b = l.copy(
            id = UUID.randomUUID().toString(), start = t,
            duration = l.end - t, srcOffset = l.srcOffset + (t - l.start)
        )
        commit { ls -> ls.flatMap { if (it.id == l.id) listOf(b, a) else listOf(it) } }
        select(b.id)
    }

    fun toggleVisible(id: String) = updateLayer(id) { it.copy(visible = !it.visible) }

    fun toggleScaleLink(id: String) = updateLayer(id) { it.copy(scaleLinked = !it.scaleLinked) }

    fun renameLayer(id: String, name: String) = updateLayer(id) { it.copy(name = name) }

    fun setText(id: String, text: String) = updateLayer(id) { it.copy(text = text, name = text.ifBlank { "Text" }) }

    /** Reorder without snapshot (call beginEdit() at gesture start). dir = +1 moves down, -1 up. */
    fun moveLayer(id: String, dir: Int) {
        val i = layers.indexOfFirst { it.id == id }
        val j = i + dir
        if (i < 0 || j !in layers.indices) return
        val m = layers.toMutableList()
        val tmp = m[i]; m[i] = m[j]; m[j] = tmp
        layers = m
    }

    fun moveClip(id: String, dt: Float) =
        updateLayer(id, push = false) { it.copy(start = max(0f, it.start + dt)) }

    fun trimRight(id: String, dt: Float) = updateLayer(id, push = false) { l ->
        l.copy(duration = (l.duration + dt).coerceIn(0.1f, max(0.1f, l.srcLength - l.srcOffset)))
    }

    fun trimLeft(id: String, dt: Float) = updateLayer(id, push = false) { l ->
        val lo = max(-l.srcOffset, -l.start)
        val hi = l.duration - 0.1f
        val d = dt.coerceIn(lo, max(lo, hi))
        l.copy(start = l.start + d, duration = l.duration - d, srcOffset = l.srcOffset + d)
    }

    /** Parenting keeps the child where it is on screen (position only, if position isn't keyframed). */
    fun setParent(childId: String, parentId: String?) {
        val byId = layers.associateBy { it.id }
        commit { ls ->
            ls.map { l ->
                if (l.id != childId) l else {
                    var nl = l.copy(parentId = parentId)
                    val px = l.p(PropId.POS_X)
                    val py = l.p(PropId.POS_Y)
                    if (!px.animated && !py.animated) {
                        val a = floatArrayOf(
                            l.p(PropId.ANCHOR_X).valueAt(time), l.p(PropId.ANCHOR_Y).valueAt(time)
                        )
                        layerMatrix(l, time, byId).mapPoints(a)
                        val pl = parentId?.let { byId[it] }
                        if (pl != null) {
                            val pm = layerMatrix(pl, time, byId)
                            pm.preTranslate(pl.p(PropId.ANCHOR_X).valueAt(time), pl.p(PropId.ANCHOR_Y).valueAt(time))
                            val inv = Matrix()
                            if (pm.invert(inv)) inv.mapPoints(a)
                        }
                        nl = nl.copy(
                            props = l.props + mapOf(
                                PropId.POS_X to px.copy(base = a[0]),
                                PropId.POS_Y to py.copy(base = a[1])
                            )
                        )
                    }
                    nl
                }
            }
        }
    }

    // ---------- properties / keyframes ----------
    /** Live change (no snapshot). Call beginEdit() once at the start of a scrub. */
    fun setPropValue(layerId: String, id: PropId, value: Float) {
        val v = if (id == PropId.OPACITY) value.coerceIn(0f, 100f) else value
        val t = time
        val eps = frame / 2f
        updateLayer(layerId, push = false) { l ->
            var next = l.props + (id to l.p(id).withValueAt(t, v, eps))
            if (l.scaleLinked && (id == PropId.SCALE_X || id == PropId.SCALE_Y)) {
                val other = if (id == PropId.SCALE_X) PropId.SCALE_Y else PropId.SCALE_X
                next = next + (other to l.p(other).withValueAt(t, v, eps))
            }
            l.copy(props = next)
        }
    }

    fun toggleStopwatch(layerId: String, id: PropId) {
        val t = time
        updateLayer(layerId) { l ->
            val p = l.p(id)
            val np = if (p.animated) Prop(p.valueAt(t)) else Prop(p.base, listOf(Keyframe(t, p.base)))
            l.copy(props = l.props + (id to np))
        }
        selectedKey = -1
    }

    fun toggleKeyAtTime(layerId: String, id: PropId) {
        val t = time
        val eps = frame / 2f
        updateLayer(layerId) { l ->
            val p = l.p(id)
            val idx = p.keys.indexOfFirst { abs(it.t - t) <= eps }
            val np = if (idx >= 0) {
                val nk = p.keys.filterIndexed { i, _ -> i != idx }
                if (nk.isEmpty()) Prop(p.keys[idx].v) else p.copy(keys = nk)
            } else {
                p.copy(keys = (p.keys + Keyframe(t, p.valueAt(t))).sortedBy { it.t })
            }
            l.copy(props = l.props + (id to np))
        }
        selectedKey = -1
    }

    fun jumpKey(layerId: String, id: PropId, dir: Int) {
        val p = layers.firstOrNull { it.id == layerId }?.p(id) ?: return
        val eps = frame / 2f
        val target = if (dir > 0) p.keys.firstOrNull { it.t > time + eps } else p.keys.lastOrNull { it.t < time - eps }
        if (target != null) selectKey(id, p.keys.indexOf(target), target.t)
    }

    fun replaceKey(layerId: String, id: PropId, index: Int, key: Keyframe, push: Boolean) {
        updateLayer(layerId, push) { l ->
            val p = l.p(id)
            if (index !in p.keys.indices) l
            else l.copy(props = l.props + (id to p.copy(keys = p.keys.toMutableList().also { it[index] = key })))
        }
    }

    fun deleteKey(layerId: String, id: PropId, index: Int) {
        updateLayer(layerId) { l ->
            val p = l.p(id)
            if (index !in p.keys.indices) l else {
                val nk = p.keys.filterIndexed { i, _ -> i != index }
                val np = if (nk.isEmpty()) Prop(p.keys[index].v) else p.copy(keys = nk)
                l.copy(props = l.props + (id to np))
            }
        }
        selectedKey = -1
    }
}
