#include <jni.h>
#include <string>
#include <fstream>
#include <sstream>
#include <fcntl.h>
#include <unistd.h>
#include <sys/uio.h>
#include <android/log.h>

#define LOG_TAG "TacoStrapNative"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

static int g_target_pid = -1;

uintptr_t get_module_base(int pid, const char* module_name) {
    std::string maps_path = "/proc/" + std::to_string(pid) + "/maps";
    std::ifstream maps(maps_path);
    std::string line;
    while (std::getline(maps, line)) {
        if (line.find(module_name) != std::string::npos) {
            std::stringstream ss(line);
            std::string address;
            std::getline(ss, address, '-');
            return std::stoull(address, nullptr, 16);
        }
    }
    return 0;
}

bool write_process_memory(int pid, uintptr_t address, void* buffer, size_t size) {
    if (pid <= 0 || address == 0) return false;
    struct iovec local_iov = { buffer, size };
    struct iovec remote_iov = { reinterpret_cast<void*>(address), size };
    ssize_t written = process_vm_writev(pid, &local_iov, 1, &remote_iov, 1, 0);
    return written == static_cast<ssize_t>(size);
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_example_flagmanager_NativeMemoryBridge_nativeInit(JNIEnv* env, jobject thiz, jint pid) {
    g_target_pid = pid;
    uintptr_t base = get_module_base(pid, "libroblox.so");
    if (base == 0) {
        LOGE("Failed to locate libroblox.so for PID %d", pid);
        return JNI_FALSE;
    }
    LOGI("Successfully initialized PID %d. libroblox.so Base: 0x%lx", pid, base);
    return JNI_TRUE;
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_example_flagmanager_NativeMemoryBridge_nativeSetMaxFPS(JNIEnv* env, jobject thiz, jlong fps_offset, jdouble max_fps) {
    if (g_target_pid <= 0 || fps_offset == 0) return JNI_FALSE;

    uintptr_t base = get_module_base(g_target_pid, "libroblox.so");
    if (base == 0) return JNI_FALSE;

    uintptr_t target_address = base + static_cast<uintptr_t>(fps_offset);
    int fps_value = static_cast<int>(max_fps);

    bool success = write_process_memory(g_target_pid, target_address, &fps_value, sizeof(fps_value));
    return success ? JNI_TRUE : JNI_FALSE;
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_example_flagmanager_NativeMemoryBridge_nativeSetWalkspeed(JNIEnv* env, jobject thiz, jlong humanoid_address, jlong walkspeed_offset, jfloat speed) {
    if (g_target_pid <= 0 || humanoid_address == 0 || walkspeed_offset == 0) return JNI_FALSE;

    uintptr_t target_address = static_cast<uintptr_t>(humanoid_address) + static_cast<uintptr_t>(walkspeed_offset);
    bool success = write_process_memory(g_target_pid, target_address, &speed, sizeof(speed));
    return success ? JNI_TRUE : JNI_FALSE;
}

