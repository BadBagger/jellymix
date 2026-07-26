package com.smithware.jellymix

import androidx.compose.runtime.Immutable
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.abs
import kotlin.math.ln
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.sqrt

enum class VisualizerRenderMode {
    FeedbackTunnel,
    Fluid,
    Ridgeline
}

@Immutable
data class AudioAnalysisFrame(
    val bands: List<Float>,
    val bass: Float,
    val mid: Float,
    val treble: Float,
    val rms: Float,
    val beat: Boolean,
    val spectralCentroid: Float,
    val live: Boolean
)

@Immutable
data class VisualizerDebugStats(
    val fps: Float = 0f,
    val meanLuminance: Float = 0f,
    val resetCount: Int = 0,
    val bands: List<Float> = emptyList(),
    val live: Boolean = false,
    val mode: VisualizerRenderMode = VisualizerRenderMode.FeedbackTunnel
)

class VisualizerFrameBus(initialFrame: AudioAnalysisFrame = ambientFrame()) {
    private val latestFrame = AtomicReference(initialFrame)

    fun publish(frame: AudioAnalysisFrame) {
        latestFrame.set(frame)
    }

    fun latest(): AudioAnalysisFrame = latestFrame.get()
}

class FeedbackSafetyMonitor(
    private val highLuminanceThreshold: Float = 0.85f,
    private val highFrameLimit: Int = 30
) {
    var meanLuminance: Float = 0.02f
        private set
    var resetCount: Int = 0
        private set
    private var highFrames = 0

    fun advance(decay: Float, injectedEnergy: Float): Boolean {
        meanLuminance = (meanLuminance * decay.coerceIn(0.88f, 0.98f) + injectedEnergy.coerceIn(0f, 0.08f))
            .coerceIn(0f, 0.92f)
        if (meanLuminance > highLuminanceThreshold) {
            highFrames++
        } else {
            highFrames = 0
        }
        if (highFrames > highFrameLimit) {
            reset()
            return true
        }
        return false
    }

    fun forceMeanForTest(value: Float) {
        meanLuminance = value.coerceIn(0f, 1f)
        highFrames = 0
    }

    fun reset() {
        meanLuminance = 0.02f
        highFrames = 0
        resetCount++
    }
}

