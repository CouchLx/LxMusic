package com.example.lxmusic.data.plugin

import android.util.Log
import com.quickjs.JSContext
import com.quickjs.QuickJS

/**
 * QuickJS 微任务泵。
 *
 * taoweiji/quickjs-android 只暴露了 JS_Eval，没有暴露 JS_ExecutePendingJob，
 * 导致 Promise.then / async-await 永远不会执行。这里通过 JNI（dlopen libquickjs.so +
 * dlsym JS_ExecutePendingJob）读取引擎内部指针并主动驱动任务队列。
 */
object QuickJsPump {

    private const val TAG = "LxQuickJsPump"

    @Volatile
    private var available: Boolean? = null

    private val runtimeField by lazy {
        runCatching {
            QuickJS::class.java.getDeclaredField("runtimePtr").apply { isAccessible = true }
        }.getOrNull()
    }
    private val contextField by lazy {
        runCatching {
            JSContext::class.java.getDeclaredField("contextPtr").apply { isAccessible = true }
        }.getOrNull()
    }

    init {
        available = runCatching {
            System.loadLibrary("quickjspump")
            true
        }.getOrElse {
            Log.w(TAG, "native pump 加载失败: ${it.message}")
            false
        }
    }

    private external fun nativePump(runtimePtr: Long, contextPtr: Long): Int

    /** 驱动 Promise/async 队列；必须在 JS 线程（事件队列线程）调用 */
    fun pump(quickJs: QuickJS, context: JSContext): Int {
        if (available != true) return 0
        val rtField = runtimeField ?: return 0
        val ctxField = contextField ?: return 0
        return runCatching {
            nativePump(rtField.getLong(quickJs), ctxField.getLong(context))
        }.getOrElse {
            Log.w(TAG, "pump 失败: ${it.message}")
            0
        }
    }
}
