#include "five_by_five_core.h"

#include <jni.h>

#include <algorithm>
#include <array>
#include <cstdint>
#include <memory>
#include <vector>

using cubik555::Pool;
using cubik555::ScoreMode;
using cubik555::WrapperPool;

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
JNIEXPORT jlong JNICALL
Java_com_cubecraft_solver_solver_NativeFiveByFiveKernel_createWrapperPoolNative(
    JNIEnv* env,
    jobject,
    jshortArray flatSetups,
    jshortArray flatUndos,
    jint count
) {
    if (flatSetups == nullptr || flatUndos == nullptr || count <= 0) return 0;
    const jsize expected = count * cubik555::kPermSize;
    if (env->GetArrayLength(flatSetups) != expected ||
        env->GetArrayLength(flatUndos) != expected) {
        return 0;
    }

    std::vector<jshort> setups(expected);
    std::vector<jshort> undos(expected);
    env->GetShortArrayRegion(flatSetups, 0, expected, setups.data());
    env->GetShortArrayRegion(flatUndos, 0, expected, undos.data());

    auto wrappers = std::make_unique<WrapperPool>();
    wrappers->count = count;
    wrappers->setups.resize(expected);
    wrappers->undos.resize(expected);

    std::transform(
        setups.begin(),
        setups.end(),
        wrappers->setups.begin(),
        [](jshort value) { return static_cast<std::uint16_t>(value); }
    );
    std::transform(
        undos.begin(),
        undos.end(),
        wrappers->undos.begin(),
        [](jshort value) { return static_cast<std::uint16_t>(value); }
    );
    return reinterpret_cast<jlong>(wrappers.release());
}

extern "C"
JNIEXPORT void JNICALL
Java_com_cubecraft_solver_solver_NativeFiveByFiveKernel_destroyWrapperPoolNative(
    JNIEnv*,
    jobject,
    jlong handle
) {
    delete reinterpret_cast<WrapperPool*>(handle);
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


extern "C"
JNIEXPORT jlong JNICALL
Java_com_cubecraft_solver_solver_NativeFiveByFiveKernel_findFirstWrappedNative(
    JNIEnv* env,
    jobject,
    jlong poolHandle,
    jlong wrapperHandle,
    jbyteArray stateArray,
    jint mode,
    jint before,
    jboolean requireCenters
) {
    auto* pool = reinterpret_cast<Pool*>(poolHandle);
    auto* wrappers = reinterpret_cast<WrapperPool*>(wrapperHandle);
    if (pool == nullptr || wrappers == nullptr) return -1;

    std::array<std::uint8_t, cubik555::kFacelets> state{};
    if (!readState(env, stateArray, state)) return -1;

    const auto result = cubik555::findFirstImprovingWrapped(
        *pool,
        *wrappers,
        state.data(),
        modeFrom(mode),
        before,
        requireCenters == JNI_TRUE
    );
    if (result.index < 0 || result.wrapper < 0) return -1;

    return (static_cast<jlong>(result.score & 0xFFFF) << 48) |
        (static_cast<jlong>(result.wrapper & 0xFFFFFF) << 24) |
        static_cast<jlong>(result.index & 0xFFFFFF);
}

extern "C"
JNIEXPORT jlong JNICALL
Java_com_cubecraft_solver_solver_NativeFiveByFiveKernel_findBestWrappedNative(
    JNIEnv* env,
    jobject,
    jlong poolHandle,
    jlong wrapperHandle,
    jbyteArray stateArray,
    jint mode,
    jint floor,
    jboolean requireCenters
) {
    auto* pool = reinterpret_cast<Pool*>(poolHandle);
    auto* wrappers = reinterpret_cast<WrapperPool*>(wrapperHandle);
    if (pool == nullptr || wrappers == nullptr) return -1;

    std::array<std::uint8_t, cubik555::kFacelets> state{};
    if (!readState(env, stateArray, state)) return -1;

    const auto result = cubik555::findBestWrapped(
        *pool,
        *wrappers,
        state.data(),
        modeFrom(mode),
        floor,
        requireCenters == JNI_TRUE
    );
    if (result.index < 0 || result.wrapper < 0) return -1;

    return (static_cast<jlong>(result.score & 0xFFFF) << 48) |
        (static_cast<jlong>(result.wrapper & 0xFFFFFF) << 24) |
        static_cast<jlong>(result.index & 0xFFFFFF);
}

extern "C"
JNIEXPORT jintArray JNICALL
Java_com_cubecraft_solver_solver_NativeFiveByFiveKernel_beamSearchNative(
    JNIEnv* env,
    jobject,
    jlong handle,
    jbyteArray stateArray,
    jint mode,
    jint target,
    jint floor,
    jboolean requireCenters,
    jint maxDepth,
    jint beamWidth,
    jint budgetMillis
) {
    auto* pool = reinterpret_cast<Pool*>(handle);
    if (pool == nullptr) return nullptr;

    std::array<std::uint8_t, cubik555::kFacelets> state{};
    if (!readState(env, stateArray, state)) return nullptr;

    const auto path = cubik555::beamSearch(
        *pool,
        state.data(),
        modeFrom(mode),
        target,
        floor,
        requireCenters == JNI_TRUE,
        maxDepth,
        beamWidth,
        budgetMillis
    );
    if (path.empty()) return nullptr;

    auto result = env->NewIntArray(static_cast<jsize>(path.size()));
    if (result == nullptr) return nullptr;
    env->SetIntArrayRegion(
        result,
        0,
        static_cast<jsize>(path.size()),
        reinterpret_cast<const jint*>(path.data())
    );
    return result;
}

extern "C"
JNIEXPORT jintArray JNICALL
Java_com_cubecraft_solver_solver_NativeFiveByFiveKernel_bestFirstSearchNative(
    JNIEnv* env,
    jobject,
    jlong handle,
    jbyteArray stateArray,
    jint mode,
    jint target,
    jint floor,
    jboolean requireCenters,
    jint maxNodes,
    jint budgetMillis
) {
    auto* pool = reinterpret_cast<Pool*>(handle);
    if (pool == nullptr) return nullptr;

    std::array<std::uint8_t, cubik555::kFacelets> state{};
    if (!readState(env, stateArray, state)) return nullptr;

    const auto path = cubik555::bestFirstSearch(
        *pool,
        state.data(),
        modeFrom(mode),
        target,
        floor,
        requireCenters == JNI_TRUE,
        maxNodes,
        budgetMillis
    );
    if (path.empty()) return nullptr;

    auto result = env->NewIntArray(static_cast<jsize>(path.size()));
    if (result == nullptr) return nullptr;
    env->SetIntArrayRegion(
        result,
        0,
        static_cast<jsize>(path.size()),
        reinterpret_cast<const jint*>(path.data())
    );
    return result;
}
