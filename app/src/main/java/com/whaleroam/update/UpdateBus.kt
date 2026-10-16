package com.whaleroam.update

/**
 * 更新下载事件总线：[UpdateDownloadService] 在后台线程发布事件，
 * 界面（CloudDrive 风格更新对话框）注册监听以实时展示下载进度。
 *
 * 注意：事件可能从任意线程回调，UI 方需自行切换到主线程。
 */
object UpdateBus {

    sealed class Event {
        /** pct < 0 表示未知总大小（不确定进度条） */
        data class Progress(val pct: Int, val downloaded: Long, val total: Long) : Event()
        data class Done(val apkPath: String) : Event()
        data class Failed(val msg: String) : Event()
        object Cancelled : Event()
    }

    interface Listener {
        fun onEvent(e: Event)
    }

    private val listeners = mutableListOf<Listener>()

    fun register(l: Listener) {
        synchronized(listeners) { if (!listeners.contains(l)) listeners.add(l) }
    }

    fun unregister(l: Listener) {
        synchronized(listeners) { listeners.remove(l) }
    }

    fun post(e: Event) {
        val snapshot = synchronized(listeners) { listeners.toList() }
        snapshot.forEach { it.onEvent(e) }
    }
}
