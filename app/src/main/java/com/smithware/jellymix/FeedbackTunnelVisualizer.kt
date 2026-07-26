package com.smithware.jellymix

import android.content.Context
import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.viewinterop.AndroidView
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

@Composable
fun FeedbackTunnelVisualizer(
    frame: AudioAnalysisFrame,
    palette: List<Color>,
    isPlaying: Boolean,
    modifier: Modifier = Modifier,
    intensity: Float = 1f,
    sensitivity: Float = 1f,
    fullscreen: Boolean = false,
    mode: VisualizerRenderMode = VisualizerRenderMode.FeedbackTunnel,
    debugOverlay: Boolean = false,
    onStats: (VisualizerDebugStats) -> Unit = {}
) {
    val renderer = remember { FeedbackTunnelRenderer() }
    AndroidView(
        modifier = modifier,
        factory = { context ->
            FeedbackTunnelGlView(context, renderer).apply {
                setRenderer(renderer)
                renderMode = GLSurfaceView.RENDERMODE_CONTINUOUSLY
            }
        },
        update = { view ->
            renderer.update(frame, palette, isPlaying, intensity, sensitivity, fullscreen, mode, debugOverlay, onStats)
            view.renderMode = if (isPlaying) GLSurfaceView.RENDERMODE_CONTINUOUSLY else GLSurfaceView.RENDERMODE_WHEN_DIRTY
            if (!isPlaying) view.requestRender()
        }
    )
    LaunchedEffect(frame, palette, isPlaying, intensity, sensitivity, fullscreen, mode, debugOverlay) {
        renderer.update(frame, palette, isPlaying, intensity, sensitivity, fullscreen, mode, debugOverlay, onStats)
    }
    DisposableEffect(Unit) {
        onDispose { renderer.stop() }
    }
}

private class FeedbackTunnelGlView(
    context: Context,
    private val feedbackRenderer: FeedbackTunnelRenderer
) : GLSurfaceView(context) {
    init {
        setEGLContextClientVersion(2)
        preserveEGLContextOnPause = true
    }

    override fun onDetachedFromWindow() {
        feedbackRenderer.stop()
        super.onDetachedFromWindow()
    }
}

class FeedbackTunnelRenderer : GLSurfaceView.Renderer {
    private val quad = floatBuffer(
        -1f, -1f, 0f, 0f,
        1f, -1f, 1f, 0f,
        -1f, 1f, 0f, 1f,
        1f, 1f, 1f, 1f
    )
    private val pointBuffer: FloatBuffer = ByteBuffer.allocateDirect(MAX_POINTS * 4 * 4)
        .order(ByteOrder.nativeOrder())
        .asFloatBuffer()
    private val bands = FloatArray(MAX_BANDS) { 0.08f }
    private val palette = FloatArray(15) { 0.2f }
    @Volatile private var bandCount = 0
    @Volatile private var bass = 0.1f
    @Volatile private var mid = 0.1f
    @Volatile private var treble = 0.1f
    @Volatile private var rms = 0.1f
    @Volatile private var beat = false
    @Volatile private var centroid = 0.35f
    @Volatile private var isPlaying = false
    @Volatile private var intensity = 1f
    @Volatile private var sensitivity = 1f
    @Volatile private var fullscreen = false
    @Volatile private var renderMode = VisualizerRenderMode.FeedbackTunnel
    @Volatile private var debugOverlay = false
    @Volatile private var statsCallback: ((VisualizerDebugStats) -> Unit)? = null
    private var width = 0
    private var height = 0
    private var bufferWidth = 0
    private var bufferHeight = 0
    private val textures = IntArray(2)
    private val framebuffers = IntArray(2)
    private var sourceIndex = 0
    private var feedbackProgram = 0
    private var pointProgram = 0
    private var screenProgram = 0
    private var startNs = 0L
    private var frameNumber = 0
    private var direction = 1f
    private var stopped = false
    private var lastRenderedNs = 0L
    private val safetyMonitor = FeedbackSafetyMonitor()
    private var lastStatsNs = 0L
    private var lastStatsFrame = 0
    private val mainHandler = Handler(Looper.getMainLooper())
    @Volatile private var latestStats = VisualizerDebugStats()

