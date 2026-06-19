#include <jni.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <dlfcn.h>
#include <elf.h>
#include <link.h>
#include <sys/mman.h>
#include <unistd.h>
#include <android/log.h>

/* Architecture-specific ELF relocation types */
#if defined(__aarch64__)
  #define R_JUMP_SLOT R_AARCH64_JUMP_SLOT
#elif defined(__arm__)
  #define R_JUMP_SLOT R_ARM_JUMP_SLOT
#elif defined(__x86_64__)
  #define R_JUMP_SLOT R_X86_64_JUMP_SLOT
#elif defined(__i386__)
  #define R_JUMP_SLOT R_386_JMP_SLOT
#endif

#ifndef ELF64_R_SYM
  #define ELF64_R_SYM(info) ((info) >> 32)
#endif
#ifndef ELF64_R_TYPE
  #define ELF64_R_TYPE(info) ((info) & 0xffffffffL)
#endif

#define TAG "DexInterceptor_Native"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)

typedef void (*log_func_t)(const char*, const char*, long, const char*);
typedef void (*log_ret_func_t)(const char*, const char*, long, long);

static JavaVM *g_jvm = NULL;
static jclass g_bridge_class = NULL;

static void set_native_call_log(const char *lib, const char *func, long addr, const char *args) {
    if (!g_jvm || !g_bridge_class) return;
    JNIEnv *env;
    int attached = 0;
    if ((*g_jvm)->GetEnv(g_jvm, (void**)&env, JNI_VERSION_1_6) != JNI_OK) {
        if ((*g_jvm)->AttachCurrentThread(g_jvm, &env, NULL) != JNI_OK) return;
        attached = 1;
    }
    jclass cls = g_bridge_class;
    jmethodID mid = (*env)->GetStaticMethodID(env, cls, "onNativeCall",
        "(Ljava/lang/String;Ljava/lang/String;JLjava/lang/String;)V");
    if (mid) {
        jstring jlib = (*env)->NewStringUTF(env, lib);
        jstring jfunc = (*env)->NewStringUTF(env, func);
        jstring jargs = (*env)->NewStringUTF(env, args ? args : "");
        (*env)->CallStaticVoidMethod(env, cls, mid, jlib, jfunc, (jlong)addr, jargs);
        (*env)->DeleteLocalRef(env, jlib);
        (*env)->DeleteLocalRef(env, jfunc);
        (*env)->DeleteLocalRef(env, jargs);
    }
    if (attached) (*g_jvm)->DetachCurrentThread(g_jvm);
}

static void set_native_ret_log(const char *lib, const char *func, long retval, long elapsed) {
    if (!g_jvm || !g_bridge_class) return;
    JNIEnv *env;
    int attached = 0;
    if ((*g_jvm)->GetEnv(g_jvm, (void**)&env, JNI_VERSION_1_6) != JNI_OK) {
        if ((*g_jvm)->AttachCurrentThread(g_jvm, &env, NULL) != JNI_OK) return;
        attached = 1;
    }
    jclass cls = g_bridge_class;
    jmethodID mid = (*env)->GetStaticMethodID(env, cls, "onNativeReturn",
        "(Ljava/lang/String;Ljava/lang/String;JJ)V");
    if (mid) {
        jstring jlib = (*env)->NewStringUTF(env, lib);
        jstring jfunc = (*env)->NewStringUTF(env, func);
        (*env)->CallStaticVoidMethod(env, cls, mid, jlib, jfunc, (jlong)retval, (jlong)elapsed);
        (*env)->DeleteLocalRef(env, jlib);
        (*env)->DeleteLocalRef(env, jfunc);
    }
    if (attached) (*g_jvm)->DetachCurrentThread(g_jvm);
}

typedef struct hooked_func {
    char lib_name[256];
    char func_name[256];
    void *original;
    void *hook;
    struct hooked_func *next;
} hooked_func_t;

static hooked_func_t *g_hooked_list = NULL;

static int make_writable(void *addr, size_t len) {
    long page = sysconf(_SC_PAGESIZE);
    void *aligned = (void*)((long)addr & ~(page - 1));
    size_t alloc_len = len + ((long)addr - (long)aligned) + page;
    return mprotect(aligned, alloc_len, PROT_READ | PROT_WRITE | PROT_EXEC);
}

