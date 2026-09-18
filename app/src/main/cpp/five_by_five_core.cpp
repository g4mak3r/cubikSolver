#include "five_by_five_core.h"

#include <algorithm>
#include <array>
#include <map>
#include <tuple>
#include <utility>
#include <vector>

namespace cubik555 {
namespace {

struct Sticker {
    int x;
    int y;
    int z;
    int face;
    int index;
};

struct EdgeSlot {
    std::array<int, 3> a{};
    std::array<int, 3> b{};
};

Sticker stickerAt(int face, int row, int col) {
    constexpr int n = 5;
    switch (face) {
        case 0: return {col, n - 1, row, face, face * 25 + row * 5 + col};
        case 1: return {n - 1, n - 1 - row, n - 1 - col, face, face * 25 + row * 5 + col};
        case 2: return {col, n - 1 - row, n - 1, face, face * 25 + row * 5 + col};
        case 3: return {col, 0, n - 1 - row, face, face * 25 + row * 5 + col};
        case 4: return {0, n - 1 - row, col, face, face * 25 + row * 5 + col};
        default: return {n - 1 - col, n - 1 - row, 0, face, face * 25 + row * 5 + col};
    }
}

const std::array<EdgeSlot, 12>& edgeSlots() {
    static const std::array<EdgeSlot, 12> slots = [] {
        using Coord = std::tuple<int, int, int>;
        struct Piece {
            int variable;
            int a;
            int b;
        };

        std::map<Coord, std::vector<Sticker>> cubies;
        for (int face = 0; face < 6; ++face) {
            for (int row = 0; row < 5; ++row) {
                for (int col = 0; col < 5; ++col) {
                    const auto s = stickerAt(face, row, col);
                    const int boundaries =
                        (s.x == 0 || s.x == 4) +
                        (s.y == 0 || s.y == 4) +
                        (s.z == 0 || s.z == 4);
                    if (boundaries == 2) {
                        cubies[{s.x, s.y, s.z}].push_back(s);
                    }
                }
            }
        }

        std::map<std::pair<int, int>, std::vector<Piece>> grouped;
        for (auto& [coord, stickers] : cubies) {
            if (stickers.size() != 2) continue;
            std::sort(
                stickers.begin(),
                stickers.end(),
                [](const Sticker& left, const Sticker& right) {
                    return left.face < right.face;
                }
            );
            const auto [x, y, z] = coord;
            const int variable =
                (x != 0 && x != 4) ? x :
                (y != 0 && y != 4) ? y : z;
            grouped[{stickers[0].face, stickers[1].face}].push_back(
                {variable, stickers[0].index, stickers[1].index}
            );
        }

        std::array<EdgeSlot, 12> result{};
        int slot = 0;
        for (auto& [faces, pieces] : grouped) {
            if (pieces.size() != 3 || slot >= 12) continue;
            std::sort(
                pieces.begin(),
                pieces.end(),
                [](const Piece& left, const Piece& right) {
                    return left.variable < right.variable;
                }
            );
            for (int i = 0; i < 3; ++i) {
                result[slot].a[i] = pieces[i].a;
                result[slot].b[i] = pieces[i].b;
            }
            ++slot;
        }
        return result;
    }();
    return slots;
}

int edgeQuality(const std::uint8_t* state, const EdgeSlot& slot) {
    const auto middleA = state[slot.a[1]];
    const auto middleB = state[slot.b[1]];
    int value = 0;
    for (const int wing : {0, 2}) {
        value += state[slot.a[wing]] == middleA &&
                 state[slot.b[wing]] == middleB;
    }
    return value;
}

void finishCandidate(
    const std::uint8_t* state,
    const std::uint16_t* op,
    const std::uint16_t* undo,
    std::uint8_t* scratch,
    std::uint8_t* result
) {
    applyPerm(state, op, scratch);
    if (undo == nullptr) {
        std::copy_n(scratch, kFacelets, result);
    } else {
        applyPerm(scratch, undo, result);
    }
}

}

int centerScore(const std::uint8_t* state) {
    int total = 0;
    for (int face = 0; face < 6; ++face) {
        const int base = face * 25;
        const auto target = state[base + 12];
        for (int row = 1; row <= 3; ++row) {
            for (int col = 1; col <= 3; ++col) {
                total += state[base + row * 5 + col] == target;
            }
        }
    }
    return total;
}

bool centersSolved(const std::uint8_t* state) {
    return centerScore(state) == 54;
}

int edgeQualityScore(const std::uint8_t* state) {
    int total = 0;
    for (const auto& slot : edgeSlots()) {
        total += edgeQuality(state, slot);
    }
    return total;
}

int edgeSearchScore(const std::uint8_t* state) {
    int wings = 0;
    int complete = 0;
    for (const auto& slot : edgeSlots()) {
        const int value = edgeQuality(state, slot);
        wings += value;
        complete += value == 2;
    }
    return wings * 16 + complete;
}

void applyPerm(
    const std::uint8_t* source,
    const std::uint16_t* perm,
    std::uint8_t* target
) {
    for (int i = 0; i < kFacelets; ++i) {
        target[i] = source[perm[i]];
    }
}

int score(const std::uint8_t* state, ScoreMode mode) {
    return mode == ScoreMode::Centers
        ? centerScore(state)
        : edgeSearchScore(state);
}

int findFirstImproving(
    const Pool& pool,
    const std::uint8_t* state,
    const std::uint16_t* undo,
    ScoreMode mode,
    int before,
    bool requireCenters
) {
    alignas(64) std::array<std::uint8_t, kFacelets> scratch{};
    alignas(64) std::array<std::uint8_t, kFacelets> result{};

    for (int i = 0; i < pool.count; ++i) {
        finishCandidate(
            state,
            pool.perm(i),
            undo,
            scratch.data(),
            result.data()
        );
        if (requireCenters && !centersSolved(result.data())) continue;
        if (score(result.data(), mode) > before) return i;
    }
    return -1;
}

EvalResult findBest(
    const Pool& pool,
    const std::uint8_t* state,
    const std::uint16_t* undo,
    ScoreMode mode,
    int floor,
    bool requireCenters
) {
    alignas(64) std::array<std::uint8_t, kFacelets> scratch{};
    alignas(64) std::array<std::uint8_t, kFacelets> result{};

    EvalResult best;
    for (int i = 0; i < pool.count; ++i) {
        finishCandidate(
            state,
            pool.perm(i),
            undo,
            scratch.data(),
            result.data()
        );
        if (requireCenters && !centersSolved(result.data())) continue;

        const int value = score(result.data(), mode);
        if (value < floor) continue;
        if (value > best.score) {
            best.index = i;
            best.score = value;
        }
    }
    return best;
}

}
