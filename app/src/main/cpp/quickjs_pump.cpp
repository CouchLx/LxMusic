// QuickJS 微任务（Promise/async）泵：taoweiji 的 quickjs-android 未暴露
// JS_ExecutePendingJob，本文件通过 dlopen 从 libquickjs.so 动态解析该符号，
// 在每次执行脚本/回调后驱动 Promise 队列，否则 async/await 永不落地。
#include <jni.h>
#include <dlfcn.h>
#include <android/log.h>

#define LOG_TAG "LxQuickJsPump"
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

typedef int (*js_execute_pending_job_t)(void *rt, void **pctx);

extern "C" JNIEXPORT jint JNICALL
Java_com_example_lxmusic_data_plugin_QuickJsPump_nativePump(JNIEnv *env, jclass clazz,
                                                            jlong runtime_ptr,
                                                            jlong context_ptr) {
    static js_execute_pending_job_t execute_pending_job = nullptr;
    static bool lookup_failed = false;

    if (execute_pending_job == nullptr) {
        if (lookup_failed) return -3;
        void *lib = dlopen("libquickjs.so", RTLD_NOW | RTLD_GLOBAL);
        if (lib == nullptr) {
            LOGE("dlopen libquickjs.so failed: %s", dlerror());
            lookup_failed = true;
            return -1;
        }
        execute_pending_job = (js_execute_pending_job_t) dlsym(lib, "JS_ExecutePendingJob");
        if (execute_pending_job == nullptr) {
            LOGE("dlsym JS_ExecutePendingJob failed: %s", dlerror());
            lookup_failed = true;
            return -2;
        }
    }

    void *ctx = reinterpret_cast<void *>(context_ptr);
    int executed = 0;
    for (int i = 0; i < 10000; ++i) {
        int ret = execute_pending_job(reinterpret_cast<void *>(runtime_ptr), &ctx);
        if (ret <= 0) break; // 0 = 队列空, <0 = 异常
        executed++;
    }
    return executed;
}