static int hook_plt_entry(const char *lib_path, const char *func_name, void *new_func, void **old_func) {
    void *handle = dlopen(lib_path, RTLD_NOW | RTLD_NOLOAD);
    if (!handle) handle = dlopen(lib_path, RTLD_NOW);
    if (!handle) {
        LOGE("dlopen failed for %s: %s", lib_path, dlerror());
        return -1;
    }

    void *target = dlsym(handle, func_name);
    if (!target) {
        LOGE("dlsym failed for %s in %s: %s", func_name, lib_path, dlerror());
        dlclose(handle);
        return -1;
    }

    *old_func = target;

    Dl_info info;
    if (!dladdr(target, &info)) {
        dlclose(handle);
        return -1;
    }

    ElfW(Ehdr) *ehdr = (ElfW(Ehdr)*)info.dli_fbase;
    ElfW(Phdr) *phdr = (ElfW(Phdr)*)((char*)ehdr + ehdr->e_phoff);

    ElfW(Dyn) *dynamic = NULL;
    for (int i = 0; i < ehdr->e_phnum; i++) {
        if (phdr[i].p_type == PT_DYNAMIC) {
            dynamic = (ElfW(Dyn)*)((char*)ehdr + phdr[i].p_vaddr);
            break;
        }
    }
    if (!dynamic) { dlclose(handle); return -1; }

    ElfW(Word) rela_size = 0;
    ElfW(Rela) *rela = NULL;
    ElfW(Word) jmprel_size = 0;
    ElfW(Rela) *jmprel = NULL;
    ElfW(Sym) *symtab = NULL;
    const char *strtab = NULL;

    for (ElfW(Dyn) *d = dynamic; d->d_tag != DT_NULL; d++) {
        switch (d->d_tag) {
            case DT_JMPREL: jmprel = (ElfW(Rela)*)d->d_un.d_ptr; break;
            case DT_PLTRELSZ: jmprel_size = d->d_un.d_val; break;
            case DT_RELA: rela = (ElfW(Rela)*)d->d_un.d_ptr; break;
            case DT_RELASZ: rela_size = d->d_un.d_val; break;
            case DT_SYMTAB: symtab = (ElfW(Sym)*)d->d_un.d_ptr; break;
            case DT_STRTAB: strtab = (const char*)d->d_un.d_ptr; break;
        }
    }

    if (!jmprel || !symtab || !strtab) { dlclose(handle); return -1; }

    for (size_t i = 0; i < jmprel_size / sizeof(ElfW(Rela)); i++) {
        ElfW(Rela) *r = &jmprel[i];
        if (ELF64_R_TYPE(r->r_info) != R_JUMP_SLOT) continue;

        size_t sym_idx = ELF64_R_SYM(r->r_info);
        const char *name = strtab + symtab[sym_idx].st_name;

        if (strcmp(name, func_name) == 0) {
            void **got_entry = (void**)((char*)ehdr + r->r_offset);
            if (make_writable(got_entry, sizeof(void*)) < 0) {
                dlclose(handle);
                return -1;
            }
            *got_entry = new_func;
            LOGI("Hooked PLT: %s -> %s in %s", func_name, func_name, lib_path);
            dlclose(handle);
            return 0;
        }
    }

    dlclose(handle);
    return -1;
}

#define MAX_FUNC_LEN 256
#define MAX_FUNCS 512

typedef struct {
    char name[MAX_FUNC_LEN];
    void *addr;
    void *original;
} func_hook_t;

static func_hook_t g_func_hooks[MAX_FUNCS];
static int g_func_hook_count = 0;

JNIEXPORT jint JNICALL JNI_OnLoad(JavaVM *vm, void *reserved) {
    g_jvm = vm;
    return JNI_VERSION_1_6;
}

JNIEXPORT void JNICALL
Java_com_yuuxi_interceptor_NativeBridge_nativeInit(JNIEnv *env, jclass clazz) {
    g_bridge_class = (*env)->NewGlobalRef(env, clazz);
    LOGI("NativeBridge initialized");
}