    fun update(
        frame: AudioAnalysisFrame,
        colors: List<Color>,
        playing: Boolean,
        visualIntensity: Float,
        visualSensitivity: Float,
        full: Boolean,
        mode: VisualizerRenderMode,
        showDebug: Boolean,
        onStats: (VisualizerDebugStats) -> Unit
    ) {
        val incomingBands = frame.bands
        bandCount = min(incomingBands.size, MAX_BANDS)
        for (i in 0 until bandCount) {
            bands[i] = incomingBands[i].coerceIn(0.02f, 1f)
        }
        val effectiveColors = colors.ifEmpty { listOf(Color(0xFF1DE9B6), Color(0xFF44546A), Color(0xFF6F7885)) }.take(5)
        for (i in 0 until 5) {
            val color = effectiveColors[i % effectiveColors.size]
            palette[i * 3] = color.red
            palette[i * 3 + 1] = color.green
            palette[i * 3 + 2] = color.blue
        }
        bass = frame.bass
        mid = frame.mid
        treble = frame.treble
        rms = frame.rms
        beat = frame.beat
        centroid = frame.spectralCentroid
        isPlaying = playing
        intensity = visualIntensity.coerceIn(0.2f, 2f)
        sensitivity = visualSensitivity.coerceIn(0.2f, 2.5f)
        fullscreen = full
        renderMode = mode
        debugOverlay = showDebug
        statsCallback = onStats
    }

