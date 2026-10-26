package com.example.lxmusic.data.plugin

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.File
import java.security.MessageDigest

/**
 * 插件仓库：插件源文件、元信息、启用状态都保存在应用私有目录。
 * 不内置任何插件；只能由用户通过设置页导入。
 */
object PluginStore {

    data class PluginInfo(
        val id: String = "",
        val fileName: String = "",
        val name: String = "",
        val version: String = "",
        val author: String = "",
        val description: String = "",
        /** 源通过 inited 上报的平台列表（JSON 数组：key/name/actions/qualities） */
        val platforms: String = "",
        val enabled: Boolean = true,
        val installedAt: Long = 0L,
        val error: String? = null
    )

    private val gson = Gson()
    private const val INDEX_FILE = "plugins_index.json"

    private fun dir(context: Context): File =
        File(context.filesDir, "plugins").apply { mkdirs() }

    private fun indexFile(context: Context): File = File(dir(context), INDEX_FILE)

    private fun load(context: Context): MutableList<PluginInfo> {
        return runCatching {
            val text = indexFile(context).takeIf { it.exists() }?.readText().orEmpty()
            if (text.isBlank()) mutableListOf()
            else gson.fromJson<List<PluginInfo>>(text, object : TypeToken<List<PluginInfo>>() {}.type)
                .toMutableList()
        }.getOrElse { mutableListOf() }
    }

    private fun save(context: Context, list: List<PluginInfo>) {
        runCatching { indexFile(context).writeText(gson.toJson(list)) }
    }

    fun list(context: Context): List<PluginInfo> = load(context)

    fun enabled(context: Context): List<PluginInfo> = load(context).filter { it.enabled && it.error == null }

    fun readSource(context: Context, id: String): String? {
        val file = load(context).firstOrNull { it.id == id }?.fileName ?: return null
        val f = File(dir(context), file)
        return runCatching { f.takeIf { it.exists() }?.readText() }.getOrNull()
    }

    /** 导入并校验插件（加载源 → 等待 inited → 记录元信息与支持平台）。失败返回带 error 的结果，不入库。 */
    fun import(context: Context, displayName: String, sourceCode: String): PluginInfo {
        val now = System.currentTimeMillis()
        val id = "pl_" + sha1("$displayName|$now|${sourceCode.hashCode()}").take(12)
        val fileName = "$id.js"
        val runtime = LxPluginRuntime(id, sourceCode)
        val startResult = try {
            runtime.start()
        } catch (t: Throwable) {
            return PluginInfo(
                id = id,
                fileName = fileName,
                name = displayName,
                installedAt = now,
                error = t.message ?: t.javaClass.simpleName
            )
        } finally {
            runCatching { runtime.close() }
        }
        if (startResult.platforms.isEmpty() && startResult.meta.name.isBlank()) {
            return PluginInfo(
                id = id,
                fileName = fileName,
                name = displayName,
                installedAt = now,
                error = "插件未上报任何可用音源（未收到 inited）"
            )
        }
        val info = PluginInfo(
            id = id,
            fileName = fileName,
            name = startResult.meta.name.takeIf { it.isNotBlank() } ?: displayName,
            version = startResult.meta.version,
            author = startResult.meta.author,
            description = startResult.meta.description,
            platforms = gson.toJson(startResult.platforms),
            enabled = true,
            installedAt = now,
            error = null
        )
        dir(context).resolve(fileName).writeText(sourceCode)
        val list = load(context).apply { add(info) }
        save(context, list)
        return info
    }

    fun setEnabled(context: Context, id: String, enabled: Boolean) {
        val list = load(context).map { if (it.id == id) it.copy(enabled = enabled) else it }
        save(context, list)
        if (!enabled) PluginSourceProvider.close(id)
    }

    fun delete(context: Context, id: String) {
        val list = load(context)
        list.firstOrNull { it.id == id }?.let { info ->
            runCatching { File(dir(context), info.fileName).delete() }
        }
        save(context, list.filterNot { it.id == id })
        PluginSourceProvider.close(id)
    }

    fun updateError(context: Context, id: String, error: String?) {
        save(context, load(context).map { if (it.id == id) it.copy(error = error) else it })
    }

    private fun sha1(value: String): String =
        MessageDigest.getInstance("SHA-1").digest(value.toByteArray())
            .joinToString("") { "%02x".format(it) }
}