JNIEXPORT jlongArray JNICALL
Java_com_yuuxi_interceptor_NativeBridge_nativeGetExportedFunctions(JNIEnv *env, jclass clazz, jstring libName) {
    const char *name = (*env)->GetStringUTFChars(env, libName, NULL);

    void *handle = dlopen(name, RTLD_NOW);
    if (!handle) {
        LOGE("dlopen failed for %s: %s", name, dlerror());
        (*env)->ReleaseStringUTFChars(env, libName, name);
        return NULL;
    }

    Dl_info info;
    void *sym = dlsym(handle, "JNI_OnLoad");
    if (!sym) {
        dlclose(handle);
        (*env)->ReleaseStringUTFChars(env, libName, name);
        return NULL;
    }
    dladdr(sym, &info);

    ElfW(Ehdr) *ehdr = (ElfW(Ehdr)*)info.dli_fbase;
    ElfW(Shdr) *shdr = (ElfW(Shdr)*)((char*)ehdr + ehdr->e_shoff);
    const char *shstrtab = (char*)ehdr + shdr[ehdr->e_shstrndx].sh_offset;

    int func_count = 0;
    jlong addrs[MAX_FUNCS];

    for (int i = 0; i < ehdr->e_shnum; i++) {
        if (shdr[i].sh_type != SHT_SYMTAB && shdr[i].sh_type != SHT_DYNSYM) continue;

        ElfW(Sym) *syms = (ElfW(Sym)*)((char*)ehdr + shdr[i].sh_offset);
        int nsyms = shdr[i].sh_size / sizeof(ElfW(Sym));
        const char *strtab = (char*)ehdr + shdr[shdr[i].sh_link].sh_offset;

        for (int j = 0; j < nsyms && func_count < MAX_FUNCS; j++) {
            if (syms[j].st_shndx == SHN_UNDEF) continue;
            if (syms[j].st_value == 0) continue;
            int type = ELF64_ST_TYPE(syms[j].st_info);
            if (type != STT_FUNC) continue;

            const char *sname = strtab + syms[j].st_name;
            if (sname && sname[0] != '\0') {
                addrs[func_count++] = (jlong)((char*)ehdr + syms[j].st_value);
                LOGI("Exported func: %s @ 0x%lx", sname, (unsigned long)addrs[func_count-1]);
            }
        }
    }

    dlclose(handle);
    (*env)->ReleaseStringUTFChars(env, libName, name);

    jlongArray result = (*env)->NewLongArray(env, func_count);
    if (result && func_count > 0) {
        (*env)->SetLongArrayRegion(env, result, 0, func_count, addrs);
    }
    return result;
}

JNIEXPORT jint JNICALL
Java_com_yuuxi_interceptor_NativeBridge_nativeHookFunction(JNIEnv *env, jclass clazz,
        jstring libName, jlong funcAddr) {
    if (g_func_hook_count >= MAX_FUNCS) return -1;

    const char *lib_name = (*env)->GetStringUTFChars(env, libName, NULL);
    void *target = (void*)funcAddr;

    Dl_info info;
    if (!dladdr(target, &info)) {
        (*env)->ReleaseStringUTFChars(env, libName, lib_name);
        return -1;
    }

    const char *func_name = info.dli_sname ? info.dli_sname : "unknown";

    func_hook_t *hook = &g_func_hooks[g_func_hook_count];
    strncpy(hook->name, func_name, MAX_FUNC_LEN - 1);
    hook->addr = target;
    hook->original = NULL;
    g_func_hook_count++;

    LOGI("Registered native hook: %s in %s @ %p", func_name, lib_name, target);

    (*env)->ReleaseStringUTFChars(env, libName, lib_name);
    return 0;
}

JNIEXPORT void JNICALL
Java_com_yuuxi_interceptor_NativeBridge_nativeLogCall(JNIEnv *env, jclass clazz,
        jstring libName, jstring funcName, jlong addr, jstring args) {
    const char *lib = (*env)->GetStringUTFChars(env, libName, NULL);
    const char *func = (*env)->GetStringUTFChars(env, funcName, NULL);
    const char *a = (*env)->GetStringUTFChars(env, args, NULL);

    set_native_call_log(lib, func, (long)addr, a);

    (*env)->ReleaseStringUTFChars(env, libName, lib);
    (*env)->ReleaseStringUTFChars(env, funcName, func);
    (*env)->ReleaseStringUTFChars(env, args, a);
}

JNIEXPORT void JNICALL
Java_com_yuuxi_interceptor_NativeBridge_nativeLogReturn(JNIEnv *env, jclass clazz,
        jstring libName, jstring funcName, jlong retval, jlong elapsed) {
    const char *lib = (*env)->GetStringUTFChars(env, libName, NULL);
    const char *func = (*env)->GetStringUTFChars(env, funcName, NULL);

    set_native_ret_log(lib, func, (long)retval, (long)elapsed);

    (*env)->ReleaseStringUTFChars(env, libName, lib);
    (*env)->ReleaseStringUTFChars(env, funcName, func);
}
