package com.yiwoosolution.piperprototype;

import android.content.Context;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/** Isolated A/B experiment; not used by the production normalizer. */
final class EnglishToKoreanPronunciationPrototype {
  static final class Result {
    final String original, current, espeak, approximation;
    Result(String original, String current, String espeak, String approximation) {
      this.original = original; this.current = current; this.espeak = espeak; this.approximation = approximation;
    }
  }

  private final Context context;
  private final Map<String, String> loanwords = new HashMap<>();
  private static final Map<String, String> PROTOTYPE_CONVENTIONAL = new HashMap<>();
  static {
    PROTOTYPE_CONVENTIONAL.put("website", "웹사이트");
    PROTOTYPE_CONVENTIONAL.put("client", "클라이언트");
    PROTOTYPE_CONVENTIONAL.put("application", "애플리케이션");
    PROTOTYPE_CONVENTIONAL.put("program", "프로그램");
    PROTOTYPE_CONVENTIONAL.put("microsoft", "마이크로소프트");
    PROTOTYPE_CONVENTIONAL.put("chatgpt", "챗지피티");
    PROTOTYPE_CONVENTIONAL.put("machine learning", "머신 러닝");
    PROTOTYPE_CONVENTIONAL.put("deep learning", "딥 러닝");
    PROTOTYPE_CONVENTIONAL.put("artificial intelligence", "인공지능");
    PROTOTYPE_CONVENTIONAL.put("computer science", "컴퓨터 사이언스");
    PROTOTYPE_CONVENTIONAL.put("smart phone", "스마트폰");
  }
  private static final Set<String> PROTECTED = new HashSet<>(Arrays.asList(
      "ai", "cpu", "gpu", "usb-c", "wi-fi", "bluetooth", "android", "onnx",
      "gtx-a", "gtx-b", "gtx-c", "google", "youtube", "openai"));
  EnglishToKoreanPronunciationPrototype(Context context) {
    this.context = context.getApplicationContext();
    try (BufferedReader reader = new BufferedReader(new InputStreamReader(
        this.context.getAssets().open("normalization/android_loanwords.tsv"), "UTF-8"))) {
      String line;
      while ((line = reader.readLine()) != null) {
        String[] fields = line.split("\\t", 2);
        if (fields.length == 2 && !fields[0].isEmpty()) loanwords.put(fields[0].toLowerCase(Locale.US), fields[1]);
      }
    } catch (Exception ignored) { }
  }

  Result convert(String input) throws Exception {
    String current = LegacyTextNormalizer.normalize(context, input);
    String espeak = EnglishEspeak.phonemize(context, input);
    String approximation = approximate(input, espeak);
    return new Result(input, current, espeak, approximation);
  }

  private String approximate(String input, String ipa) {
    // Existing uppercase/product forms remain protected; this prototype only
    // evaluates ordinary lexical English words and phrases.
    if (!input.matches("(?i)[a-z]+(?:[-][a-z]+|\\s+[a-z]+)*")) return LegacyTextNormalizer.normalize(context, input);
    String lower = input.toLowerCase(Locale.US).trim();
    if (PROTECTED.contains(lower)) return LegacyTextNormalizer.normalize(context, input);
    if (PROTOTYPE_CONVENTIONAL.containsKey(lower)) return PROTOTYPE_CONVENTIONAL.get(lower);
    if (!lower.contains(" ") && loanwords.containsKey(lower)) return loanwords.get(lower);
    return syllabify(ipa);
  }

  private static String syllabify(String raw) {
    String s = raw.replace("ˈ", "").replace("ˌ", "").replace("ː", "").replace("'", "").trim();
    StringBuilder out = new StringBuilder();
    String onset = "";
    for (int i = 0; i < s.length();) {
      String symbol = symbolAt(s, i); i += symbol.length();
      if (isVowel(symbol)) {
        String next = "";
        if (i < s.length()) next = symbolAt(s, i);
        String coda = "";
        // Keep one consonant as onset of the next syllable when another vowel
        // follows; otherwise it is the final coda of this syllable.
        int look = i;
        if (!next.isEmpty() && !isVowel(next)) {
          int after = look + next.length();
          if (after < s.length() && isVowel(symbolAt(s, after))) { onset = onset + mapOnset(symbol); onset += ""; }
          else { coda = mapCoda(next); i = after; }
        }
        appendSyllable(out, onset, mapVowel(symbol), coda);
        onset = "";
      } else if (symbol.equals(" ")) {
        if (out.length() > 0) out.append(' ');
        onset = "";
      } else {
        onset += mapOnset(symbol);
      }
    }
    return out.toString().isEmpty() ? raw : out.toString();
  }