    fun stop() {
        stopped = true
    }

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        stopped = false
        startNs = System.nanoTime()
        feedbackProgram = createProgram(FEEDBACK_VERTEX_SHADER, FEEDBACK_FRAGMENT_SHADER)
        pointProgram = createProgram(POINT_VERTEX_SHADER, POINT_FRAGMENT_SHADER)
        screenProgram = createProgram(SCREEN_VERTEX_SHADER, SCREEN_FRAGMENT_SHADER)
        GLES20.glDisable(GLES20.GL_DEPTH_TEST)
        GLES20.glEnable(GLES20.GL_BLEND)
        GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA)
    }

    override fun onSurfaceChanged(gl: GL10?, surfaceWidth: Int, surfaceHeight: Int) {
        width = surfaceWidth.coerceAtLeast(1)
        height = surfaceHeight.coerceAtLeast(1)
        val scale = if (fullscreen) 4 else 2
        bufferWidth = max(1, width / scale)
        bufferHeight = max(1, height / scale)
        createBuffers()
    }

    override fun onDrawFrame(gl: GL10?) {
        if (stopped || width == 0 || height == 0 || feedbackProgram == 0) return
        if (!isPlaying && frameNumber > 2) return
        val frameTimeNs = if (fullscreen) 16_666_667L else 33_333_333L
        val drawNs = System.nanoTime()
        if (lastRenderedNs != 0L && drawNs - lastRenderedNs < frameTimeNs) return
        lastRenderedNs = drawNs
        val now = (System.nanoTime() - startNs) / 1_000_000_000f
        val targetIndex = 1 - sourceIndex
        if (beat) direction *= -1f
        drawFeedback(targetIndex, sourceIndex, now)
        drawEnergy(targetIndex, now)
        if (safetyMonitor.advance(feedbackDecay(), injectedEnergyEstimate())) {
            clearAccumulationBuffers()
            Log.w("JellyMixVisualizer", "Feedback luminance guard reset accumulation buffer.")
        }
        drawToScreen(targetIndex)
        publishStatsIfNeeded()
        sourceIndex = targetIndex
        frameNumber++
    }

    private fun drawFeedback(targetIndex: Int, textureIndex: Int, time: Float) {
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, framebuffers[targetIndex])
        GLES20.glViewport(0, 0, bufferWidth, bufferHeight)
        GLES20.glUseProgram(feedbackProgram)
        bindQuad(feedbackProgram)
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textures[textureIndex])
        GLES20.glUniform1i(GLES20.glGetUniformLocation(feedbackProgram, "uTexture"), 0)
        GLES20.glUniform1f(GLES20.glGetUniformLocation(feedbackProgram, "uTime"), time)
        GLES20.glUniform1f(GLES20.glGetUniformLocation(feedbackProgram, "uBass"), bass * sensitivity)
        GLES20.glUniform1f(GLES20.glGetUniformLocation(feedbackProgram, "uTreble"), treble)
        GLES20.glUniform1f(GLES20.glGetUniformLocation(feedbackProgram, "uBeat"), if (beat) 1f else 0f)
        GLES20.glUniform1f(GLES20.glGetUniformLocation(feedbackProgram, "uDirection"), direction)
        GLES20.glUniform1f(GLES20.glGetUniformLocation(feedbackProgram, "uIntensity"), intensity)
        GLES20.glUniform1f(GLES20.glGetUniformLocation(feedbackProgram, "uDecay"), feedbackDecay())
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
    }

    private fun drawEnergy(targetIndex: Int, time: Float) {
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, framebuffers[targetIndex])
        GLES20.glViewport(0, 0, bufferWidth, bufferHeight)
        GLES20.glUseProgram(pointProgram)
        GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE)
        pointBuffer.clear()
        val count = bandCount.coerceIn(1, MAX_BANDS)
        val ringCount = if (fullscreen) 4 else 3
        var pointCount = 0
        for (ring in 0 until ringCount) {
            val radiusBase = 0.09f + ring * 0.16f + bass * 0.1f
            for (i in 0 until count) {
                val band = bands[i] * sensitivity
                val angle = (i / count.toFloat()) * 6.28318f + time * (0.12f + ring * 0.04f) * direction
                val wobble = sin(time * 1.7f + i * 0.37f + ring) * bass * 0.16f
                val radius = radiusBase + band * (0.28f + ring * 0.04f) + wobble
                val x = cos(angle) * radius
                val y = sin(angle * (1.0f + mid * 0.12f)) * radius
                pointBuffer.put(x)
                pointBuffer.put(y)
                pointBuffer.put((12f + band * 54f + treble * 18f) * intensity)
                pointBuffer.put((i % 5).toFloat())
                pointCount++
                if (pointCount >= MAX_POINTS) break
            }
        }
        pointBuffer.position(0)
        val positionHandle = GLES20.glGetAttribLocation(pointProgram, "aPoint")
        GLES20.glEnableVertexAttribArray(positionHandle)
        GLES20.glVertexAttribPointer(positionHandle, 4, GLES20.GL_FLOAT, false, 4 * 4, pointBuffer)
        GLES20.glUniform1f(GLES20.glGetUniformLocation(pointProgram, "uRms"), rms)
        GLES20.glUniform1f(GLES20.glGetUniformLocation(pointProgram, "uCentroid"), centroid)
        GLES20.glUniform3fv(GLES20.glGetUniformLocation(pointProgram, "uPalette"), 5, palette, 0)
        GLES20.glDrawArrays(GLES20.GL_POINTS, 0, pointCount)
        GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA)
        GLES20.glDisableVertexAttribArray(positionHandle)
    }

    private fun drawToScreen(textureIndex: Int) {
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0)
        GLES20.glViewport(0, 0, width, height)
        GLES20.glUseProgram(screenProgram)
        GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA)
        bindQuad(screenProgram)
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textures[textureIndex])
        GLES20.glUniform1i(GLES20.glGetUniformLocation(screenProgram, "uTexture"), 0)
        GLES20.glUniform1f(GLES20.glGetUniformLocation(screenProgram, "uMeanLuminance"), safetyMonitor.meanLuminance)
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
    }

    private fun bindQuad(program: Int) {
        quad.position(0)
        val position = GLES20.glGetAttribLocation(program, "aPosition")
        GLES20.glEnableVertexAttribArray(position)
        GLES20.glVertexAttribPointer(position, 2, GLES20.GL_FLOAT, false, 4 * 4, quad)
        quad.position(2)
        val texCoord = GLES20.glGetAttribLocation(program, "aTexCoord")
        GLES20.glEnableVertexAttribArray(texCoord)
        GLES20.glVertexAttribPointer(texCoord, 2, GLES20.GL_FLOAT, false, 4 * 4, quad)
    }

    private fun createBuffers() {
        if (textures[0] != 0) GLES20.glDeleteTextures(2, textures, 0)
        if (framebuffers[0] != 0) GLES20.glDeleteFramebuffers(2, framebuffers, 0)
        GLES20.glGenTextures(2, textures, 0)
        GLES20.glGenFramebuffers(2, framebuffers, 0)
        for (i in 0..1) {
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textures[i])
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
            GLES20.glTexImage2D(GLES20.GL_TEXTURE_2D, 0, GLES20.GL_RGBA, bufferWidth, bufferHeight, 0, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, null)
            GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, framebuffers[i])
            GLES20.glFramebufferTexture2D(GLES20.GL_FRAMEBUFFER, GLES20.GL_COLOR_ATTACHMENT0, GLES20.GL_TEXTURE_2D, textures[i], 0)
            GLES20.glViewport(0, 0, bufferWidth, bufferHeight)
            GLES20.glClearColor(0f, 0f, 0f, 1f)
            GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)
        }
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0)
        sourceIndex = 0
        safetyMonitor.reset()
    }

    private fun clearAccumulationBuffers() {
        for (i in 0..1) {
            GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, framebuffers[i])
            GLES20.glViewport(0, 0, bufferWidth, bufferHeight)
            GLES20.glClearColor(0f, 0f, 0f, 1f)
            GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)
        }
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0)
        sourceIndex = 0
    }

    private fun feedbackDecay(): Float =
        (0.965f - treble.coerceIn(0f, 1f) * 0.035f - rms.coerceIn(0f, 1f) * 0.012f).coerceIn(0.92f, 0.97f)

    private fun injectedEnergyEstimate(): Float =
        (rms.coerceIn(0f, 1f) * intensity.coerceIn(0.2f, 2f) * 0.018f + if (beat) 0.015f else 0f)
            .coerceIn(0f, 0.055f)

    private fun publishStatsIfNeeded() {
        if (!debugOverlay) return
        val nowNs = System.nanoTime()
        if (nowNs - lastStatsNs < 500_000_000L) return
        val frames = frameNumber - lastStatsFrame
        val seconds = ((nowNs - lastStatsNs).coerceAtLeast(1L) / 1_000_000_000f).coerceAtLeast(0.001f)
        lastStatsNs = nowNs
        lastStatsFrame = frameNumber
        latestStats = VisualizerDebugStats(
            fps = frames / seconds,
            meanLuminance = safetyMonitor.meanLuminance,
            resetCount = safetyMonitor.resetCount,
            bands = bands.take(bandCount.coerceIn(0, MAX_BANDS)),
            live = isPlaying,
            mode = renderMode
        )
        mainHandler.post {
            Log.d(
                "JellyMixVisualizer",
                "fps=${latestStats.fps.roundOne()} mean=${latestStats.meanLuminance.roundOne()} resets=${latestStats.resetCount} bands=${latestStats.bands.take(8)}"
            )
            statsCallback?.invoke(latestStats)
        }
    }

    private fun createProgram(vertexSource: String, fragmentSource: String): Int {
        val vertex = compileShader(GLES20.GL_VERTEX_SHADER, vertexSource)
        val fragment = compileShader(GLES20.GL_FRAGMENT_SHADER, fragmentSource)
        val program = GLES20.glCreateProgram()
        GLES20.glAttachShader(program, vertex)
        GLES20.glAttachShader(program, fragment)
        GLES20.glLinkProgram(program)
        return program
    }

    private fun compileShader(type: Int, source: String): Int {
        val shader = GLES20.glCreateShader(type)
        GLES20.glShaderSource(shader, source)
        GLES20.glCompileShader(shader)
        return shader
    }

    private companion object {
        private const val MAX_BANDS = 64
        private const val MAX_POINTS = 256
    }
}

