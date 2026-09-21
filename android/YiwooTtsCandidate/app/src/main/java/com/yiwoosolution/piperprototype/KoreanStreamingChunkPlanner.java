package com.yiwoosolution.piperprototype;

import java.util.ArrayList;
import java.util.List;
import java.util.function.ToIntFunction;
import java.util.function.UnaryOperator;
import java.util.regex.Pattern;

/** Performance-adaptive planning without introducing arbitrary short word fragments. */
final class KoreanStreamingChunkPlanner {
  private static final int MODEL_SAFETY_PHONEMES = 256;
  private static final int MIN_SPLIT_PHONEMES = 24;
  private static final double FIRST_INFERENCE_TARGET_MS = 1500;
  private static final Pattern CLAUSE_END = Pattern.compile(
      "[가-힣]{2,}(?:하며|이며|하고|되고|있고|지만|으며|해서|하여|하면|되면|다면|으므로|으면서)");

  static List<SentenceBoundaryPausePolicy.Segment> plan(String text,
      UnaryOperator<String> normalize, ToIntFunction<String> count,
      SynthesisPerformance.Snapshot performance, float rate, int pauseMs) {
    List<SentenceBoundaryPausePolicy.Segment> result = new ArrayList<>();
    double audioAhead = 0, previousAudio = 0;
    for (SentenceBoundaryPausePolicy.Segment sentence : contextualSegments(text, normalize, count)) {
      // Preserve numeric/counter context before splitting at any additional boundary.
      String remaining = sentence.text;
      while (!remaining.isEmpty()) {
        double budget = result.isEmpty() ? FIRST_INFERENCE_TARGET_MS : audioAhead * 0.85;
        int cut = chooseBoundary(remaining, count, performance, rate, budget);
        String piece = trimWhitespace(remaining.substring(0, cut));
        remaining = trimWhitespace(remaining.substring(cut));
        SentenceBoundaryPausePolicy.Boundary boundary = remaining.isEmpty()
            ? sentence.boundary : SentenceBoundaryPausePolicy.Boundary.NONE;
        result.add(new SentenceBoundaryPausePolicy.Segment(piece, boundary));
        int phones = count.applyAsInt(piece);
        double audio = performance.audioMs(phones, rate);
        if (boundary == SentenceBoundaryPausePolicy.Boundary.SENTENCE) audio += pauseMs;
        if (boundary == SentenceBoundaryPausePolicy.Boundary.STRONG) audio += Math.min(1000, Math.round(pauseMs * 1.8));
        // Respect bounded prefetch: don't assume the whole request is buffered.
        audioAhead = Math.min(previousAudio + audio,
            Math.max(0, audioAhead - performance.inferenceMs(phones, rate)) + audio);
        previousAudio = audio;
      }
    }
    return result;
  }

  /** Normalize each semantic unit first, then give tiny units neighboring acoustic context.
   * Internal punctuation remains in the model input; only the final boundary adds PCM silence.
   * Oversized merged units are still subject to the existing model safety split.
   */
  static List<SentenceBoundaryPausePolicy.Segment> contextualSegments(String text,
      UnaryOperator<String> normalize, ToIntFunction<String> count) {
    List<SentenceBoundaryPausePolicy.Segment> units = new ArrayList<>();
    for (SentenceBoundaryPausePolicy.Segment source : SentenceBoundaryPausePolicy.segment(text)) {
      String value = trimWhitespace(normalize.apply(source.text));
      if (!value.isEmpty()) units.add(new SentenceBoundaryPausePolicy.Segment(value, source.boundary));
    }
    for (int i = 0; i < units.size();) {
      if (units.size() == 1 || count.applyAsInt(units.get(i).text) >= MIN_SPLIT_PHONEMES) {
        i++;
        continue;
      }
      int left = i + 1 < units.size() ? i : i - 1;
      SentenceBoundaryPausePolicy.Segment a = units.get(left), b = units.get(left + 1);
      String last = a.text.substring(a.text.length() - 1);
      // A bare line break needs an audible delimiter rather than word concatenation.
      String delimiter = ".!?…,:;。！？".contains(last) ? " " : ", ";
      units.set(left, new SentenceBoundaryPausePolicy.Segment(a.text + delimiter + b.text, b.boundary));
      units.remove(left + 1);
      i = left;
    }
    return units;
  }

  private static int chooseBoundary(String text, ToIntFunction<String> count,
      SynthesisPerformance.Snapshot performance, float rate, double budget) {
    int wholePhones = count.applyAsInt(text);
    boolean safe = wholePhones <= MODEL_SAFETY_PHONEMES;
    if (safe && (!performance.calibrated() || performance.inferenceMs(wholePhones, rate) <= budget))
      return text.length();
    int first = 0, best = 0, wordStart = 0;
    for (int i = 0; i < text.length(); i++) {
      char c = text.charAt(i);
      // A short introductory label needs the following words as acoustic context.
      boolean punctuation = (c == ',' || c == ';' || c == '，' || c == '；') && i > 4;
      boolean whitespace = isWhitespace(c);
      boolean clause = whitespace && CLAUSE_END.matcher(text.substring(wordStart, i)).matches();
      int end = punctuation ? i + 1 : i;
      if ((punctuation || clause) && end > 0 && end < text.length()) {
        int phones = count.applyAsInt(text.substring(0, end));
        if (phones > MODEL_SAFETY_PHONEMES) break;
        if (phones < MIN_SPLIT_PHONEMES
            || count.applyAsInt(text.substring(end)) < MIN_SPLIT_PHONEMES) continue;
        if (first == 0) first = end;
        if (performance.inferenceMs(phones, rate) <= budget) best = end;
      }
      if (whitespace || punctuation) wordStart = i + 1;
    }
    if (best > 0) return best;
    if (first > 0) return first;
    // No meaningful boundary: preserve the sentence, even if TTFA exceeds its target.
    // Only the pre-existing model safety limit permits an ordinary word split.
    return safe ? text.length() : safetyCut(text, count);
  }

  static int safetyCut(String text, ToIntFunction<String> count) {
    int best = 0;
    for (int i = 1; i < text.length(); i++) {
      if (!isWhitespace(text.charAt(i))) continue;
      int head = count.applyAsInt(text.substring(0, i));
      if (head > MODEL_SAFETY_PHONEMES) break;
      if (head < MIN_SPLIT_PHONEMES) continue;
      if (count.applyAsInt(text.substring(i)) < MIN_SPLIT_PHONEMES) continue;
      best = i;
    }
    if (best > 0) return best;
    int cut = 0;
    for (int next = 0; next < text.length();) {
      next += Character.charCount(text.codePointAt(next));
      if (cut > 0 && count.applyAsInt(text.substring(0, next)) > MODEL_SAFETY_PHONEMES) break;
      cut = next;
    }
    // Rebalance a hard split so the final remainder is not an isolated syllable.
    while (cut > 1 && count.applyAsInt(text.substring(cut)) < MIN_SPLIT_PHONEMES) {
      cut = text.offsetByCodePoints(cut, -1);
    }
    return Math.max(1, cut);
  }

  private static String trimWhitespace(String text) {
    int start = 0, end = text.length();
    while (start < end && isWhitespace(text.charAt(start))) start++;
    while (end > start && isWhitespace(text.charAt(end - 1))) end--;
    return text.substring(start, end);
  }
  private static boolean isWhitespace(char c) {
    return Character.isWhitespace(c) || Character.isSpaceChar(c);
  }
}
