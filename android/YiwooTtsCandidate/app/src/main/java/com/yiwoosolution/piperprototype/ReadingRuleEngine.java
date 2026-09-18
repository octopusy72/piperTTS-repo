package com.yiwoosolution.piperprototype;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Legacy-compatible rule application. Structured spans are protected from user replacement. */
final class ReadingRuleEngine {
  private static final String DIGITS = "공일이삼사오육칠팔구";
  private static final Pattern PROTECTED = Pattern.compile("(?i)([A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}|https?://\\S+|\\b\\d{1,3}(?:\\.\\d{1,3}){3}\\b|\\b0\\d{1,2}-\\d{3,4}-\\d{4}\\b)");
  private ReadingRuleEngine() {}

  static String apply(String input, ReadingRuleRepository repository) {
    return apply(input, repository, ReadingRule.Language.KO_KR);
  }

  static String apply(String input, ReadingRuleRepository repository, ReadingRule.Language language) {
    if (input == null || input.isEmpty() || !repository.enabled()) return input == null ? "" : input;
    List<ReadingRule> rules = new ArrayList<>();
    for (ReadingRule rule : repository.list()) {
      if (!rule.enabled || (rule.language != ReadingRule.Language.ALL && rule.language != language)) continue;
      rules.add(rule);
    }
    rules.sort(Comparator.comparingInt((ReadingRule r) -> r.type == ReadingRule.Type.CONTEXT_PREFIX_NUMBER ? 300 + r.prefix.length() : 400 + r.source.length()).reversed());
    List<int[]> protectedRanges = new ArrayList<>(); Matcher protectedMatcher = PROTECTED.matcher(input);
    while (protectedMatcher.find()) protectedRanges.add(new int[]{protectedMatcher.start(), protectedMatcher.end()});
    List<Replacement> replacements = new ArrayList<>();
    for (ReadingRule rule : rules) {
      if (rule.type == ReadingRule.Type.CONTEXT_PREFIX_NUMBER && protectedContext(rule.prefix)) continue;
      Pattern pattern = patternFor(rule); if (pattern == null) continue;
      Matcher m = pattern.matcher(input);
      while (m.find()) {
        int start = rule.type == ReadingRule.Type.CONTEXT_PREFIX_NUMBER ? m.start(1) : m.start(), end = rule.type == ReadingRule.Type.CONTEXT_PREFIX_NUMBER ? m.end(1) : m.end();
        if (overlapsProtected(start, end, protectedRanges) || overlapsReplacement(start, end, replacements)) continue;
        replacements.add(new Replacement(start, end, replacement(rule, input.substring(start, end), language)));
      }
    }
    replacements.sort((a,b) -> Integer.compare(b.start, a.start));
    String result = input;
    for (Replacement r : replacements) result = result.substring(0, r.start) + r.value + result.substring(r.end);
    return result;
  }
  private static Pattern patternFor(ReadingRule r) {
    int flags = r.caseSensitive ? 0 : Pattern.CASE_INSENSITIVE;
    if (r.type == ReadingRule.Type.CONTEXT_PREFIX_NUMBER) {
      if (r.prefix.trim().isEmpty()) return null;
      String count = r.digitCount == null ? "+" : "{" + r.digitCount + "}";
      return Pattern.compile("(?<![\\p{L}\\d])" + Pattern.quote(r.prefix) + "\\s*(\\d" + count + ")(?!\\d)", flags);
    }
    if (r.source.trim().isEmpty()) return null;
    String boundary = r.source.matches("[A-Za-z0-9]+") ? "(?<![A-Za-z0-9])" + Pattern.quote(r.source) + "(?![A-Za-z0-9])" : Pattern.quote(r.source);
    return Pattern.compile(boundary, flags);
  }
  private static String replacement(ReadingRule r, String matched, ReadingRule.Language language) {
    if (r.type == ReadingRule.Type.EXACT_TEXT_CUSTOM) return r.reading;
    StringBuilder out = new StringBuilder();
    for (int i = 0; i < matched.length(); i++) { char c = matched.charAt(i); if (out.length() > 0) out.append(' '); out.append(c >= '0' && c <= '9' ? (language == ReadingRule.Language.EN_US ? englishDigit(c) : DIGITS.charAt(c - '0')) : (language == ReadingRule.Language.EN_US ? String.valueOf(Character.toUpperCase(c)) : letter(c))); }
    return out.toString();
  }
  private static String englishDigit(char c) { switch (c) { case '0': return "zero"; case '1': return "one"; case '2': return "two"; case '3': return "three"; case '4': return "four"; case '5': return "five"; case '6': return "six"; case '7': return "seven"; case '8': return "eight"; default: return "nine"; } }
  private static String letter(char c) { switch (Character.toUpperCase(c)) {
    case 'A': return "에이"; case 'B': return "비"; case 'C': return "씨"; case 'D': return "디"; case 'E': return "이"; case 'F': return "에프"; case 'G': return "지"; case 'H': return "에이치"; case 'I': return "아이"; case 'J': return "제이"; case 'K': return "케이"; case 'L': return "엘"; case 'M': return "엠"; case 'N': return "엔"; case 'O': return "오"; case 'P': return "피"; case 'Q': return "큐"; case 'R': return "알"; case 'S': return "에스"; case 'T': return "티"; case 'U': return "유"; case 'V': return "브이"; case 'W': return "더블유"; case 'X': return "엑스"; case 'Y': return "와이"; case 'Z': return "지"; default: return String.valueOf(c); }
  }
  private static boolean protectedContext(String value) { return Pattern.compile("(?i)(pin|otp|password|passcode|verification\\s+code|security\\s+code|비밀번호|인증번호|인증\\s*코드)").matcher(value).find(); }
  private static boolean overlapsProtected(int start, int end, List<int[]> ranges) { for (int[] r : ranges) if (r[0] < end && start < r[1]) return true; return false; }
  private static boolean overlapsReplacement(int start, int end, List<Replacement> ranges) { for (Replacement r : ranges) if (r.start < end && start < r.end) return true; return false; }
  private static final class Replacement { final int start, end; final String value; Replacement(int s,int e,String v){start=s;end=e;value=v;} }
}