private fun Float.roundOne(): String = String.format("%.1f", this)

private fun floatBuffer(vararg values: Float): FloatBuffer =
    ByteBuffer.allocateDirect(values.size * 4)
        .order(ByteOrder.nativeOrder())
        .asFloatBuffer()
        .apply {
            put(values)
            position(0)
        }

private const val FEEDBACK_VERTEX_SHADER = """
attribute vec2 aPosition;
attribute vec2 aTexCoord;
varying vec2 vTexCoord;
void main() {
    vTexCoord = aTexCoord;
    gl_Position = vec4(aPosition, 0.0, 1.0);
}
"""

private const val FEEDBACK_FRAGMENT_SHADER = """
precision mediump float;
uniform sampler2D uTexture;
uniform float uTime;
uniform float uBass;
uniform float uTreble;
uniform float uBeat;
uniform float uDirection;
uniform float uIntensity;
uniform float uDecay;
varying vec2 vTexCoord;
void main() {
    vec2 p = vTexCoord - 0.5;
    float warp = sin((p.x + p.y) * 12.0 + uTime * 1.4) * 0.004 * (1.0 + uBass * 5.0);
    float angle = (0.006 + uBass * 0.018 + uBeat * 0.035) * uDirection;
    float c = cos(angle);
    float s = sin(angle);
    mat2 rot = mat2(c, -s, s, c);
    float zoom = 1.012 + uBass * 0.026 + uBeat * 0.018;
    vec2 uv = rot * (p * zoom + vec2(warp, -warp)) + 0.5;
    vec4 previous = texture2D(uTexture, uv);
    vec3 color = previous.rgb * uDecay;
    color += vec3(0.00045, 0.0008, 0.0011) * uIntensity;
    color = min(color, vec3(0.82));
    gl_FragColor = vec4(color, 1.0);
}
"""

