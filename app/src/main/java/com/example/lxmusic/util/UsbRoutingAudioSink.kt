package com.example.lxmusic.util

import android.media.AudioDeviceInfo
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.AuxEffectInfo
import androidx.media3.common.Format
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.DefaultAudioSink
import java.nio.ByteBuffer

/**
 * 支持 USB DAC 路由的 AudioSink（委托 [DefaultAudioSink]）。
 *
 * 当设置启用 USB 独占输出且检测到 USB 音频设备时，把 AudioTrack 输出直通到 USB DAC，
 * 由系统 USB 音频 HAL 处理位完美输出。未启用或无 USB 设备时行为与默认一致。
 */
@OptIn(UnstableApi::class)
class UsbRoutingAudioSink(
    private val delegate: DefaultAudioSink,
    private val preferredUsbDeviceProvider: () -> AudioDeviceInfo?
) : AudioSink {

    private var preferredUsbDevice: AudioDeviceInfo? = null

    /**
     * 独占激活强制内置输出：native libusb 直驱 USB DAC 期间，系统侧 DefaultAudioSink
     * 再被路由到 USB DAC 会与 libusb 双主体并发写同一等时端点 → 等时包错误（前台沙沙声）。
     * 独占激活时钉到内置扬声器，让系统侧离开 USB 端点；未激活/回退时恢复 USB 路由。
     */
    private var forceBuiltInOutput = false

    fun setForceBuiltInOutput(force: Boolean, builtInOutput: AudioDeviceInfo? = null) {
        if (forceBuiltInOutput == force) return
        forceBuiltInOutput = force
        if (force) {
            runCatching { delegate.setPreferredDevice(builtInOutput) }
        }
        // !force 时不立即恢复 USB 路由：独占会话刚关闭时系统 HAL 尚未接管
        // USB DAC（异步挂载），此时 setPreferredDevice 到未就绪的 DAC 会导致
        // AudioTrack 配置异常 → 扬声器异响。由调用方延迟（约 2s）后 refreshUsbDevice()。
    }

    fun refreshUsbDevice() {
        if (forceBuiltInOutput) return
        val device = preferredUsbDeviceProvider()
        if (device != preferredUsbDevice) {
            preferredUsbDevice = device
            runCatching { delegate.setPreferredDevice(device) }
        }
    }

    override fun setListener(listener: AudioSink.Listener) = delegate.setListener(listener)
    override fun supportsFormat(format: Format): Boolean = delegate.supportsFormat(format)
    override fun getFormatSupport(format: Format): Int = delegate.getFormatSupport(format)
    override fun getCurrentPositionUs(sourceEnded: Boolean): Long = delegate.getCurrentPositionUs(sourceEnded)
    override fun configure(format: Format, specifiedBufferSize: Int, outputChannels: IntArray?) =
        delegate.configure(format, specifiedBufferSize, outputChannels)
    override fun play() = delegate.play()
    override fun handleDiscontinuity() = delegate.handleDiscontinuity()
    override fun handleBuffer(buffer: ByteBuffer, presentationTimeUs: Long, encodedAccessUnitCount: Int): Boolean =
        delegate.handleBuffer(buffer, presentationTimeUs, encodedAccessUnitCount)
    override fun playToEndOfStream() = delegate.playToEndOfStream()
    override fun isEnded(): Boolean = delegate.isEnded()
    override fun hasPendingData(): Boolean = delegate.hasPendingData()
    override fun setPlaybackParameters(playbackParameters: PlaybackParameters) =
        delegate.setPlaybackParameters(playbackParameters)
    override fun getPlaybackParameters(): PlaybackParameters = delegate.playbackParameters
    override fun setSkipSilenceEnabled(enabled: Boolean) = delegate.setSkipSilenceEnabled(enabled)
    override fun getSkipSilenceEnabled(): Boolean = delegate.skipSilenceEnabled
    override fun setAudioAttributes(audioAttributes: AudioAttributes) = delegate.setAudioAttributes(audioAttributes)
    override fun getAudioAttributes(): AudioAttributes = delegate.audioAttributes
    override fun setAudioSessionId(audioSessionId: Int) = delegate.setAudioSessionId(audioSessionId)
    override fun setAuxEffectInfo(auxEffectInfo: AuxEffectInfo) = delegate.setAuxEffectInfo(auxEffectInfo)
    override fun setPreferredDevice(device: AudioDeviceInfo?) {
        delegate.setPreferredDevice(device)
    }
    override fun enableTunnelingV21() = delegate.enableTunnelingV21()
    override fun disableTunneling() = delegate.disableTunneling()
    override fun setVolume(volume: Float) = delegate.setVolume(volume)
    override fun pause() = delegate.pause()
    override fun flush() = delegate.flush()
    override fun reset() = delegate.reset()
}
