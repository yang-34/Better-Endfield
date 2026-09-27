#pragma once
#include <dlfcn.h>
#include <link.h>
#include <cstring>
#include <android/log.h>
namespace betterendfield {
inline void* OpenLoadedIl2Cpp() {
    if (void* image = dlopen("libil2cpp.so", RTLD_NOLOAD | RTLD_NOW)) return image;
    static int direct_misses = 0;
    if (direct_misses++ % 50 == 0) {
        __android_log_print(ANDROID_LOG_WARN, "BetterEndfield",
            "[il2cpp.open] direct RTLD_NOLOAD miss (attempt %d)", direct_misses);
    }
    void* image = nullptr;
    dl_iterate_phdr([](dl_phdr_info* info, size_t, void* context) {
        const char* path = info->dlpi_name;
        if (!path || !*path) return 0;
        const char* slash = std::strrchr(path, '/');
        const char* name = slash ? slash + 1 : path;
        if (std::strcmp(name, "libil2cpp.so") != 0) return 0;
        auto*& result = *static_cast<void**>(context);
        __android_log_print(ANDROID_LOG_WARN, "BetterEndfield",
            "[il2cpp.open] phdr hit, re-opening: %s", path);
        result = dlopen(path, RTLD_NOLOAD | RTLD_NOW);
        return result ? 1 : 0;
    }, &image);
    static int scan_misses = 0;
    if (image == nullptr && scan_misses++ % 50 == 0) {
        __android_log_print(ANDROID_LOG_WARN, "BetterEndfield",
            "[il2cpp.open] phdr scan found no dlopen-able libil2cpp.so (attempt %d)", scan_misses);
    }
    return image;
}
}
