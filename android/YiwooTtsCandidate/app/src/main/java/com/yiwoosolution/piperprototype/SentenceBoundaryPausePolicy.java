package com.yiwoosolution.piperprototype;

import android.content.Context;
import java.util.ArrayList;
import java.util.List;

/** Shared, playback-only pause policy. It never changes frontend input. */
final class SentenceBoundaryPausePolicy {
  static final String PREFS = "speech_preferences";
  static final String KEY = "sentence_pause_ms";
  static final int MIN_MS = 0, MAX_MS = 1000, STEP_MS = 20, DEFAULT_MS = 300;
  private static final int STRONG_MULTIPLIER_TENTHS = 18;

  static int pauseMs(Context context) {
    return Math.max(MIN_MS, Math.min(MAX_MS, context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getInt(KEY, DEFAULT_MS)));
  }
  static void setPauseMs(Context context, int value) {
    int snapped = Math.max(MIN_MS, Math.min(MAX_MS, Math.round(value / (float) STEP_MS) * STEP_MS));
    context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putInt(KEY, snapped).apply();
  }
  static int effectivePause(Context context, Boundary boundary) {
    int base = pauseMs(context);
    if (boundary == Boundary.NONE || boundary == Boundary.WEAK) return 0;
    return boundary == Boundary.STRONG ? Math.min(1000, Math.round(base * STRONG_MULTIPLIER_TENTHS / 10f)) : base;
  }
  static short[] silence(int sampleRate, int pauseMs) {
    if (sampleRate <= 0 || pauseMs <= 0) return new short[0];
    long frames = Math.round(sampleRate * (pauseMs / 1000.0));
    if (frames <= 0) return new short[0];
    return new short[(int) Math.min(Integer.MAX_VALUE, frames)];
  }
  enum Boundary { NONE, WEAK, SENTENCE, STRONG }
  static final class Segment { final String text; final Boundary boundary; Segment(String text, Boundary boundary) { this.text = text; this.boundary = boundary; } }

  /** Splits only at semantic boundaries; punctuation inside protected numeric/URL forms is retained. */
  static List<Segment> segment(String input) {
    List<Segment> result = new ArrayList<>();
    if (input == null || input.trim().isEmpty()) return result;
    String text = input.replace("\r\n", "\n").replace('\r', '\n');
    int start = 0;
    for (int i = 0; i < text.length();) {
      char c = text.charAt(i);
      Boundary boundary = Boundary.NONE;
      int end = i + 1;
      if (c == '\n') {
        int count = 1; while (end < text.length() && text.charAt(end) == '\n') { count++; end++; }
        boundary = count >= 2 ? Boundary.STRONG : Boundary.SENTENCE;
      } else if (c == '…') {
        boundary = Boundary.STRONG;
      } else if (c == '.' && !internalPeriod(text, i)) {
        int count = 1; while (end < text.length() && text.charAt(end) == '.') { count++; end++; }
        boundary = count >= 3 ? Boundary.STRONG : Boundary.SENTENCE;
      } else if (c == '?' || c == '!') {
        while (end < text.length() && (text.charAt(end) == '?' || text.charAt(end) == '!')) end++;
        boundary = Boundary.SENTENCE;
      }
      if (boundary != Boundary.NONE) {
        int next = end;
        while (next < text.length() && Character.isWhitespace(text.charAt(next)) && text.charAt(next) != '\n') next++;
        // A punctuation followed by a newline is one boundary, with paragraph strength preserved.
        if (next < text.length() && text.charAt(next) == '\n') {
          int count = 0; while (next < text.length() && text.charAt(next) == '\n') { count++; next++; }
          if (count >= 2) boundary = Boundary.STRONG;
          end = next;
        }
        String piece = text.substring(start, end).trim();
        if (!piece.isEmpty()) result.add(new Segment(piece, boundary));
        start = end; i = end; continue;
      }
      i++;
    }
    String tail = text.substring(start).trim();
    if (!tail.isEmpty()) result.add(new Segment(tail, Boundary.NONE));
    // A boundary only separates speech segments; never add silence after the final segment.
    if (!result.isEmpty()) {
      Segment last = result.get(result.size() - 1);
      if (last.boundary != Boundary.NONE) result.set(result.size() - 1, new Segment(last.text, Boundary.NONE));
    }
    return result;
  }

  private static boolean internalPeriod(String text, int index) {
    char before = index > 0 ? text.charAt(index - 1) : 0;
    char after = index + 1 < text.length() ? text.charAt(index + 1) : 0;
    if (Character.isDigit(before) && Character.isDigit(after)) return true;
    // Dots between URL/email/domain characters are protected; a terminal dot remains a boundary.
    return (Character.isLetterOrDigit(before) || before == '_' || before == '-') &&
        (Character.isLetterOrDigit(after) || after == '_' || after == '-');
  }
}
