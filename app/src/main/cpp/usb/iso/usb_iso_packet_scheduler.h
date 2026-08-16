#pragma once

#include <algorithm>
#include <cstdint>

namespace neri::usb {

// Scale for fractional rate correction: 1 unit = 1/1'000'000 frame per interval.
// At 8000 intervals/s that is 0.008 frames/s (~0.18 ppm at 44.1 kHz), which is
// fine enough to track typical synchronous-mode DAC clock drift without
// audible stepping.
constexpr int64_t kIsoRateCorrectionScale = 1'000'000;

struct IsoPacketPlan {
    int frames = 0;
    int bytes = 0;
};

class IsoPacketScheduler {
public:
    void configure(int sampleRate, int intervalsPerSecond, int frameBytes) {
        sampleRate_ = std::max(1, sampleRate);
        intervalsPerSecond_ = std::max(1, intervalsPerSecond);
        frameBytes_ = std::max(1, frameBytes);
        reset();
    }

    void reset() {
        frameRemainder_ = 0;
    }

    // correctionUnitsPerInterval: signed rate bias in units of
    // 1/1'000'000 frame per interval. Negative values slow the scheduled rate
    // down (host faster than the DAC), positive values speed it up.
    void setRateCorrection(int64_t correctionUnitsPerInterval) {
        rateCorrection_ = std::clamp<int64_t>(
            correctionUnitsPerInterval,
            -kIsoRateCorrectionScale,
            kIsoRateCorrectionScale
        );
    }

    [[nodiscard]] int64_t rateCorrection() const {
        return rateCorrection_;
    }

    [[nodiscard]] IsoPacketPlan next() {
        frameRemainder_ +=
            static_cast<int64_t>(sampleRate_) * kIsoRateCorrectionScale +
            rateCorrection_;
        const int64_t divisor =
            static_cast<int64_t>(intervalsPerSecond_) * kIsoRateCorrectionScale;
        const int64_t frames64 = frameRemainder_ / divisor;
        const int frames = static_cast<int>(frames64);
        frameRemainder_ -= frames64 * divisor;
        return IsoPacketPlan { frames, frames * frameBytes_ };
    }

private:
    int sampleRate_ = 1;
    int intervalsPerSecond_ = 1;
    int frameBytes_ = 1;
    int64_t frameRemainder_ = 0;
    int64_t rateCorrection_ = 0;
};

} // namespace neri::usb
