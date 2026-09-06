package rk.or.core

import kotlin.math.PI
import kotlin.math.cos

/** Maps normalized time t (usually 0..1, can under/overshoot) to interpolated time. */
typealias Interp = (Double) -> Double

/**
 * Port of js/Interpolator.js. Use Padé approximations for fun, same as the JS source.
 */
object Interpolator {
    // Linear "il"
    val LinearInterpolator: Interp = { t -> t }

    // Starts and ends slowly accelerate between "iad"
    val AccelerateDecelerateInterpolator: Interp = { t -> (cos((t + 1) * PI) / 2) + 0.5 }

    // Model of a spring with overshoot "iso"
    val SpringOvershootInterpolator: Interp = { t ->
        when {
            t < 0.1825 -> (((-237.11 * t) + 61.775) * t + 3.664) * t
            t < 0.425 -> (((74.243 * t) - 72.681) * t + 21.007) * t - 0.579
            t < 0.6875 -> (((-16.378 * t) + 28.574) * t - 15.913) * t + 3.779
            t < 1 -> (((5.12 * t) - 12.800) * t + 10.468) * t - 1.788
            else -> (((-176.823 * t) + 562.753) * t - 594.598) * t + 209.669
        }
    }

    // Model of a spring with bounce "isb"; 1-Math.exp(-4*t)*Math.cos(2*Math.PI*t)
    val SpringBounceInterpolator: Interp = { t ->
        val x = when {
            t < 0.185 -> (((-94.565 * t) + 28.123) * t + 2.439) * t
            t < 0.365 -> (((-3.215 * t) - 4.890) * t + 5.362) * t + 0.011
            t < 0.75 -> (((5.892 * t) - 10.432) * t + 5.498) * t + 0.257
            t < 1 -> (((1.52 * t) - 2.480) * t + 0.835) * t + 1.125
            else -> (((-299.289 * t) + 945.190) * t - 991.734) * t + 346.834
        }
        if (x > 1) 2 - x else x
    }

    // Model of gravity with bounce "igb"; a = 8, k=1.5; x=(a*t*t-v0*t)*Math.exp(-k*t);
    val GravityBounceInterpolator: Interp = { t ->
        val x = when {
            t < 0.29 -> (((-14.094 * t) + 9.810) * t - 0.142) * t
            t < 0.62 -> (((-16.696 * t) + 21.298) * t - 6.390) * t + 0.909
            t < 0.885 -> (((31.973 * t) - 74.528) * t + 56.497) * t + -12.844
            t < 1 -> (((-37.807 * t) + 114.745) * t - 114.938) * t + 39
            else -> (((-7278.029 * t) + 22213.034) * t - 22589.244) * t + 7655.239
        }
        if (x > 1) 2 - x else x
    }

    private fun bounce(t: Double): Double = t * t * 8

    // Bounce at the end "ib"
    val BounceInterpolator: Interp = { t0 ->
        val t = t0 * 1.1226
        when {
            t < 0.3535 -> bounce(t)
            t < 0.7408 -> bounce(t - 0.54719) + 0.7
            t < 0.9644 -> bounce(t - 0.8526) + 0.9
            else -> bounce(t - 1.0435) + 0.95
        }
    }

    // Overshoot "io"
    val OvershootInterpolator: Interp = { t0 ->
        val mTension = 2.0
        val t = t0 - 1
        t * t * ((mTension + 1) * t + mTension) + 1
    }

    // Anticipate "ia"
    val AnticipateInterpolator: Interp = { t ->
        val mTension = 2.0
        t * t * ((mTension + 1) * t - mTension)
    }

    private fun a(t: Double, s: Double): Double = t * t * ((s + 1) * t - s)
    private fun o(t: Double, s: Double): Double = t * t * ((s + 1) * t + s)

    // Anticipate Overshoot "iao"
    val AnticipateOvershootInterpolator: Interp = { t ->
        val mTension = 1.5
        if (t < 0.5) 0.5 * a(t * 2, mTension) else 0.5 * (o(t * 2 - 2, mTension) + 2)
    }
}
