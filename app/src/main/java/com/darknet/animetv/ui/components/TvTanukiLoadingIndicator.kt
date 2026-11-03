package com.darknet.animetv.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import com.darknet.animetv.R
import com.darknet.animetv.ui.theme.TvAccentPurple
import com.darknet.animetv.ui.theme.TvBrandCyan
import com.darknet.animetv.ui.theme.TvGoldStar
import com.darknet.animetv.ui.theme.TvPrimaryRed
import com.darknet.animetv.ui.theme.TvTextPrimary
import com.darknet.animetv.ui.theme.TvTextSecondary
import kotlin.math.cos
import kotlin.math.sin

/**
 * Animated TanukiTV Brand Logo loading screen component.
 * Renders the current TanukiTV typography & mascot logo (`R.drawable.tanukitv_logo`)
 * brought to life with:
 * - Cheerfully waving Tanuki paw above the 'T' with comic motion arcs
 * - Natural periodic eye blinking over the Tanuki's eyes
 * - Glowing, pulsing antenna beacons on the Tanuki's head
 * - Playful floating bob & subtle tilt motion
 * - Wagging tail motion lines and pulsing neon '▶ TV' badge aura
 * - Sleek animated streaming progress bar beneath the logo
 */
@Composable
fun TvTanukiLoadingIndicator(
    modifier: Modifier = Modifier,
    statusText: String = "Summoning anime catalog...",
    subtitleText: String? = null,
    isCompact: Boolean = false
) {
    val transition = rememberInfiniteTransition(label = "tanukitv_logo_loader")

    // Smooth vertical floating bob (-1f..+1f)
    val bobPhase by transition.animateFloat(
        initialValue = -1f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "logo_bob"
    )

    // Cheerful Tanuki paw wave angle (-24 deg..+28 deg)
    val pawWaveAngle by transition.animateFloat(
        initialValue = -24f,
        targetValue = 28f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 480, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "tanuki_paw_wave"
    )

    // Bushy tail wag / body sway (-2.2 deg..+2.2 deg)
    val logoTiltAngle by transition.animateFloat(
        initialValue = -2.2f,
        targetValue = 2.2f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 900, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "logo_tilt"
    )

    // Natural eye blink (0f = open, 1f = closed eyelid) every 2.7 seconds
    val blinkAmount by transition.animateFloat(
        initialValue = 0f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = keyframes {
                durationMillis = 2700
                0.0f at 0
                0.0f at 2300
                1.0f at 2440
                0.0f at 2590
                0.0f at 2700
            },
            repeatMode = RepeatMode.Restart
        ),
        label = "tanuki_eye_blink"
    )

    // Continuous cycle phase (0f..360f) for antenna pulse, sparkles, and progress sweep
    val cycleAngle by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "logo_cycle"
    )

    // Pulsing neon glow intensity (0.82f..1.18f)
    val glowPulse by transition.animateFloat(
        initialValue = 0.82f,
        targetValue = 1.18f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "neon_glow_pulse"
    )

    val dotCount = ((cycleAngle / 90f).toInt() % 4)
    val animatedDots = ".".repeat(dotCount).padEnd(3, ' ')
    val baseStatus = statusText.trimEnd('.')

    if (isCompact) {
        Row(
            modifier = modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            AnimatedTanukiLogoArtwork(
                modifier = Modifier.size(width = 112.dp, height = 72.dp),
                bobPhase = bobPhase,
                pawWaveAngle = pawWaveAngle,
                logoTiltAngle = logoTiltAngle,
                blinkAmount = blinkAmount,
                cycleAngle = cycleAngle,
                glowPulse = glowPulse
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column {
                Text(
                    text = "$baseStatus$animatedDots",
                    color = TvTextPrimary,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold
                )
                if (!subtitleText.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(3.dp))
                    Text(
                        text = subtitleText,
                        color = TvTextSecondary,
                        fontSize = 12.sp
                    )
                }
            }
        }
    } else {
        Column(
            modifier = modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            AnimatedTanukiLogoArtwork(
                modifier = Modifier.size(width = 300.dp, height = 194.dp),
                bobPhase = bobPhase,
                pawWaveAngle = pawWaveAngle,
                logoTiltAngle = logoTiltAngle,
                blinkAmount = blinkAmount,
                cycleAngle = cycleAngle,
                glowPulse = glowPulse
            )

            Spacer(modifier = Modifier.height(14.dp))

            // Sleek neon loading shimmer bar beneath the animated logo
            Canvas(modifier = Modifier.size(width = 180.dp, height = 5.dp)) {
                val barW = size.width
                val barH = size.height
                drawRoundRect(
                    color = Color.White.copy(alpha = 0.12f),
                    size = Size(barW, barH),
                    cornerRadius = CornerRadius(barH / 2f, barH / 2f)
                )
                val progressFrac = (cycleAngle / 360f)
                val segmentW = barW * 0.42f
                val startX = (barW + segmentW) * progressFrac - segmentW
                val clampedStart = startX.coerceIn(0f, barW)
                val clampedEnd = (startX + segmentW).coerceIn(0f, barW)
                if (clampedEnd > clampedStart) {
                    drawRoundRect(
                        brush = Brush.horizontalGradient(
                            colors = listOf(TvBrandCyan, TvAccentPurple, Color(0xFFFF40E5)),
                            startX = clampedStart,
                            endX = clampedEnd
                        ),
                        topLeft = Offset(clampedStart, 0f),
                        size = Size(clampedEnd - clampedStart, barH),
                        cornerRadius = CornerRadius(barH / 2f, barH / 2f)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "$baseStatus$animatedDots",
                color = TvTextPrimary,
                fontSize = 19.sp,
                fontWeight = FontWeight.ExtraBold,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = subtitleText ?: "Tanuki is tuning your stream...",
                color = TvTextSecondary,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun AnimatedTanukiLogoArtwork(
    modifier: Modifier,
    bobPhase: Float,
    pawWaveAngle: Float,
    logoTiltAngle: Float,
    blinkAmount: Float,
    cycleAngle: Float,
    glowPulse: Float
) {
    Box(
        modifier = modifier.graphicsLayer {
            translationY = bobPhase * 6f
            rotationZ = logoTiltAngle * 0.65f
        },
        contentAlignment = Alignment.Center
    ) {
        // 1. Ambient backdrop aura behind the Tanuki and '▶ TV' neon badge
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height

            // Soft purple/gold glow behind the Tanuki mascot on the left
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        TvAccentPurple.copy(alpha = 0.26f * glowPulse),
                        Color.Transparent
                    ),
                    center = Offset(w * 0.18f, h * 0.36f),
                    radius = w * 0.30f * glowPulse
                ),
                radius = w * 0.30f * glowPulse,
                center = Offset(w * 0.18f, h * 0.36f)
            )

            // Pulsing cyan/magenta neon aura behind the '▶ TV' badge on the bottom right
            drawRoundRect(
                brush = Brush.horizontalGradient(
                    colors = listOf(
                        TvBrandCyan.copy(alpha = 0.24f * glowPulse),
                        Color(0xFFFF38E8).copy(alpha = 0.24f * glowPulse)
                    ),
                    startX = w * 0.54f,
                    endX = w * 0.96f
                ),
                topLeft = Offset(w * 0.54f, h * 0.62f),
                size = Size(w * 0.43f, h * 0.33f),
                cornerRadius = CornerRadius(w * 0.08f, w * 0.08f)
            )
        }

        // 2. Actual TanukiTV Brand Logo Image
        Image(
            painter = painterResource(id = R.drawable.tanukitv_logo),
            contentDescription = "TanukiTV Animated Logo",
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Fit
        )

        // 3. Synchronized Character Animations Overlaid on Exact Normalized Logo Coordinates
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height

            // --- A. Blinking Eyelids over Tanuki's Left & Right Eyes ---
            // Normalized coordinates from tanukitv_logo.png (919x592):
            // Left eye: (0.105f, 0.242f), Right eye: (0.189f, 0.214f)
            if (blinkAmount > 0.05f) {
                val leftEyeCenter = Offset(w * 0.105f, h * 0.242f)
                val rightEyeCenter = Offset(w * 0.189f, h * 0.214f)
                val eyeRx = w * 0.017f
                val eyeRy = h * 0.029f * blinkAmount

                // Mask patch brown color covering the open eye during blink
                val maskBrown = Color(0xFF69311D)
                val lashDark = Color(0xFF231116)

                drawOval(
                    color = maskBrown,
                    topLeft = Offset(leftEyeCenter.x - eyeRx, leftEyeCenter.y - eyeRy),
                    size = Size(eyeRx * 2f, eyeRy * 2f)
                )
                drawOval(
                    color = maskBrown,
                    topLeft = Offset(rightEyeCenter.x - eyeRx, rightEyeCenter.y - eyeRy),
                    size = Size(eyeRx * 2f, eyeRy * 2f)
                )

                // Cute happy curved closed-eye line when fully blinked
                if (blinkAmount > 0.55f) {
                    drawArc(
                        color = lashDark,
                        startAngle = 15f,
                        sweepAngle = 150f,
                        useCenter = false,
                        topLeft = Offset(leftEyeCenter.x - eyeRx * 0.85f, leftEyeCenter.y - eyeRy * 0.4f),
                        size = Size(eyeRx * 1.7f, eyeRy * 0.9f),
                        style = Stroke(width = w * 0.0045f, cap = StrokeCap.Round)
                    )
                    drawArc(
                        color = lashDark,
                        startAngle = 15f,
                        sweepAngle = 150f,
                        useCenter = false,
                        topLeft = Offset(rightEyeCenter.x - eyeRx * 0.85f, rightEyeCenter.y - eyeRy * 0.4f),
                        size = Size(eyeRx * 1.7f, eyeRy * 0.9f),
                        style = Stroke(width = w * 0.0045f, cap = StrokeCap.Round)
                    )
                }
            }

            // --- B. Cheerfully Waving Tanuki Paw above the 'T' ---
            // Anchored at the Tanuki's right shoulder/paw perch (0.258f, 0.268f)
            val pawPivot = Offset(w * 0.258f, h * 0.268f)
            rotate(degrees = pawWaveAngle, pivot = pawPivot) {
                val armW = w * 0.048f
                val armH = h * 0.095f
                val armLeft = pawPivot.x - armW * 0.48f
                val armTop = pawPivot.y - armH * 0.88f

                // Dark brown comic outline of waving arm/paw
                drawRoundRect(
                    color = Color(0xFF281219),
                    topLeft = Offset(armLeft - w * 0.005f, armTop - w * 0.005f),
                    size = Size(armW + w * 0.010f, armH + w * 0.010f),
                    cornerRadius = CornerRadius(armW * 0.55f, armW * 0.55f)
                )
                // Warm golden-chestnut fur on lower arm
                drawRoundRect(
                    color = Color(0xFFDF8743),
                    topLeft = Offset(armLeft, armTop + armH * 0.22f),
                    size = Size(armW, armH * 0.78f),
                    cornerRadius = CornerRadius(armW * 0.5f, armW * 0.5f)
                )
                // Dark brown Tanuki paw tip
                drawRoundRect(
                    color = Color(0xFF8C4622),
                    topLeft = Offset(armLeft, armTop),
                    size = Size(armW, armH * 0.58f),
                    cornerRadius = CornerRadius(armW * 0.5f, armW * 0.5f)
                )
                // Cute light peach-tan paw pad & 3 toe beans
                val padCenter = Offset(armLeft + armW * 0.5f, armTop + armH * 0.34f)
                drawCircle(
                    color = Color(0xFFF3B38A),
                    radius = armW * 0.19f,
                    center = padCenter
                )
                drawCircle(
                    color = Color(0xFFF3B38A),
                    radius = armW * 0.09f,
                    center = Offset(padCenter.x - armW * 0.20f, padCenter.y - armH * 0.14f)
                )
                drawCircle(
                    color = Color(0xFFF3B38A),
                    radius = armW * 0.09f,
                    center = Offset(padCenter.x, padCenter.y - armH * 0.18f)
                )
                drawCircle(
                    color = Color(0xFFF3B38A),
                    radius = armW * 0.09f,
                    center = Offset(padCenter.x + armW * 0.20f, padCenter.y - armH * 0.14f)
                )
            }

            // Cute animated motion arcs ('))') next to the waving paw
            val waveIntensity = ((kotlin.math.abs(pawWaveAngle) / 28f)).coerceIn(0.2f, 1f)
            drawArc(
                color = TvBrandCyan.copy(alpha = 0.75f * waveIntensity),
                startAngle = -55f,
                sweepAngle = 75f,
                useCenter = false,
                topLeft = Offset(w * 0.265f, h * 0.145f),
                size = Size(w * 0.065f, h * 0.095f),
                style = Stroke(width = w * 0.0055f, cap = StrokeCap.Round)
            )
            drawArc(
                color = TvGoldStar.copy(alpha = 0.65f * waveIntensity),
                startAngle = -50f,
                sweepAngle = 68f,
                useCenter = false,
                topLeft = Offset(w * 0.282f, h * 0.132f),
                size = Size(w * 0.075f, h * 0.110f),
                style = Stroke(width = w * 0.0045f, cap = StrokeCap.Round)
            )

            // --- C. Pulsing Antenna Energy Beacons on Tanuki's Head ---
            // Left antenna bulb: (0.060f, 0.065f), Right antenna bulb: (0.175f, 0.035f)
            val leftAntenna = Offset(w * 0.060f, h * 0.065f)
            val rightAntenna = Offset(w * 0.175f, h * 0.035f)
            val pulseRadius = w * 0.018f * glowPulse

            drawCircle(
                color = TvBrandCyan.copy(alpha = 0.45f * (1.25f - (glowPulse - 0.8f))),
                radius = pulseRadius * 1.45f,
                center = leftAntenna,
                style = Stroke(width = w * 0.0035f)
            )
            drawCircle(
                color = TvGoldStar.copy(alpha = 0.85f),
                radius = w * 0.007f * glowPulse,
                center = leftAntenna
            )

            drawCircle(
                color = TvBrandCyan.copy(alpha = 0.45f * (1.25f - (glowPulse - 0.8f))),
                radius = pulseRadius * 1.45f,
                center = rightAntenna,
                style = Stroke(width = w * 0.0035f)
            )
            drawCircle(
                color = TvGoldStar.copy(alpha = 0.85f),
                radius = w * 0.007f * glowPulse,
                center = rightAntenna
            )

            // --- D. Tail Wag Motion Lines near the Bushy Striped Tail ---
            val tailSwayAlpha = ((sin(Math.toRadians(cycleAngle.toDouble() * 2.0)) + 1.0) / 2.0).toFloat()
            drawArc(
                color = TvGoldStar.copy(alpha = 0.55f * tailSwayAlpha),
                startAngle = -35f,
                sweepAngle = 70f,
                useCenter = false,
                topLeft = Offset(w * 0.485f, h * 0.625f),
                size = Size(w * 0.065f, h * 0.14f),
                style = Stroke(width = w * 0.005f, cap = StrokeCap.Round)
            )

            // --- E. Floating Spirit Sparkles around the Logo ---
            val rad = Math.toRadians(cycleAngle.toDouble())
            val sparkle1 = Offset(
                w * 0.36f + (cos(rad) * w * 0.015f).toFloat(),
                h * 0.18f + (sin(rad) * h * 0.025f).toFloat()
            )
            val sparkle2 = Offset(
                w * 0.92f + (sin(rad) * w * 0.012f).toFloat(),
                h * 0.22f + (cos(rad) * h * 0.020f).toFloat()
            )
            drawCircle(color = TvBrandCyan.copy(alpha = 0.85f), radius = w * 0.006f * glowPulse, center = sparkle1)
            drawCircle(color = TvGoldStar.copy(alpha = 0.85f), radius = w * 0.0055f * glowPulse, center = sparkle2)
        }
    }
}