class VisualizerAnalysisEngine(
    private val bandCount: Int = 48,
    private val attackMs: Float = 15f,
    private val releaseMs: Float = 250f
) {
    private val envelopes = FloatArray(bandCount) { 0.08f }
    private var rollingMaxDb = -48f
    private var bassAverage = 0.18f
    private var lastFrameMs = 0L

    fun analyzeVisualizerFft(fft: ByteArray, samplingRateMilliHz: Int, nowMs: Long = System.currentTimeMillis()): AudioAnalysisFrame {
        if (fft.size < 6) return ambientFrame(bandCount)
        val magnitudes = FloatArray((fft.size / 2).coerceAtLeast(1))
        magnitudes[0] = abs(fft[0].toInt()).toFloat()
        for (i in 1 until magnitudes.size) {
            val realIndex = i * 2
            val imagIndex = realIndex + 1
            if (imagIndex >= fft.size) break
            val real = fft[realIndex].toInt()
            val imag = fft[imagIndex].toInt()
            magnitudes[i] = sqrt((real * real + imag * imag).toFloat())
        }
        return analyzeMagnitudes(magnitudes, samplingRateMilliHz / 1000f, nowMs, live = true)
    }

    fun analyzeWaveform(waveform: ByteArray, nowMs: Long = System.currentTimeMillis()): AudioAnalysisFrame {
        if (waveform.isEmpty()) return ambientFrame(bandCount)
        val magnitudes = FloatArray(waveform.size / 2) { index ->
            abs(waveform[index * 2].toInt() - 128).toFloat()
        }
        return analyzeMagnitudes(magnitudes, 44_100f, nowMs, live = true)
    }

    fun ambient(track: Track?, nowMs: Long = System.currentTimeMillis()): AudioAnalysisFrame {
        val seed = track?.let { "${it.id}:${it.title}:${it.artist}".fold(0) { acc, char -> acc * 31 + char.code } } ?: 17
        val t = nowMs / 1000f
        val bands = List(bandCount) { index ->
            val slow = kotlin.math.sin(t * 0.7f + index * 0.31f + seed * 0.0007f)
            val drift = kotlin.math.sin(t * 0.23f + index * 0.11f)
            (0.16f + slow * 0.08f + drift * 0.05f + (index % 7) * 0.01f).coerceIn(0.06f, 0.42f)
        }
        return AudioAnalysisFrame(
            bands = bands,
            bass = bands.take(8).averageFloat(),
            mid = bands.drop(8).take(22).averageFloat(),
            treble = bands.drop(30).averageFloat(),
            rms = bands.averageFloat(),
            beat = false,
            spectralCentroid = 0.38f,
            live = false
        )
    }

    private fun analyzeMagnitudes(magnitudes: FloatArray, sampleRate: Float, nowMs: Long, live: Boolean): AudioAnalysisFrame {
        val dtMs = if (lastFrameMs == 0L) 33f else (nowMs - lastFrameMs).coerceIn(1L, 250L).toFloat()
        lastFrameMs = nowMs
        val attack = envelopeCoefficient(dtMs, attackMs)
        val release = envelopeCoefficient(dtMs, releaseMs)
        val bandValues = FloatArray(bandCount)
        var weightedFreq = 0f
        var totalMagnitude = 0f
        for (band in 0 until bandCount) {
            val start = logIndex(band, bandCount, magnitudes.size)
            val end = logIndex(band + 1, bandCount, magnitudes.size).coerceAtLeast(start + 1).coerceAtMost(magnitudes.size)
            var sum = 0f
            for (i in start until end) {
                val mag = magnitudes[i]
                sum += mag
                val freq = (i / magnitudes.size.toFloat()) * (sampleRate / 2f)
                weightedFreq += freq * mag
                totalMagnitude += mag
            }
            val average = sum / (end - start).coerceAtLeast(1)
            val db = 20f * log10((average + 1f).coerceAtLeast(1f) / 128f)
            rollingMaxDb = maxOf(rollingMaxDb * 0.995f, db, -42f)
            val normalizerFloor = (rollingMaxDb - 42f).coerceAtMost(-12f)
            val normalized = ((db - normalizerFloor) / 42f).coerceIn(0.04f, 1f)
            val coeff = if (normalized > envelopes[band]) attack else release
            envelopes[band] += (normalized - envelopes[band]) * coeff
            bandValues[band] = envelopes[band].coerceIn(0.04f, 1f)
        }
        val bands = bandValues.toList()
        val bass = bands.take((bandCount * 0.18f).toInt().coerceAtLeast(4)).averageFloat()
        val mid = bands.drop((bandCount * 0.18f).toInt()).take((bandCount * 0.48f).toInt()).averageFloat()
        val treble = bands.drop((bandCount * 0.66f).toInt()).averageFloat()
        bassAverage = bassAverage * 0.94f + bass * 0.06f
        val beat = bass > bassAverage * 1.42f && bass > 0.28f
        val centroidHz = if (totalMagnitude <= 0f) 0f else weightedFreq / totalMagnitude
        val centroid = (centroidHz / (sampleRate / 2f).coerceAtLeast(1f)).coerceIn(0f, 1f)
        return AudioAnalysisFrame(
            bands = bands,
            bass = bass,
            mid = mid,
            treble = treble,
            rms = bands.averageFloat(),
            beat = beat,
            spectralCentroid = centroid,
            live = live
        )
    }

    private fun logIndex(band: Int, totalBands: Int, totalBins: Int): Int {
        if (totalBins <= 1) return 0
        val minLog = ln(1f)
        val maxLog = ln(totalBins.toFloat())
        val position = band / totalBands.toFloat()
        return (kotlin.math.exp(minLog + (maxLog - minLog) * position).toInt() - 1).coerceIn(0, totalBins - 1)
    }

    private fun envelopeCoefficient(deltaMs: Float, timeMs: Float): Float =
        (1f - 2.7182818f.pow(-deltaMs / timeMs.coerceAtLeast(1f))).coerceIn(0.01f, 1f)
}

internal fun ambientFrame(bandCount: Int = 48): AudioAnalysisFrame {
    val bands = restingVisualizerBands(bandCount)
    return AudioAnalysisFrame(
        bands = bands,
        bass = bands.take(8).averageFloat(),
        mid = bands.drop(8).take(24).averageFloat(),
        treble = bands.drop(32).averageFloat(),
        rms = bands.averageFloat(),
        beat = false,
        spectralCentroid = 0.35f,
        live = false
    )
}

internal fun List<Float>.averageFloat(): Float =
    if (isEmpty()) 0f else sum() / size
