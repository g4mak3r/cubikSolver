#include "five_by_five_core.h"

#include <array>
#include <cassert>
#include <cstdint>
#include <iostream>

using namespace cubik555;

int main() {
    std::array<std::uint8_t, kFacelets> solved{};
    for (int i = 0; i < kFacelets; ++i) {
        solved[i] = static_cast<std::uint8_t>(i / 25);
    }

    assert(centerScore(solved.data()) == 54);
    assert(score(solved.data(), ScoreMode::Centers) == 78);
    assert(centersSolved(solved.data()));
    assert(edgeQualityScore(solved.data()) == 24);
    assert(edgeSearchScore(solved.data()) == 396);

    auto scrambled = solved;
    const int a = 6;
    const int b = 31;
    std::swap(scrambled[a], scrambled[b]);
    assert(centerScore(scrambled.data()) == 52);

    Pool pool;
    pool.count = 1;
    pool.perms.resize(kPermSize);
    for (int i = 0; i < kPermSize; ++i) {
        pool.perms[i] = static_cast<std::uint16_t>(i);
    }
    std::swap(pool.perms[a], pool.perms[b]);

    const int index = findFirstImproving(
        pool,
        scrambled.data(),
        nullptr,
        ScoreMode::Centers,
        52,
        false
    );
    assert(index == 0);

    const auto beam = beamSearch(
        pool,
        scrambled.data(),
        ScoreMode::Centers,
        78,
        52,
        false,
        2,
        8,
        100
    );
    assert(beam.size() == 1);
    assert(beam[0] == 0);

    const auto bestFirst = bestFirstSearch(
        pool,
        scrambled.data(),
        ScoreMode::Centers,
        78,
        52,
        false,
        32,
        100
    );
    assert(bestFirst.size() == 1);
    assert(bestFirst[0] == 0);

    const auto best = findBest(
        pool,
        scrambled.data(),
        nullptr,
        ScoreMode::Centers,
        52,
        false
    );
    assert(best.index == 0);
    assert(best.score == 54);

    std::cout << "cubik555 native core ok\n";
    return 0;
}
