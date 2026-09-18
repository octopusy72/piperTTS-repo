package com.yiwoosolution.piperprototype;

import java.util.Arrays;
import sonic.Sonic;

/** Independent rate/pitch adjustment; default playback remains bit-identical. */
public final class SpeechPcmProcessor {
  private SpeechPcmProcessor() {}
  public static float bounded(float value) {
    if (Float.isNaN(value) || Float.isInfinite(value) || value <= 0) return 1f;
    return Math.max(0.25f, Math.min(4f, value));
  }
  public static short[] process(short[] pcm, int sampleRate, float rate, float pitch) {
    rate = bounded(rate);
    pitch = bounded(pitch);
    if (pcm.length == 0 || (rate == 1f && pitch == 1f)) return pcm;
    Sonic sonic = new Sonic(sampleRate, 1);
    sonic.setSpeed(rate);
    sonic.setPitch(pitch);
    sonic.writeShortToStream(pcm, pcm.length);
    sonic.flushStream();
    short[] out = new short[sonic.samplesAvailable()];
    int count = sonic.readShortFromStream(out, out.length);
    return count == out.length ? out : Arrays.copyOf(out, count);
  }
}
