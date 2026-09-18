package com.yiwoosolution.piperprototype;

import android.content.Context;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Exact small runtime port of Legacy KoreanG2pSupport.convertEnglish(). */
final class LegacyCmuEnglishConverter {
  private static final Pattern WORDS = Pattern.compile("[A-Za-z']+");
  private static final Pattern COMMENT = Pattern.compile("##");
  private static final Pattern FIELDS = Pattern.compile("\\s+");
  private static final Pattern VARIANT = Pattern.compile("\\(\\d+\\)$");
  private static final Pattern DIGITS = Pattern.compile("\\d+");
  private static volatile Map<String, List<String>> dictionary;

  private LegacyCmuEnglishConverter() {}

  static String convert(Context context, String text) {
    if (context == null || text == null || text.isEmpty()) return text;
    java.util.LinkedHashSet<String> words = new java.util.LinkedHashSet<>();
    Matcher matcher = WORDS.matcher(text);
    while (matcher.find()) words.add(matcher.group());
    if (words.isEmpty()) return text;
    Map<String, List<String>> cmu = load(context);
    String out = text;
    for (String source : words) {
      List<String> raw = cmu.get(source.toLowerCase(Locale.US));
      if (raw == null) continue;
      String replacement = convertWord(raw);
      out = out.replace(source, replacement);
    }
    return out;
  }

  private static Map<String, List<String>> load(Context context) {
    Map<String, List<String>> current = dictionary;
    if (current != null) return current;
    synchronized (LegacyCmuEnglishConverter.class) {
      if (dictionary != null) return dictionary;
      Map<String, List<String>> result = new LinkedHashMap<>();
      try (BufferedReader reader = new BufferedReader(new InputStreamReader(
          context.getAssets().open("g2pkk/cmudict.dict"), StandardCharsets.UTF_8))) {
        String line;
        while ((line = reader.readLine()) != null) {
          String trimmed = COMMENT.split(line, 2)[0].trim();
          if (trimmed.isEmpty()) continue;
          String[] fields = FIELDS.split(trimmed);
          if (fields.length < 2) continue;
          String word = VARIANT.matcher(fields[0].toLowerCase(Locale.US)).replaceFirst("");
          int start = fields.length > 1 && DIGITS.matcher(fields[1]).matches() ? 2 : 1;
          if (start >= fields.length) continue;
          List<String> phones = new ArrayList<>();
          for (int i = start; i < fields.length; i++) if (!fields[i].equals("-")) phones.add(fields[i]);
          result.putIfAbsent(word, phones);
        }
      } catch (Exception error) {
        throw new IllegalStateException("CMUDICT_LOAD_FAILED", error);
      }
      dictionary = result;
      return result;
    }
  }

  private static List<String> adjust(List<String> phones) {
    StringBuilder text = new StringBuilder(" ");
    for (String phone : phones) if (!phone.equals("-")) text.append(phone).append(' ');
    text.append('$');
    String value = text.toString().replaceAll("\\d", "")
        .replace(" T S ", " TS ").replace(" D Z ", " DZ ")
        .replace(" AW ER ", " AWER ").replace(" IH R $", " IH ER ")
        .replace(" EH R $", " EH ER ").replace(" $", "").trim();
    String[] parts = value.split("\\s+");
    List<String> result = new ArrayList<>();
    for (String part : parts) if (!part.isEmpty()) result.add(part);
    return result;
  }

