#pragma once

#include <algorithm>

namespace neri::usb {

// 致命阈值：32。Android usbfs 的异步 ISO 传输对高频端点（如 125us microframe）
// 有偶发包错误（实测 8 秒内 5~13 个错包 / 数万包，音频帧仍实时推进）。
// 计分按"有错包的传输"每次 +1（而非按失败包数），干净传输 -1：
// 单个传输的偶发错误只会短暂升到 1~2 分并快速回落；连续大量传输失败才致命。
constexpr int kIsoPacketErrorFailureScore = 32;

inline int updateIsoPacketErrorScore(
    int currentScore,
    int failedPacketCount
) {
    if (failedPacketCount <= 0) {
        return std::max(0, currentScore - 1);
    }
    return std::min(
        kIsoPacketErrorFailureScore,
        currentScore + 1
    );
}

inline bool shouldFailForIsoPacketErrors(int errorScore) {
    return errorScore >= kIsoPacketErrorFailureScore;
}

inline int completedIsoPacketBytes(
    bool packetCompleted,
    int requestedLength,
    int actualLength
) {
    if (!packetCompleted || requestedLength <= 0 || actualLength <= 0) {
        return 0;
    }
    return std::min(requestedLength, actualLength);
}

} // namespace neri::usb
