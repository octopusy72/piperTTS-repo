package com.yiwoosolution.piperprototype;

import android.content.Context;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.IOException;

/** Same-voice contextual recordings for finite list labels, loaded only on demand. */
final class ListLabelAudio {
  static short[] read(Context context, String normalized, int sampleRate, float rate) throws Exception {
    if (sampleRate != 16000) throw new IOException("LIST_LABEL_SAMPLE_RATE_MISMATCH");
    String label = normalized.replace(".", "").trim();
    if (!label.matches("[가-힣]+")) throw new IOException("INVALID_LIST_LABEL");
    byte[] data;
    try (InputStream in = context.getAssets().open("list_labels/" + label + ".pcm");
         ByteArrayOutputStream out = new ByteArrayOutputStream()) {
      byte[] buffer = new byte[4096];
      for (int count; (count = in.read(buffer)) != -1;) out.write(buffer, 0, count);
      data = out.toByteArray();
    }
    return atRate(decode(data), sampleRate, rate);
  }

  static short[] atRate(short[] pcm, int sampleRate, float rate) throws IOException {
    // Do not let a high speech-rate setting compress a label back into a click.
    int activeMs = ShortSpeechGuard.activeSpeechMs(pcm, sampleRate);
    float effectiveRate = Math.min(SpeechPcmProcessor.bounded(rate), activeMs / 240f);
    short[] result = SpeechPcmProcessor.process(pcm, sampleRate, effectiveRate, 1f);
    if (ShortSpeechGuard.activeSpeechMs(result, sampleRate) < 180)
      throw new IOException("LIST_LABEL_TOO_SHORT_AFTER_RATE");
    return result;
  }

  static short[] decode(byte[] data) throws IOException {
    if (data.length == 0 || data.length % 2 != 0) throw new IOException("INVALID_LIST_LABEL_PCM");
    short[] pcm = new short[data.length / 2];
    for (int i = 0; i < pcm.length; i++)
      pcm[i] = (short)((data[2*i] & 255) | ((data[2*i+1] & 255) << 8));
    if (ShortSpeechGuard.activeSpeechMs(pcm, 16000) < 200)
      throw new IOException("SHORT_OR_SILENT_LIST_LABEL_PCM");
    return pcm;
  }
}