  private static String convertWord(List<String> raw) {
    List<String> phones = adjust(raw);
    StringBuilder out = new StringBuilder();
    for (int i = 0; i < phones.size(); i++) {
      String p = phones.get(i);
      String prev = i > 0 ? phones.get(i - 1) : "^";
      String next = i + 1 < phones.size() ? phones.get(i + 1) : "$";
      String next2 = i + 2 < phones.size() ? phones.get(i + 1) : "$";
      if (p.equals("P") || p.equals("T") || p.equals("K")) {
        if (starts(prev, "AE", "AH", "AX", "EH", "IH", "IX", "UH") && next.equals("$")) out.append(jong(p));
        else if (starts(prev, "AE", "AH", "AX", "EH", "IH", "IX", "UH") && !consonantOrDollar(first(next))) out.append(jong(p));
        else if (consonantOrDollar(first(next)) || next.startsWith("Y")) out.append(choseong(p)).append('ᅳ');
        else out.append(choseong(p));
      } else if (p.equals("B") || p.equals("D") || p.equals("G")) {
        out.append(choseong(p)); if (consonantOrDollar(first(next))) out.append('ᅳ');
      } else if (p.equals("S") || p.equals("Z") || p.equals("F") || p.equals("V") || p.equals("TH") || p.equals("DH") || p.equals("SH") || p.equals("ZH")) {
        out.append(choseong(p));
        if (p.equals("S") || p.equals("Z") || p.equals("F") || p.equals("V") || p.equals("TH") || p.equals("DH")) {
          if (consonantOrDollar(first(next))) out.append('ᅳ');
        } else if (p.equals("SH")) {
          if (next.equals("$")) out.append('ᅵ'); else if (consonant(first(next))) out.append("ᅲ"); else out.append('Y');
        } else if (p.equals("ZH") && consonantOrDollar(first(next))) out.append('ᅵ');
      } else if (p.equals("TS") || p.equals("DZ") || p.equals("CH") || p.equals("JH")) {
        out.append(choseong(p)); if (consonantOrDollar(first(next))) out.append(p.equals("TS") || p.equals("DZ") ? 'ᅳ' : 'ᅵ');
      } else if (p.equals("M") || p.equals("N") || p.equals("NG")) {
        if ((p.equals("M") || p.equals("N")) && vowel(first(next))) out.append(choseong(p)); else out.append(jong(p));
      } else if (p.equals("L")) {
        if (prev.equals("^")) out.append(choseong(p));
        else if (consonantOrDollar(first(next))) out.append(jong(p));
        else if (prev.equals("M") || prev.equals("N")) out.append(choseong(p));
        else if (vowel(first(next))) out.append("ᆯᄅ");
        else if ((next.equals("M") || next.equals("N")) && !vowel(first(next2))) out.append("ᆯ르");
      } else if (p.equals("ER")) {
        if (vowel(first(prev))) out.append('ᄋ'); out.append('ᅥ'); if (vowel(first(next))) out.append('ᄅ');
      } else if (p.equals("R")) {
        if (vowel(first(next))) out.append(choseong(p));
      } else if (vowel(first(p))) out.append(jung(p));
      else out.append(choseong(p));
    }
    return compose(reconstruct(out.toString()));
  }

  private static boolean starts(String value, String... prefixes) { for (String p : prefixes) if (value.startsWith(p)) return true; return false; }
  private static char first(String value) { return value.isEmpty() ? '$' : value.charAt(0); }
  private static boolean vowel(char c) { return c == 'A' || c == 'E' || c == 'I' || c == 'O' || c == 'U' || c == 'Y'; }
  private static boolean consonant(char c) { return "BCDFGHJKLMNPQRSTVXZ".indexOf(c) >= 0; }
  private static boolean consonantOrDollar(char c) { return c == '$' || consonant(c); }

