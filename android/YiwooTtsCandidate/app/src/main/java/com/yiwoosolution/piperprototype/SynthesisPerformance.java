package com.yiwoosolution.piperprototype;

/** Session-local measurements; accessed only under the Korean synthesis lock. */
final class SynthesisPerformance {
  private double weight, x, xx, inference, audio, xInference, xAudio;
  private int samples;

  void observe(int phones, double inferenceMs, double audioMs, float rate) {
    if (phones <= 0 || !Double.isFinite(inferenceMs) || !Double.isFinite(audioMs)
        || inferenceMs <= 0 || audioMs <= 0 || !Float.isFinite(rate) || rate <= 0) return;
    // Exponentially weighted linear fit separates per-call work from sentence length.
    // Otherwise a five-syllable warmup overpredicts every longer sentence.
    double decay = 0.9;
    weight = weight * decay + 1;
    x = x * decay + phones;
    xx = xx * decay + (double) phones * phones;
    inference = inference * decay + inferenceMs * rate;
    audio = audio * decay + audioMs * rate;
    xInference = xInference * decay + phones * inferenceMs * rate;
    xAudio = xAudio * decay + phones * audioMs * rate;
    samples++;
  }

  Snapshot snapshot() {
    if (samples == 0) return new Snapshot(0, 0, 0);
    double variance = xx - x * x / weight;
    double[] time = fit(inference, xInference, variance);
    double[] duration = fit(audio, xAudio, variance);
    return new Snapshot(samples, time[1], duration[1], time[0], duration[0], inference / audio);
  }

  private double[] fit(double y, double xy, double variance) {
    if (variance > 16) {
      double slope = (xy - x * y / weight) / variance;
      double intercept = (y - slope * x) / weight;
      if (slope > 0 && intercept >= 0) return new double[]{intercept, slope};
    }
    return new double[]{0, y / x};
  }

  static final class Snapshot {
    final int samples;
    final double inferencePerPhone, audioPerPhone;
    final double inferenceFixedMs, audioFixedMs, measuredRtf;
    Snapshot(int samples, double inferencePerPhone, double audioPerPhone) {
      this(samples, inferencePerPhone, audioPerPhone, 0, 0, inferencePerPhone / audioPerPhone);
    }
    Snapshot(int samples, double inferencePerPhone, double audioPerPhone,
        double inferenceFixedMs, double audioFixedMs, double measuredRtf) {
      this.samples = samples; this.inferencePerPhone = inferencePerPhone; this.audioPerPhone = audioPerPhone;
      this.inferenceFixedMs = inferenceFixedMs; this.audioFixedMs = audioFixedMs; this.measuredRtf = measuredRtf;
    }
    boolean calibrated() { return samples > 0; }
    double rtf() { return calibrated() ? measuredRtf : Double.NaN; }
    double inferenceMs(int phones, float rate) { return (inferenceFixedMs + inferencePerPhone * phones) / rate; }
    double audioMs(int phones, float rate) { return (audioFixedMs + audioPerPhone * phones) / rate; }
    long startupReserveMs(int nextPhones, float rate, double firstAudioMs) {
      if (!calibrated()) return 0;
      // Bounded, measured head start, not a fixed sleep or extra silence in PCM.
      return Math.round(Math.min(1500, Math.max(0, inferenceMs(nextPhones, rate) * 1.15 - firstAudioMs)));
    }
  }
}