  private static String symbolAt(String s, int i) {
    if (i + 1 < s.length()) {
      String pair = s.substring(i, i + 2);
      if (pair.equals("tʃ") || pair.equals("dʒ") || pair.equals("aɪ") || pair.equals("aʊ")
          || pair.equals("eɪ") || pair.equals("ɔɪ") || pair.equals("oʊ") || pair.equals("əʊ")) return pair;
    }
    return String.valueOf(s.charAt(i));
  }
  private static boolean isVowel(String s) {
    return s.equals("a") || s.equals("ɑ") || s.equals("æ") || s.equals("ʌ") || s.equals("ə")
        || s.equals("ɜ") || s.equals("ɪ") || s.equals("i") || s.equals("ɛ") || s.equals("e")
        || s.equals("ɔ") || s.equals("o") || s.equals("ʊ") || s.equals("u")
        || s.equals("aɪ") || s.equals("aʊ") || s.equals("eɪ") || s.equals("ɔɪ") || s.equals("oʊ") || s.equals("əʊ");
  }
  private static String mapVowel(String s) {
    if (s.equals("i") || s.equals("ɪ") || s.equals("eɪ")) return "ㅣ";
    if (s.equals("e") || s.equals("ɛ") || s.equals("æ")) return "ㅔ";
    if (s.equals("a") || s.equals("ɑ") || s.equals("ʌ")) return "ㅏ";
    if (s.equals("ə") || s.equals("ɜ")) return "ㅓ";
    if (s.equals("ɔ") || s.equals("o") || s.equals("oʊ")) return "ㅗ";
    if (s.equals("ʊ") || s.equals("u") || s.equals("əʊ")) return "ㅜ";
    if (s.equals("aɪ") || s.equals("aʊ") || s.equals("ɔɪ")) return "ㅏ";
    return "ㅓ";
  }
  private static String mapOnset(String s) {
    if (s.equals("p")) return "ㅍ"; if (s.equals("b")) return "ㅂ"; if (s.equals("t")) return "ㅌ";
    if (s.equals("d")) return "ㄷ"; if (s.equals("k")) return "ㅋ"; if (s.equals("g")) return "ㄱ";
    if (s.equals("m")) return "ㅁ"; if (s.equals("n")) return "ㄴ"; if (s.equals("ŋ")) return "ㅇ";
    if (s.equals("l") || s.equals("r")) return "ㄹ"; if (s.equals("f")) return "ㅍ"; if (s.equals("v")) return "ㅂ";
    if (s.equals("θ")) return "ㅅ"; if (s.equals("ð")) return "ㄷ"; if (s.equals("s")) return "ㅅ";
    if (s.equals("z")) return "ㅈ"; if (s.equals("ʃ")) return "ㅅ"; if (s.equals("ʒ")) return "ㅈ";
    if (s.equals("tʃ")) return "ㅊ"; if (s.equals("dʒ")) return "ㅈ"; if (s.equals("h")) return "ㅎ";
    if (s.equals("w")) return "ㅇ"; if (s.equals("j")) return "ㅇ"; return "";
  }
  private static String mapCoda(String s) {
    if (s.equals("m")) return "ㅁ"; if (s.equals("n")) return "ㄴ"; if (s.equals("ŋ")) return "ㅇ";
    if (s.equals("p") || s.equals("b") || s.equals("f")) return "ㅂ";
    if (s.equals("t") || s.equals("d") || s.equals("s") || s.equals("z")) return "ㅅ";
    if (s.equals("k") || s.equals("g")) return "ㄱ"; if (s.equals("l") || s.equals("r")) return "ㄹ"; return "";
  }
  private static void appendSyllable(StringBuilder out, String onset, String vowel, String coda) {
    if (onset.length() > 1) {
      for (int i = 0; i < onset.length() - 1; i++) appendSyllable(out, String.valueOf(onset.charAt(i)), "ㅡ", "");
      onset = String.valueOf(onset.charAt(onset.length() - 1));
    }
    if (vowel.length() > 1) { out.append(onset).append(vowel); return; }
    int l = "ㄱㄲㄴㄷㄸㄹㅁㅂㅃㅅㅆㅇㅈㅉㅊㅋㅌㅍㅎ".indexOf(onset);
    int v = "ㅏㅐㅑㅒㅓㅔㅕㅖㅗㅘㅙㅚㅛㅜㅝㅞㅟㅠㅡㅢㅣ".indexOf(vowel);
    int t = " ㄱㄲㄳㄴㄵㄶㄷㄹㄺㄻㄼㄽㄾㄿㅀㅁㅂㅄㅅㅆㅇㅈㅊㅋㅌㅍㅎ".indexOf(coda);
    if (l >= 0 && v >= 0 && t >= 0) out.append((char)(0xAC00 + (l * 21 + v) * 28 + t));
    else out.append(onset).append(vowel).append(coda);
  }
}