  private static String choseong(String p) { switch (p) {
    case "B": return "ᄇ"; case "CH": return "ᄎ"; case "D": case "DH": return "ᄃ"; case "DZ": return "ᄌ";
    case "F": return "ᄑ"; case "G": return "ᄀ"; case "HH": return "ᄒ"; case "JH": case "Z": case "ZH": return "ᄌ";
    case "K": return "ᄏ"; case "L": case "R": return "ᄅ"; case "M": return "ᄆ"; case "N": return "ᄂ"; case "NG": return "ᄋ";
    case "P": return "ᄑ"; case "S": case "SH": case "TH": return "ᄉ"; case "T": return "ᄐ"; case "TS": return "ᄎ";
    case "V": return "ᄇ"; case "W": return "W"; case "Y": return "Y"; default: return p;
  }}
  private static String jung(String p) { switch (p) {
    case "AA": return "ᅡ"; case "AE": return "ᅢ"; case "AH": case "ER": return "ᅥ"; case "AO": return "ᅩ";
    case "AW": return "ᅡ우"; case "AWER": return "ᅡ워"; case "AY": return "ᅡ이"; case "EH": return "ᅦ";
    case "EY": return "ᅦ이"; case "IH": case "IY": return "ᅵ"; case "OW": return "ᅩ"; case "OY": return "ᅩ이";
    case "UH": case "UW": return "ᅮ"; default: return p;
  }}
  private static String jong(String p) { switch (p) {
    case "B": case "V": case "P": return "ᆸ"; case "CH": return "ᆾ"; case "D": case "DH": return "ᆮ";
    case "F": return "ᇁ"; case "G": case "K": return "ᆨ"; case "HH": return "ᇂ"; case "JH": case "Z": case "ZH": return "ᆽ";
    case "L": case "R": return "ᆯ"; case "M": return "ᆷ"; case "N": return "ᆫ"; case "NG": case "W": case "Y": return "ᆼ";
    case "S": case "SH": case "T": case "TH": return "ᆺ"; default: return p;
  }}

  private static String reconstruct(String value) {
    String[][] pairs = {{"그W","ᄀW"},{"흐W","ᄒW"},{"크W","ᄏW"},{"ᄂYᅥ","니어"},{"ᄃYᅥ","디어"},{"ᄅYᅥ","리어"},{"Yᅵ","ᅵ"},{"Yᅡ","ᅣ"},{"Yᅢ","ᅤ"},{"Yᅥ","ᅧ"},{"Yᅦ","ᅨ"},{"Yᅩ","ᅭ"},{"Yᅮ","ᅲ"},{"Wᅡ","ᅪ"},{"Wᅢ","ᅫ"},{"Wᅥ","ᅯ"},{"Wᅩ","ᅯ"},{"Wᅮ","ᅮ"},{"Wᅦ","ᅰ"},{"Wᅵ","ᅱ"},{"ᅳᅵ","ᅴ"},{"Y","ᅵ"},{"W","ᅮ"}};
    String out = value; for (String[] pair : pairs) out = out.replace(pair[0], pair[1]); return out;
  }

  private static String compose(String text) {
    final String onsets = "ᄀᄁᄂᄃᄄᄅᄆᄇᄈᄉᄊᄋᄌᄍᄎᄏᄐᄑᄒ";
    final String vowels = "ᅡᅢᅣᅤᅥᅦᅧᅨᅩᅪᅫᅬᅭᅮᅯᅰᅱᅲᅳᅴᅵ";
    final String codas = "\u0000ᆨᆩᆪᆫᆬᆭᆮᆯᆰᆱᆲᆳᆴᆵᆶᆷᆸᆹᆺᆻᆼᆽᆾᆿᇀᇁᇂ";
    StringBuilder out = new StringBuilder();
    for (int i = 0; i < text.length();) {
      char a = text.charAt(i++);
      if (i < text.length() && onsets.indexOf(a) >= 0 && vowels.indexOf(text.charAt(i)) >= 0) {
        char b = text.charAt(i++); char c = '\u0000';
        if (i < text.length() && codas.indexOf(text.charAt(i)) > 0) c = text.charAt(i++);
        int oi = onsets.indexOf(a), vi = vowels.indexOf(b), ti = c == '\u0000' ? 0 : codas.indexOf(c);
        out.append((char)(0xAC00 + oi * 588 + vi * 28 + ti));
      } else if (vowels.indexOf(a) >= 0) {
        int vi = vowels.indexOf(a); out.append((char)(0xAC00 + 11 * 588 + vi * 28));
      } else out.append(a);
    }
    return out.toString();
  }
}