private const val POINT_VERTEX_SHADER = """
attribute vec4 aPoint;
uniform float uRms;
uniform float uCentroid;
varying float vIndex;
varying float vEnergy;
void main() {
    gl_Position = vec4(aPoint.xy, 0.0, 1.0);
    gl_PointSize = aPoint.z * (0.58 + uRms * 0.55);
    vIndex = aPoint.w;
    vEnergy = clamp(aPoint.z / 64.0, 0.0, 1.0);
}
"""

private const val POINT_FRAGMENT_SHADER = """
precision mediump float;
uniform vec3 uPalette[5];
uniform float uCentroid;
varying float vIndex;
varying float vEnergy;
void main() {
    vec2 p = gl_PointCoord - 0.5;
    float d = length(p);
    float core = smoothstep(0.5, 0.02, d);
    float glow = smoothstep(0.5, 0.0, d) * 0.55;
    int index = int(mod(vIndex + floor(uCentroid * 5.0), 5.0));
    vec3 color = uPalette[index];
    color += vec3(uCentroid * 0.18, uCentroid * 0.12, uCentroid * 0.24);
    gl_FragColor = vec4(color, (core + glow) * (0.14 + vEnergy * 0.48));
}
"""

private const val SCREEN_VERTEX_SHADER = """
attribute vec2 aPosition;
attribute vec2 aTexCoord;
varying vec2 vTexCoord;
void main() {
    vTexCoord = aTexCoord;
    gl_Position = vec4(aPosition, 0.0, 1.0);
}
"""

private const val SCREEN_FRAGMENT_SHADER = """
precision mediump float;
uniform sampler2D uTexture;
uniform float uMeanLuminance;
varying vec2 vTexCoord;
void main() {
    vec2 p = vTexCoord - 0.5;
    vec4 color = texture2D(uTexture, vTexCoord);
    float vignette = smoothstep(0.82, 0.18, length(p));
    vec3 mapped = color.rgb * (0.62 + vignette * 0.34);
    float lum = dot(mapped, vec3(0.2126, 0.7152, 0.0722));
    float guard = smoothstep(0.72, 0.86, max(lum, uMeanLuminance));
    mapped = mix(mapped, mapped * 0.72, guard);
    gl_FragColor = vec4(clamp(mapped, 0.0, 0.82), 1.0);
}
"""
