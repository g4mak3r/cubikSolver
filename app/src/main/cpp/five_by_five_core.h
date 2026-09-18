#pragma once

#include <array>
#include <cstdint>
#include <vector>

namespace cubik555 {

constexpr int kFacelets = 150;
constexpr int kPermSize = 150;

enum class ScoreMode : int {
    Centers = 0,
    Edges = 1
};

struct EvalResult {
    int index = -1;
    int score = -1;
};

struct Pool {
    int count = 0;
    std::vector<std::uint16_t> perms;

    const std::uint16_t* perm(int index) const {
        return perms.data() + static_cast<std::size_t>(index) * kPermSize;
    }
};

int centerScore(const std::uint8_t* state);
bool centersSolved(const std::uint8_t* state);
int edgeQualityScore(const std::uint8_t* state);
int edgeSearchScore(const std::uint8_t* state);
void applyPerm(
    const std::uint8_t* source,
    const std::uint16_t* perm,
    std::uint8_t* target
);
int score(const std::uint8_t* state, ScoreMode mode);

int findFirstImproving(
    const Pool& pool,
    const std::uint8_t* state,
    const std::uint16_t* undo,
    ScoreMode mode,
    int before,
    bool requireCenters
);

EvalResult findBest(
    const Pool& pool,
    const std::uint8_t* state,
    const std::uint16_t* undo,
    ScoreMode mode,
    int floor,
    bool requireCenters
);

}
