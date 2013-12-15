package rk.or;

import android.util.FloatMath;

/** 
 * Maps time to time 
 * interpolate(tn) returns t for tn. 
 * t and tn should start at 0.0 and end at 1.0
 * between 0 and 1, t can be < 0 (anticipate) and >1 (overshoot) 
 */
public interface Interpolator {
  float interpolate(float t);
}

/** Linear */
class LinearInterpolator implements Interpolator {
  public float interpolate(float t) {
    return t;
  }
}

/** Starts and ends slowly accelerate between "iad" */
class AccelerateDecelerateInterpolator implements Interpolator {
  public float interpolate(float t) {
    return ((FloatMath.cos((t + 1) * (float)Math.PI) / 2.0f) + 0.5f);
  }
}
/** Model of a spring with overshoot "iso" */
class SpringOvershootInterpolator implements Interpolator {
  public float interpolate(float t) {
    if (t < 0.1825f)
      return (((-237.110f*t)+61.775f)*t+3.664f)*t+0.000f;
    if (t < 0.425f)
      return (((74.243f*t)-72.681f)*t+21.007f)*t-0.579f;
    if (t < 0.6875f)
      return (((-16.378f*t)+28.574f)*t-15.913f)*t+3.779f;
    if (t < 1.0f)
      return (((5.120f*t)-12.800f)*t+10.468f)*t-1.788f;
    return (((-176.823f*t)+562.753f)*t-594.598f)*t+209.669f;
  }
}
/** Model of a spring with bounce "isb" */
/** 1.0-Math.exp(-4.0*t)*Math.cos(2*Math.PI*t) */
class SpringBounceInterpolator implements Interpolator {
  public float interpolate(float t) {
    float x=0.0f;
    if (t < 0.185f)
      x=(((-94.565f*t)+28.123f)*t+2.439f)*t+0.000f;
    else if (t < 0.365f)
      x=(((-3.215f*t)-4.890f)*t+5.362f)*t+0.011f;
    else if (t < 0.75f)
      x=(((5.892f*t)-10.432f)*t+5.498f)*t+0.257f;
    else if (t < 1.0f)
      x=(((1.520f*t)-2.480f)*t+0.835f)*t+1.125f;
    else x=(((-299.289f*t)+945.190f)*t-991.734f)*t+346.834f;
    return x > 1 ? 2-x : x;
  }
}
/** Model of a gravity with bounce "igb" */
/**  a = 8.0, k=1.5; x=(a*t*t-v0*t)*Math.exp(-k*t); */
class GravityBounceInterpolator implements Interpolator {
  public float interpolate(float t) {
    float x=0.0f;
    if (t < 0.29f)
      x=(((-14.094f*t)+9.810f)*t-0.142f)*t+0.000f;
    else if (t < 0.62f)
      x=(((-16.696f*t)+21.298f)*t-6.390f)*t+0.909f;
    else if (t < 0.885f)
      x=(((31.973f*t)-74.528f)*t+56.497f)*t+-12.844f;
    else if (t < 1.0f)
      x=(((-37.807f*t)+114.745f)*t-114.938f)*t+39.000f;
    else x=(((-7278.029f*t)+22213.034f)*t-22589.244f)*t+7655.239f;
    return x>1?2-x:x;
  }
}

/** Bounce at the end "ib" */
class BounceInterpolator implements Interpolator {
  public float interpolate(float t) {
    t *= 1.1226f;
    if (t < 0.3535f) return bounce(t);
    else if (t < 0.7408f) return bounce(t - 0.54719f) + 0.7f;
    else if (t < 0.9644f) return bounce(t - 0.8526f) + 0.9f;
    else return bounce(t - 1.0435f) + 0.95f;
  }
  private static float bounce(float t) {
    return t * t * 8.0f;
  }
}

/** Overshoot "io" */
class OvershootInterpolator implements Interpolator {
  private final float mTension;
  /** With tension = 2.0 */
  public OvershootInterpolator() {
    mTension = 2.0f;
  }
  /** With tension =0 no overshoot, just deceleration 2.0 classic */
  public OvershootInterpolator(float tension) {
    mTension = tension;
  }
  public float interpolate(float t) {
    t -= 1.0f;
    return t * t * ((mTension + 1) * t + mTension) + 1.0f;
  }  
}

/** Anticipate "ia" */
class AnticipateInterpolator implements Interpolator{
  private final float mTension;
  /** With tension =0 no overshoot, just deceleration 2.0 classic */
  public AnticipateInterpolator(float tension) {
    mTension = tension;
  }
  public float interpolate(float t) {
    return t * t * ((mTension + 1) * t - mTension);
  }
}

/** Anticipate Overshoot "iao" */
class AnticipateOvershootInterpolator implements Interpolator{
  private final float mTension;
  /** With tension =0 no overshoot, just deceleration 2.0 classic */
  public AnticipateOvershootInterpolator(float tension) {
    mTension = tension*1.5f;
  }
  private static float a(float t, float s) {
    return t * t * ((s + 1) * t - s);
  }
  private static float o(float t, float s) {
    return t * t * ((s + 1) * t + s);
  }
  public float interpolate(float t) {
    if (t < 0.5f) return 0.5f * a(t * 2.0f, mTension);
    else return 0.5f * (o(t * 2.0f - 2.0f, mTension) + 2.0f);
  }
}



