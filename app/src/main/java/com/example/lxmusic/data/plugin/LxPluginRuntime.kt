package com.example.lxmusic.data.plugin

import android.util.Log
import com.google.gson.Gson
import com.quickjs.JSArray
import com.quickjs.JSContext
import com.quickjs.JSValue
import com.quickjs.JavaCallback
import com.quickjs.QuickJS
import com.quickjs.plugin.ConsolePlugin
import com.quickjs.plugin.SetTimeoutPlugin
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.Closeable
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * 落雪音乐（lx-music）JS 自定义源运行时 —— 按真实事件协议实现。
 *
 * 宿主提供：lx = { EVENT_NAMES, request, on, send, env, version, currentScriptInfo, utils }
 * 源通过 lx.on(EVENT_NAMES.request, handler) 注册解析逻辑，通过 send(EVENT_NAMES.inited, {sources})
 * 上报支持的平台/音质/动作。调用方用 request(source, action, info) 取播放地址/歌词/封面。
 *
 * 兼容：若源没有注册事件而是导出 module.exports.api（早期/简化格式），自动包装成 request 处理器。
 */
class LxPluginRuntime(
    private val pluginId: String,
    private val sourceCode: String
) : Closeable {

    data class Platform(
        val key: String,
        val name: String,
        val actions: List<String>,
        val qualities: List<String>
    )

    data class Meta(
        val name: String,
        val version: String,
        val author: String,
        val description: String,
        val homepage: String
    )

    data class StartResult(
        val meta: Meta,
        val platforms: List<Platform>
    )

    companion object {
        private const val TAG = "LxPlugin"
        private const val CALL_TIMEOUT_MS = 35_000L
        private const val INIT_TIMEOUT_MS = 15_000L
    }

    private val gson = Gson()
    private val pendingCalls = ConcurrentHashMap<String, CompletableFuture<Pair<String?, String?>>>()
    private val callSeq = AtomicInteger()
    private lateinit var quickJs: QuickJS
    private lateinit var context: JSContext

    @Volatile
    private var initedJson: String? = null
    private val initedLatch = CountDownLatch(1)

    @Volatile
    private var started = false
    private var startResult: StartResult? = null

    /** 最近一次 request 失败原因（供播放失败提示） */
    @Volatile
    var lastError: String? = null
        private set

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()
    private val httpExecutor = Executors.newCachedThreadPool()

    fun start(): StartResult {
        startResult?.let { return it }
        if (!started) {
            quickJs = QuickJS.createRuntimeWithEventQueue()
            val initLatch = CountDownLatch(1)
            val initError = arrayOfNulls<Throwable>(1)
            quickJs.postEventQueue {
                try {
                    context = quickJs.createContext()
                    context.addPlugin(ConsolePlugin())
                    context.addPlugin(SetTimeoutPlugin())
                    installHostBridge()
                    installPreambles()
                    installPluginModule()
                    started = true
                } catch (t: Throwable) {
                    initError[0] = t
                } finally {
                    initLatch.countDown()
                }
            }
            if (!initLatch.await(INIT_TIMEOUT_MS, TimeUnit.MILLISECONDS)) {
                throw IllegalStateException("插件初始化超时")
            }
            initError[0]?.let { throw IllegalStateException("插件初始化失败: ${it.message}", it) }
        }

        // 等待源上报 inited（部分源会先做网络探测，留足时间）
        initedLatch.await(INIT_TIMEOUT_MS, TimeUnit.MILLISECONDS)

        val header = parseMeta(sourceCode)
        val moduleMeta = evalString(
            "JSON.stringify({name:(globalThis.__lx_module&&globalThis.__lx_module.name)||''," +
                "version:(globalThis.__lx_module&&globalThis.__lx_module.version)||''," +
                "author:(globalThis.__lx_module&&globalThis.__lx_module.author)||''," +
                "description:(globalThis.__lx_module&&globalThis.__lx_module.description)||''})"
        )?.let { runCatching { JSONObject(it) }.getOrNull() }
        val meta = Meta(
            name = header.name.takeIf { it.isNotBlank() }
                ?: moduleMeta?.optString("name")?.takeIf { it.isNotBlank() }
                ?: pluginId,
            version = header.version.ifBlank { moduleMeta?.optString("version").orEmpty() },
            author = header.author.ifBlank { moduleMeta?.optString("author").orEmpty() },
            description = header.description.ifBlank { moduleMeta?.optString("description").orEmpty() },
            homepage = header.homepage
        )
        val platforms = parsePlatforms(initedJson)
        val result = StartResult(meta, platforms)
        startResult = result
        return result
    }

    private fun evalString(script: String): String? = runCatching {
        val latch = CountDownLatch(1)
        val out = arrayOfNulls<String>(1)
        quickJs.postEventQueue {
            runCatching {
                out[0] = context.executeStringScript(script, "eval")
                QuickJsPump.pump(quickJs, context)
            }
            latch.countDown()
        }
        latch.await(5, TimeUnit.SECONDS)
        out[0]
    }.getOrNull()

    /** 调用源的 request 处理器：action = musicUrl / lyric / pic。返回结果 JSON（字符串/对象/null） */
    fun request(source: String, action: String, infoJson: String, timeoutMs: Long = CALL_TIMEOUT_MS): String? {
        if (!started) start()
        val id = callSeq.incrementAndGet().toString()
        val future = CompletableFuture<Pair<String?, String?>>()
        pendingCalls[id] = future
        quickJs.postEventQueue {
            runCatching {
                context.executeVoidScript(
                    "__lx_do_request(${gson.toJson(source)}, ${gson.toJson(action)}, ${gson.toJson(infoJson)}, ${gson.toJson(id)})",
                    "request"
                )
                QuickJsPump.pump(quickJs, context)
            }.onFailure {
                pendingCalls.remove(id)?.complete(null to (it.message ?: "调用异常"))
            }
        }
        return try {
            val (json, err) = future.get(timeoutMs, TimeUnit.MILLISECONDS)
            if (err != null) {
                lastError = err
                Log.w(TAG, "[$pluginId] $source/$action 失败: $err")
                null
            } else {
                lastError = null
                json
            }
        } catch (e: Exception) {
            lastError = "解析超时（${timeoutMs}ms）"
            Log.w(TAG, "[$pluginId] $source/$action 超时/异常: ${e.message}")
            null
        } finally {
            pendingCalls.remove(id)
        }
    }

    /** 兼容旧式（module.exports.api.search 等）调用，仅供测试/简化插件使用 */
    fun callApi(method: String, args: List<Any?>): String? {
        if (!started) start()
        val id = callSeq.incrementAndGet().toString()
        val future = CompletableFuture<Pair<String?, String?>>()
        pendingCalls[id] = future
        val argsJson = gson.toJson(args)
        quickJs.postEventQueue {
            runCatching {
                context.executeVoidScript(
                    "__lx_call_api(${gson.toJson(method)}, ${gson.toJson(argsJson)}, ${gson.toJson(id)})",
                    "call"
                )
                QuickJsPump.pump(quickJs, context)
            }.onFailure {
                pendingCalls.remove(id)?.complete(null to (it.message ?: "调用异常"))
            }
        }
        return try {
            val (json, err) = future.get(CALL_TIMEOUT_MS, TimeUnit.MILLISECONDS)
            if (err != null) {
                Log.w(TAG, "[$pluginId] $method 失败: $err")
                null
            } else json
        } catch (e: Exception) {
            Log.w(TAG, "[$pluginId] $method 超时/异常: ${e.message}")
            null
        } finally {
            pendingCalls.remove(id)
        }
    }

    private fun installHostBridge() {
        // __lx_http(id, method, url, headersJson, body)
        context.registerJavaMethod(JavaCallback { _, args ->
            try {
                val id = args.getString(0)
                val method = args.getString(1) ?: "GET"
                val url = args.getString(2)
                val headersJson = optString(args, 3)
                val body = optString(args, 4)
                doHttp(id, method, url, headersJson, body)
            } catch (t: Throwable) {
                Log.w(TAG, "lx.request 参数错误: ${t.message}")
            }
            null
        }, "__lx_http")

        // __lx_done(callId, json, err)
        context.registerJavaMethod(JavaCallback { _, args ->
            val id = args.getString(0)
            val json = optString(args, 1)
            val err = optString(args, 2)
            pendingCalls.remove(id)?.complete(json to err)
            null
        }, "__lx_done")

        // __lx_send(event, json) —— 源 -> 宿主，主要捕获 inited
        context.registerJavaMethod(JavaCallback { _, args ->
            val event = args.getString(0) ?: ""
            val json = optString(args, 1)
            if (event == "inited") {
                initedJson = json
                initedLatch.countDown()
            } else if (event == "updateAlert") {
                Log.d(TAG, "[$pluginId] 更新提示: $json")
            }
            null
        }, "__lx_send")

        // 字符串/编码工具
        context.registerJavaMethod(JavaCallback { _, a -> digestHex("MD5", a.getString(0)) }, "__lx_md5")
        context.registerJavaMethod(JavaCallback { _, a -> digestHex("SHA-1", a.getString(0)) }, "__lx_sha1")
        context.registerJavaMethod(JavaCallback { _, a -> digestHex("SHA-256", a.getString(0)) }, "__lx_sha256")
        context.registerJavaMethod(JavaCallback { _, a -> hmacHex("HmacSHA256", a.getString(0), a.getString(1)) }, "__lx_hmac_sha256")
        context.registerJavaMethod(JavaCallback { _, a ->
            val bytes = ByteArray(a.getInteger(0).coerceIn(1, 1024)).also { SecureRandom().nextBytes(it) }
            Base64.getEncoder().encodeToString(bytes)
        }, "__lx_random_b64")
        context.registerJavaMethod(JavaCallback { _, a ->
            // from(str, encoding) -> base64 内部表示
            val text = a.getString(0) ?: ""
            val enc = (optString(a, 1) ?: "utf-8").lowercase()
            val bytes = when (enc) {
                "hex" -> hexToBytes(text)
                "base64" -> runCatching { Base64.getDecoder().decode(text) }.getOrElse { ByteArray(0) }
                "latin1", "binary" -> text.toByteArray(Charsets.ISO_8859_1)
                else -> text.toByteArray(Charsets.UTF_8)
            }
            Base64.getEncoder().encodeToString(bytes)
        }, "__lx_buf_from")
        context.registerJavaMethod(JavaCallback { _, a ->
            val b64 = a.getString(0) ?: ""
            val enc = (optString(a, 1) ?: "utf-8").lowercase()
            val bytes = runCatching { Base64.getDecoder().decode(b64) }.getOrElse { ByteArray(0) }
            when (enc) {
                "hex" -> bytes.joinToString("") { "%02x".format(it) }
                "base64" -> b64
                "latin1", "binary" -> String(bytes, Charsets.ISO_8859_1)
                else -> String(bytes, Charsets.UTF_8)
            }
        }, "__lx_buf_to_string")
        context.registerJavaMethod(JavaCallback { _, a ->
            // aesEncrypt(b64Data, mode, key, iv) -> base64
            aes(a, encrypt = true)
        }, "__lx_aes_encrypt")
        context.registerJavaMethod(JavaCallback { _, a ->
            aes(a, encrypt = false)
        }, "__lx_aes_decrypt")
    }

    private fun optString(args: JSArray, index: Int): String? {
        return if (args.getType(index) == JSValue.TYPE.NULL || args.getType(index) == JSValue.TYPE.UNDEFINED) null
        else args.getString(index)
    }

    private fun aes(args: JSArray, encrypt: Boolean): String? {
        return try {
            val dataB64 = args.getString(0) ?: return null
            val mode = (args.getString(1) ?: "aes-128-cbc").lowercase()
            val keyB64 = args.getString(2) ?: return null
            val ivB64 = optString(args, 3)
            val data = Base64.getDecoder().decode(dataB64)
            val key = Base64.getDecoder().decode(keyB64)
            val iv = ivB64?.let { Base64.getDecoder().decode(it) }
            val transformation = if (mode.contains("ecb")) "AES/ECB/PKCS5Padding" else "AES/CBC/PKCS5Padding"
            val cipher = Cipher.getInstance(transformation)
            val keySpec = SecretKeySpec(key, "AES")
            if (transformation.contains("CBC")) {
                cipher.init(
                    if (encrypt) Cipher.ENCRYPT_MODE else Cipher.DECRYPT_MODE,
                    keySpec,
                    IvParameterSpec(iv ?: ByteArray(16))
                )
            } else {
                cipher.init(if (encrypt) Cipher.ENCRYPT_MODE else Cipher.DECRYPT_MODE, keySpec)
            }
            Base64.getEncoder().encodeToString(cipher.doFinal(data))
        } catch (t: Throwable) {
            Log.w(TAG, "aes 失败: ${t.message}")
            null
        }
    }

    private fun installPreambles() {
        val rawScript = gson.toJson(sourceCode)
        val preamble = """
            globalThis.__lx_handlers = {};
            globalThis.__lx_pending = {};
            globalThis.__lx_reqSeq = 0;
            globalThis.__lx_raw_script = $rawScript;

            function __lx_makeBuf(b64, len) {
              return {
                __b64: b64,
                length: len,
                toString: function(enc) { return __lx_buf_to_string(b64, enc || 'utf-8'); },
                toString64: function() { return b64; },
                slice: function(a, b) {
                  var s = __lx_buf_to_string(b64, 'latin1');
                  var part = s.slice(a, b === undefined ? undefined : b);
                  return __lx_makeBuf(__lx_buf_from(part, 'latin1'), part.length);
                }
              };
            }

            globalThis.lx = {
              EVENT_NAMES: { inited: 'inited', request: 'request', updateAlert: 'updateAlert' },
              env: 'mobile',
              version: '2.0.0',
              currentScriptInfo: {
                name: '', description: '', version: '', author: '', homepage: '', rawScript: __lx_raw_script
              },
              on: function(event, handler) { __lx_handlers[event] = handler; },
              send: function(event, data) {
                try {
                  if (event === 'inited') globalThis.__lx_inited_sent = true;
                  __lx_send(String(event), JSON.stringify(data === undefined ? null : data));
                } catch (e) {}
              },
              request: function(url, options, callback) {
                var id = ++__lx_reqSeq;
                __lx_pending[id] = callback;
                var opts = options || {};
                __lx_http(
                  String(id),
                  String(opts.method || 'GET').toUpperCase(),
                  String(url),
                  JSON.stringify(opts.headers || {}),
                  (opts.body === undefined || opts.body === null) ? null : String(opts.body)
                );
              },
              utils: {
                crypto: {
                  md5: function(s) { return __lx_md5(String(s)); },
                  sha1: function(s) { return __lx_sha1(String(s)); },
                  sha256: function(s) { return __lx_sha256(String(s)); },
                  hmacSha256: function(s, key) { return __lx_hmac_sha256(String(s), String(key)); },
                  randomBytes: function(n) {
                    var b64 = __lx_random_b64(Number(n) || 16);
                    return __lx_makeBuf(b64, Number(n) || 16);
                  },
                  aesEncrypt: function(data, mode, key, iv) {
                    return __lx_makeBuf(__lx_aes_encrypt(__lx_buf64(data), mode, __lx_buf64(key), iv ? __lx_buf64(iv) : null), 0);
                  },
                  aesDecrypt: function(data, mode, key, iv) {
                    return __lx_makeBuf(__lx_aes_decrypt(__lx_buf64(data), mode, __lx_buf64(key), iv ? __lx_buf64(iv) : null), 0);
                  }
                },
                buffer: {
                  from: function(data, enc) {
                    if (data && data.__b64) return __lx_makeBuf(data.__b64, data.length);
                    var b64 = __lx_buf_from(String(data), enc || 'utf-8');
                    return __lx_makeBuf(b64, String(data).length);
                  },
                  bufToString: function(buf, enc) {
                    if (!buf) return '';
                    if (buf.__b64) return __lx_buf_to_string(buf.__b64, enc || 'utf-8');
                    return String(buf);
                  }
                }
              }
            };

            globalThis.__lx_buf64 = function(v) {
              if (v && v.__b64) return v.__b64;
              return __lx_buf_from(String(v), 'utf-8');
            };

            globalThis.__lx_on_http = function(id, errStr, respJson) {
              var cb = __lx_pending[id];
              if (cb) { delete __lx_pending[id]; }
              if (!cb) return;
              if (errStr) { try { cb(new Error(errStr), null); } catch (e) {} return; }
              var resp;
              try { resp = JSON.parse(respJson); } catch (e) { try { cb(new Error('bad response'), null); } catch (e2) {} return; }
              try { cb(null, resp); } catch (e) {}
            };

            globalThis.__lx_do_request = function(source, action, infoJson, callId) {
              try {
                var h = __lx_handlers['request'];
                if (typeof h !== 'function') { __lx_done(callId, null, '插件未注册 request 事件'); return; }
                var info = JSON.parse(infoJson || '{}');
                Promise.resolve(h({ source: source, action: action, info: info })).then(
                  function(r) { __lx_done(callId, JSON.stringify(r === undefined ? null : r), null); },
                  function(e) {
                    var msg = e && (e.message || e);
                    __lx_done(callId, null, String(msg) + (e && e.stack ? ' | ' + String(e.stack).split('\n').slice(0, 3).join(' ') : ''));
                  }
                );
              } catch (e) {
                var m = e && (e.message || e);
                __lx_done(callId, null, String(m) + (e && e.stack ? ' | ' + String(e.stack).split('\n').slice(0, 3).join(' ') : ''));
              }
            };

            globalThis.__lx_call_api = function(method, argsJson, callId) {
              try {
                var api = (__lx_module && __lx_module.api) ? __lx_module.api : __lx_module;
                var fn = api ? api[method] : null;
                if (typeof fn !== 'function') { __lx_done(callId, null, '插件未实现方法: ' + method); return; }
                Promise.resolve(fn.apply(api, JSON.parse(argsJson || '[]'))).then(
                  function(r) { __lx_done(callId, JSON.stringify(r === undefined ? null : r), null); },
                  function(e) { __lx_done(callId, null, String((e && (e.stack || e.message)) || e)); }
                );
              } catch (e) { __lx_done(callId, null, String(e)); }
            };

            // Node 兼容垫片（按需最小实现）
            globalThis.Buffer = {
              from: function(d, e) { return lx.utils.buffer.from(d, e); },
              isBuffer: function(v) { return !!(v && v.__b64); },
              alloc: function(n) { return lx.utils.buffer.from('', 'utf-8'); }
            };
            globalThis.btoa = function(s) { return __lx_buf_to_string(__lx_buf_from(String(s), 'utf-8'), 'base64'); };
            globalThis.atob = function(s) { return __lx_buf_to_string(__lx_buf_from(String(s), 'base64'), 'utf-8'); };
            globalThis.__lx_require = function(name) {
              if (name === 'crypto') {
                return {
                  createHash: function() { return { update: function(s) { this._v = String(s); return this; }, digest: function() { return __lx_md5(this._v || ''); } }; },
                  randomBytes: function(n) { return lx.utils.crypto.randomBytes(n); }
                };
              }
              if (name === 'buffer') return { Buffer: globalThis.Buffer };
              if (name === 'url' || name === 'querystring') return {};
              if (name === 'zlib') return {};
              throw new Error('不支持的模块: ' + name);
            };
        """.trimIndent()
        context.executeVoidScript(preamble, "preamble")

        // 注入脚本元信息（部分源会读 currentScriptInfo.version）
        val meta = parseMeta(sourceCode)
        context.executeVoidScript(
            """
            lx.currentScriptInfo = {
              name: ${gson.toJson(meta.name)},
              description: ${gson.toJson(meta.description)},
              version: ${gson.toJson(meta.version)},
              author: ${gson.toJson(meta.author)},
              homepage: ${gson.toJson(meta.homepage)},
              rawScript: __lx_raw_script
            };
            """.trimIndent(),
            "script-info"
        )
    }

    private fun installPluginModule() {
        // CommonJS 语义 + 捕获顶层异常；源通常在此阶段注册 lx.on / send(inited)
        val wrapper = """
            globalThis.module = { exports: {} };
            globalThis.exports = globalThis.module.exports;
            (function(module, exports, require) {
            $sourceCode
            })(globalThis.module, globalThis.exports, globalThis.__lx_require);
            globalThis.__lx_module = globalThis.module.exports;
        """.trimIndent()
        context.executeVoidScript(wrapper, "plugin")
        QuickJsPump.pump(quickJs, context)

        // 若源没有注册事件但有 module.exports.api，包装成 request 处理器（兼容简化格式），
        // 并合成一个 inited，避免等待超时。
        context.executeVoidScript(
            """
            (function() {
              var mod = globalThis.__lx_module;
              if (typeof __lx_handlers['request'] !== 'function') {
                if (!mod) return;
                var api = mod.api || mod;
                if (!api) return;
                __lx_handlers['request'] = function(payload) {
                  var action = payload.action;
                  var info = payload.info || {};
                  if (action === 'musicUrl') {
                    if (typeof api.getMusicUrl === 'function') return api.getMusicUrl(info.musicInfo, info.type);
                    if (typeof api.musicUrl === 'function') return api.musicUrl(info.musicInfo, info.type);
                  }
                  if (action === 'lyric') {
                    if (typeof api.getLyric === 'function') return api.getLyric(info.musicInfo);
                    if (typeof api.lyric === 'function') return api.lyric(info.musicInfo);
                  }
                  if (action === 'pic') {
                    if (typeof api.getPic === 'function') return api.getPic(info.musicInfo);
                    if (typeof api.pic === 'function') return api.pic(info.musicInfo);
                  }
                  return null;
                };
              }
              if (!globalThis.__lx_inited_sent && typeof __lx_handlers['request'] === 'function') {
                var name = (mod && mod.name) || '插件';
                __lx_send('inited', JSON.stringify({
                  sources: { legacy: { name: name, type: 'music', actions: ['musicUrl','lyric','pic'], qualitys: [] } }
                }));
              }
            })();
            """.trimIndent(),
            "legacy-bridge"
        )
        QuickJsPump.pump(quickJs, context)
    }

    private fun parseMeta(code: String): Meta {
        fun field(key: String): String {
            val m = Regex("""@$key\s+([^\r\n*]+)""").find(code) ?: return ""
            return m.groupValues[1].trim().replace(Regex("""\*/\s*$"""), "").trim()
        }
        val name = field("name")
        return Meta(
            name = name,
            version = field("version"),
            author = field("author"),
            description = field("description"),
            homepage = field("homepage")
        )
    }

    private fun parsePlatforms(json: String?): List<Platform> {
        if (json.isNullOrBlank()) return emptyList()
        return runCatching {
            val root = JSONObject(json)
            val sources = root.optJSONObject("sources") ?: return emptyList()
            sources.keys().asSequence().map { key ->
                val obj = sources.optJSONObject(key) ?: JSONObject()
                val actions = obj.optJSONArray("actions")?.let { arr ->
                    (0 until arr.length()).map { arr.optString(it) }
                } ?: emptyList()
                val qualities = obj.optJSONArray("qualitys")?.let { arr ->
                    (0 until arr.length()).map { arr.optString(it) }
                } ?: emptyList()
                Platform(
                    key = key,
                    name = obj.optString("name", key),
                    actions = actions,
                    qualities = qualities
                )
            }.toList()
        }.getOrElse { emptyList() }
    }

    private fun doHttp(id: String, method: String, url: String, headersJson: String?, body: String?) {
        httpExecutor.execute {
            var err: String? = null
            var respJson = "{}"
            try {
                val builder = Request.Builder().url(url)
                runCatching {
                    if (!headersJson.isNullOrBlank()) {
                        val obj = JSONObject(headersJson)
                        obj.keys().forEach { key -> builder.header(key, obj.optString(key, "")) }
                    }
                }
                val requestBody = when {
                    body != null && method != "GET" && method != "HEAD" ->
                        body.toRequestBody("application/json".toMediaTypeOrNull())
                    else -> null
                }
                builder.method(method, requestBody)
                httpClient.newCall(builder.build()).execute().use { response ->
                    val bodyText = response.body?.string() ?: ""
                    Log.d(
                        TAG,
                        "[$pluginId] HTTP ${response.code} ${method} ${url.take(120)} body=${bodyText.take(240)}"
                    )
                    val resp = JSONObject()
                    resp.put("statusCode", response.code)
                    resp.put("body", bodyText)
                    val headers = JSONObject()
                    response.headers.forEach { (k, v) -> headers.put(k, v) }
                    resp.put("headers", headers)
                    respJson = resp.toString()
                }
            } catch (t: Throwable) {
                err = t.message ?: t.javaClass.simpleName
            }
            val errLit = if (err == null) "null" else gson.toJson(err)
            val respLit = gson.toJson(respJson)
            quickJs.postEventQueue {
                runCatching {
                    context.executeVoidScript("__lx_on_http(${gson.toJson(id)}, $errLit, $respLit)", "http-cb")
                    QuickJsPump.pump(quickJs, context)
                }
            }
        }
    }

    private fun digestHex(algorithm: String, value: String): String {
        val digest = MessageDigest.getInstance(algorithm).digest(value.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }

    private fun hmacHex(algorithm: String, value: String, key: String): String {
        val mac = Mac.getInstance(algorithm)
        mac.init(SecretKeySpec(key.toByteArray(Charsets.UTF_8), algorithm))
        return mac.doFinal(value.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
    }

    private fun hexToBytes(hex: String): ByteArray {
        val clean = hex.trim()
        if (clean.length % 2 != 0) return ByteArray(0)
        return ByteArray(clean.length / 2) { i ->
            clean.substring(i * 2, i * 2 + 2).toInt(16).toByte()
        }
    }

    override fun close() {
        runCatching { quickJs.close() }
        runCatching { httpExecutor.shutdownNow() }
        Log.d(TAG, "[$pluginId] runtime closed")
    }
}
