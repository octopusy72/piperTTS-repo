package com.yiwoosolution.piperprototype;

/** Model-input context and waveform validation for isolated short Korean speech. */
final class ShortSpeechGuard {
  static boolean applies(String text) {
    int syllables = 0;
    for (int i = 0; i < text.length(); i++) {
      char c = text.charAt(i);
      if (c >= '\uAC00' && c <= '\uD7A3') syllables++;
      else if (!Character.isWhitespace(c) && ".,!?".indexOf(c) < 0) return false;
    }
    return syllables > 0 && syllables <= 4;
  }

  static boolean hasSpeech(short[] pcm, int sampleRate) {
    return activeSpeechMs(pcm, sampleRate) >= 30;
  }

  static int activeSpeechMs(short[] pcm, int sampleRate) {
    // Count energetic 10 ms windows, not samples or total duration: a long
    // almost-silent model output must not pass as successful speech.
    int window = Math.max(1, sampleRate / 100), active = 0;
    for (int start = 0; start < pcm.length; start += window) {
      int end = Math.min(pcm.length, start + window);
      double energy = 0;
      for (int i = start; i < end; i++) energy += (double) pcm[i] * pcm[i];
      if (Math.sqrt(energy / (end - start)) >= 100) active++;
    }
    return active * 10;
  }
}
