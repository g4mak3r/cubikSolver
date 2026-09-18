#include "five_by_five_core.h"

#include <jni.h>

#include <algorithm>
#include <cstdint>
#include <memory>
#include <vector>

using cubik555::Pool;
using cubik555::ScoreMode;

namespace {

ScoreMode modeFrom(jint value) {
    return value == 0 ? ScoreMode::Centers : ScoreMode::Edges;
}

const std::uint16_t* readUndo(
    JNIEnv* env,
    jshortArray undoArray,
    std::vector<std::uint16_t>& undo
) {
    if (undoArray == nullptr) return nullptr;
    const jsize count = env->GetArrayLength(undoArray);
    if (count != cubik555::kPermSize) return nullptr;

    std::vector<jshort> temp(count);
    env->GetShortArrayRegion(undoArray, 0, count, temp.data());
    undo.resize(count);
    std::transform(
        temp.begin(),
        temp.end(),
        undo.begin(),
        [](jshort value) { return static_cast<std::uint16_t>(value); }
    );
    return undo.data();
}

bool readState(
    JNIEnv* env,
    jbyteArray stateArray,
    std::array<std::uint8_t, cubik555::kFacelets>& state
) {
    if (stateArray == nullptr ||
        env->GetArrayLength(stateArray) != cubik555::kFacelets) {
        return false;
    }
    env->GetByteArrayRegion(
        stateArray,
        0,
        cubik555::kFacelets,
        reinterpret_cast<jbyte*>(state.data())
    );
    return true;
}

}

extern "C"
JNIEXPORT jlong JNICALL
Java_com_cubecraft_solver_solver_NativeFiveByFiveKernel_createPoolNative(
    JNIEnv* env,
    jobject,
    jshortArray flatPerms,
    jint count
) {
    if (flatPerms == nullptr || count <= 0) return 0;

    const jsize size = env->GetArrayLength(flatPerms);
    if (size != count * cubik555::kPermSize) return 0;

    std::vector<jshort> source(size);
    env->GetShortArrayRegion(flatPerms, 0, size, source.data());

    auto pool = std::make_unique<Pool>();
    pool->count = count;
    pool->perms.resize(size);
    std::transform(
        source.begin(),
        source.end(),
        pool->perms.begin(),
        [](jshort value) { return static_cast<std::uint16_t>(value); }
    );
    return reinterpret_cast<jlong>(pool.release());
}

extern "C"
JNIEXPORT void JNICALL
Java_com_cubecraft_solver_solver_NativeFiveByFiveKernel_destroyPoolNative(
    JNIEnv*,
    jobject,
    jlong handle
) {
    delete reinterpret_cast<Pool*>(handle);
}

extern "C"
JNIEXPORT jint JNICALL
Java_com_cubecraft_solver_solver_NativeFiveByFiveKernel_findFirstImprovingNative(
    JNIEnv* env,
    jobject,
    jlong handle,
    jbyteArray stateArray,
    jshortArray undoArray,
    jint mode,
    jint before,
    jboolean requireCenters
) {
    auto* pool = reinterpret_cast<Pool*>(handle);
    if (pool == nullptr) return -1;

    std::array<std::uint8_t, cubik555::kFacelets> state{};
    if (!readState(env, stateArray, state)) return -1;

    std::vector<std::uint16_t> undo;
    const auto* undoPtr = readUndo(env, undoArray, undo);

    return cubik555::findFirstImproving(
        *pool,
        state.data(),
        undoPtr,
        modeFrom(mode),
        before,
        requireCenters == JNI_TRUE
    );
}

extern "C"
JNIEXPORT jlong JNICALL
Java_com_cubecraft_solver_solver_NativeFiveByFiveKernel_findBestNative(
    JNIEnv* env,
    jobject,
    jlong handle,
    jbyteArray stateArray,
    jshortArray undoArray,
    jint mode,
    jint floor,
    jboolean requireCenters
) {
    auto* pool = reinterpret_cast<Pool*>(handle);
    if (pool == nullptr) return -1;

    std::array<std::uint8_t, cubik555::kFacelets> state{};
    if (!readState(env, stateArray, state)) return -1;

    std::vector<std::uint16_t> undo;
    const auto* undoPtr = readUndo(env, undoArray, undo);

    const auto result = cubik555::findBest(
        *pool,
        state.data(),
        undoPtr,
        modeFrom(mode),
        floor,
        requireCenters == JNI_TRUE
    );

    if (result.index < 0) return -1;
    return (static_cast<jlong>(result.score) << 32) |
        static_cast<std::uint32_t>(result.index);
}
