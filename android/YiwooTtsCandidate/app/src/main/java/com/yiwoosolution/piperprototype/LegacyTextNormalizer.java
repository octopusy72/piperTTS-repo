package com.yiwoosolution.piperprototype;

import android.content.Context;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Model-independent text side of the legacy Korean pipeline, adapted to feed
 * the Candidate V2 RAW-59 frontend. This is the single normalization entrypoint.
 */
final class LegacyTextNormalizer {
  private static final String DIGITS = "영일이삼사오육칠팔구";
  private static final String DIGIT_SPOKEN = "공일이삼사오육칠팔구";
  private static final String[] SMALL = {"", "십", "백", "천"};
  private static final String[] GROUP = {"", "만", "억", "조", "경"};
  private static final Pattern URL = Pattern.compile("(?i)(?:https?://|www\\.)[^\\s]+|(?<![.@A-Za-z0-9])(?:[a-z0-9-]+\\.)+[a-z]{2,}(?:/[^\\s]*)?");
  private static final Pattern EMAIL = Pattern.compile("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}");
  private static final Pattern IP = Pattern.compile("(?<![\\d.])\\d{1,3}(?:\\.\\d{1,3}){3}(?![\\d.])");
  private static final Pattern PHONE = Pattern.compile("(?<![\\d-])(?:0\\d{1,2}-\\d{3,4}-\\d{4}|1\\d{3}-\\d{4})(?!-\\d)");
  private static final Pattern DATE = Pattern.compile("\\d{4}\\s*년\\s*\\d{1,2}\\s*월\\s*\\d{1,2}\\s*일|\\d{1,2}\\s*월\\s*\\d{1,2}\\s*일|\\d{4}[-.]\\d{1,2}[-.]\\d{1,2}");
  private static final Pattern DATE_SLASH = Pattern.compile("\\b(?:\\d{4}/\\d{1,2}/\\d{1,2}|0\\d/\\d{1,2})\\b");
  private static final Pattern DATE_CONTEXT_SLASH = Pattern.compile("\\b(\\d{1,2})/(\\d{1,2})(?=\\s*(?:에|일|일에))");
  // Clock minutes are two digits; this keeps numeric ratios such as 1:2 on the ratio path.
  private static final Pattern TIME = Pattern.compile("\\d{1,2}:\\d{2}");
  private static final Pattern MEASUREMENT = Pattern.compile("([+\\-]?\\d[\\d,]*(?:\\.\\d+)?)\\s*(km/h|GHz|GB|MB|kcal|Kcal|kg|mg|km|cm|mm|mL|ml|cc|Hz|°C|℃|도씨|g|m|L|l|V|A|W)(?![A-Za-z])");
  private static final Pattern CURRENCY = Pattern.compile("(?:₩|\\$)\\s*\\d[\\d,]*(?:\\.\\d+)?|\\d[\\d,]*(?:\\.\\d+)?\\s*원");
  private static final Pattern PERCENT = Pattern.compile("\\d[\\d,]*(?:\\.\\d+)?%");
  private static final Pattern DECIMAL = Pattern.compile("(?<![A-Za-z0-9])\\d[\\d,]*\\.\\d+(?![A-Za-z0-9])");
  private static final Pattern FRACTION = Pattern.compile("(?<![A-Za-z0-9])(\\d[\\d,]*)\\s*/\\s*(\\d[\\d,]*)(?![A-Za-z0-9])");
  private static final Pattern RATIO = Pattern.compile("(?<![A-Za-z0-9])(\\d[\\d,]*)\\s*:\\s*(\\d[\\d,]*)(?![A-Za-z0-9])");
  private static final Pattern SIGNED = Pattern.compile("(?<![A-Za-z0-9])([+-])\\s*(\\d[\\d,]*)(?![A-Za-z0-9])");
  // A two-part decimal (for example 2.23, including the diagnostic RTF) is
  // not a version. Treat only an explicit v-prefix or three-part versions as
  // version contexts and leave ordinary decimals to DECIMAL below.
  private static final Pattern VERSION = Pattern.compile("(?i)(?<![A-Za-z0-9])(?:v\\d+(?:\\.\\d+)+|version\\s*\\d+(?:\\.\\d+)+|버전\\s*\\d+(?:\\.\\d+)+|\\d+\\.\\d+\\.\\d+)(?![A-Za-z0-9])");
  private static final Pattern V_PREFIX_VERSION = Pattern.compile("(?i)(?<![A-Za-z0-9])v\\d+(?:\\.\\d+){1,3}(?![A-Za-z0-9])");
  private static final Pattern FULL_SLASH_DATE_EXTENSION = Pattern.compile("(?<!\\d)(\\d{4})/(\\d{1,2})/(\\d{1,2})(?!\\d)");
  private static final Pattern FULL_HYPHEN_DATE_EXTENSION = Pattern.compile("(?<!\\d)(\\d{4})-(\\d{1,2})-(\\d{1,2})(?!\\d)");
  private static final Pattern TILDE_RANGE_EXTENSION = Pattern.compile("(?<![A-Za-z0-9:])([+\\-]?\\d[\\d,]*(?:\\.\\d+)?)\\s*[~∼]\\s*([+\\-]?\\d[\\d,]*(?:\\.\\d+)?)\\s*(km/h|GHz|GB|MB|kcal|Kcal|kg|mg|km|cm|mm|mL|ml|cc|Hz|℃|°C|g|m|L|l|V|A|W|%|도씨)?(?![A-Za-z0-9])");
  private static final Pattern SHORT_HYPHEN_RANGE_EXTENSION = Pattern.compile("(?<![A-Za-z0-9:])([+\\-]?\\d{1,3})\\s*-\\s*([+\\-]?\\d{1,3})\\s*(km/h|GHz|GB|MB|kcal|Kcal|kg|mg|km|cm|mm|mL|ml|cc|Hz|℃|°C|g|m|L|l|V|A|W|%|도씨)?(?![A-Za-z0-9])");
  private static final Pattern SUPERSCRIPT = Pattern.compile("(?<![A-Za-z0-9])([A-Za-z가-힣0-9]+)([⁰¹²³⁴⁵⁶⁷⁸⁹]+)(?![A-Za-z0-9])");
  private static final Pattern TIME_RANGE_EXTENSION = Pattern.compile("(?<!\\d)(\\d{1,2}:\\d{2})\\s*[~∼–—-]\\s*(\\d{1,2}:\\d{2})(?!\\d)");
  private static final Pattern CLOCK_EXTENSION = Pattern.compile("(?<![\\d:])\\d{1,2}:\\d{2}(?![\\d:])");
  private static final Pattern RANGE = Pattern.compile("(?<![A-Za-z0-9])([0-9][0-9,]*)([가-힣%]*)\\s*[~∼–—-]\\s*([0-9][0-9,]*)([가-힣%]*)(?![A-Za-z0-9])");
  private static final Pattern IDENTIFIER = Pattern.compile("(?i)(?<![가-힣A-Za-z0-9])(?:[A-Za-z]+[-_/]?)+\\d+[A-Za-z0-9]*(?![가-힣A-Za-z0-9])|(?<![가-힣A-Za-z0-9])\\d+[A-Za-z]+[-_/]?[A-Za-z0-9]*(?![가-힣A-Za-z0-9])");
  private static final Pattern NUMBER_UNIT = Pattern.compile("(?<![A-Za-z])([0-9][0-9,]*)\\s*(만|억|조|경|년|월|일|개월|시|분|개|명|살|병|번|호)(?![A-Za-z])");
  private static final Pattern NUMBER = Pattern.compile("\\d[\\d,]*");

  private LegacyTextNormalizer() {}

  static String normalize(Context context, String input) {
    if (input == null) return "";
    String value = input.trim();
    if (context != null) value = ReadingRuleEngine.apply(value, new ReadingRuleRepository(context));
    value = value.replace("GTX-A", "지티엑스 에이").replace("GTX-B", "지티엑스 비").replace("GTX-C", "지티엑스 씨");
    value = value.replace("Wi-Fi", "와이파이").replace("WiFi", "와이파이").replace("USB-C", "유에스비 씨");
    value = replace(value, EMAIL, LegacyTextNormalizer::email);
    value = replace(value, URL, LegacyTextNormalizer::url);
    value = replace(value, IP, LegacyTextNormalizer::ip);
    value = replace(value, PHONE, LegacyTextNormalizer::phone);
    value = replace(value, DATE, LegacyTextNormalizer::date);
    value = replace(value, DATE_SLASH, LegacyTextNormalizer::date);
    value = replace(value, DATE_CONTEXT_SLASH, LegacyTextNormalizer::date);
    value = replace(value, TIME, LegacyTextNormalizer::time);
    value = replace(value, CURRENCY, LegacyTextNormalizer::currency);
    value = replace(value, PERCENT, LegacyTextNormalizer::percent);
    value = replace(value, MEASUREMENT, LegacyTextNormalizer::measurement);
    value = replace(value, RANGE, m -> number(m.group(1)) + (m.group(2).isEmpty() ? "" : " "+m.group(2)) + " 에서 " + number(m.group(3)) + (m.group(4).isEmpty() ? "" : " "+m.group(4)));
    // Version context must win over the identifier prefix match (for example v2.1.3).
    value = replace(value, VERSION, LegacyTextNormalizer::version);
    value = replace(value, IDENTIFIER, LegacyTextNormalizer::identifier);
    // Korean fractions are read denominator first: A/B -> B분의 A.
    value = replace(value, FRACTION, m -> number(m.group(2)) + "분의 " + number(m.group(1)));
    value = replace(value, RATIO, m -> number(m.group(1)) + " 대 " + number(m.group(2)));
    value = replace(value, SIGNED, m -> ("-".equals(m.group(1)) ? "마이너스 " : "플러스 ") + number(m.group(2)));
    value = replace(value, DECIMAL, LegacyTextNormalizer::decimal);
    value = replace(value, NUMBER_UNIT, m -> number(m.group(1)) + m.group(2));
    value = replace(value, NUMBER, m -> number(m.group()));
    value = replaceEnglish(value);
    value = value.replaceAll("[\\\"'‘’“”]", " ").replaceAll("[()\\[\\]{}]", " ");
    value = value.replace("∼", "에서 ").replace("~", "에서 ");
    value = replaceSymbolsAndEmoji(value);
    value = value.replaceAll("[^가-힣ㄱ-ㅎㅏ-ㅣ !$,.?^_\\n]", " ");
    return value.replace('\n', ' ').replaceAll("[ \\t]+", " ").trim();
  }

  private interface Replacement { String apply(Matcher matcher); }
  private static String replace(String input, Pattern pattern, Replacement replacement) {
    Matcher matcher = pattern.matcher(input); StringBuffer out = new StringBuffer();
    while (matcher.find()) matcher.appendReplacement(out, Matcher.quoteReplacement(replacement.apply(matcher)));
    matcher.appendTail(out); return out.toString();
  }

  static String number(String raw) {
    String clean = raw.replace(",", "").trim();
    try { long value = Long.parseLong(clean); return number(value); } catch (Exception e) { return raw; }
  }
  private static String number(long value) {
    if (value == 0) return "영";
    if (value < 0) return "마이너스 " + number(-value);
    StringBuilder result = new StringBuilder(); int group = 0;
    while (value > 0 && group < GROUP.length) {
      int chunk = (int) (value % 10000);
      if (chunk != 0) {
        String part = small(chunk);
        if (chunk == 1 && group == 1) part = "";
        if (chunk == 1 && group >= 2) part = "일";
        if (result.length() > 0 && part.length() > 0) result.insert(0, " ");
        result.insert(0, part + GROUP[group]);
      }
      value /= 10000; group++;
    }
    return result.toString();
  }
  private static String small(int value) {
    StringBuilder out = new StringBuilder(); int divisor = 1000;
    for (int pos = 3; pos >= 0; pos--) {
      int digit = value / divisor % 10;
      if (digit != 0) { if (digit != 1 || pos == 0) out.append(DIGITS.charAt(digit)); out.append(SMALL[pos]); }
      divisor /= 10;
    }
    return out.toString();
  }

  private static String date(Matcher m) {
    String source = m.group().replace('.', '-');
    if (source.matches("\\d{4}/\\d{1,2}/\\d{1,2}")) {
      String[] parts = source.split("/");
      return number(parts[0]) + "년 " + number(parts[1]) + "월 " + number(parts[2]) + "일";
    }
    Matcher n = Pattern.compile("\\d+").matcher(source); java.util.ArrayList<String> values = new java.util.ArrayList<>(); while (n.find()) values.add(n.group());
    if (values.size() == 3) return number(values.get(0)) + "년 " + number(values.get(1)) + "월 " + number(values.get(2)) + "일";
    if (values.size() == 2 && source.contains("/")) return number(values.get(0)) + "월 " + number(values.get(1)) + "일";
    n = Pattern.compile("\\d+").matcher(source); StringBuilder out = new StringBuilder(); int i = 0;
    while (n.find()) { if (i++ > 0) out.append(' '); out.append(number(n.group())); if (i == 1 && source.contains("년")) out.append("년"); else if (i == 2 || (i == 1 && !source.contains("년"))) out.append("월"); else out.append("일"); }
    return out.toString();
  }
  private static String time(Matcher m) { return time(m.group()); }
  private static String time(String source) { String[] p = source.split(":"); int hour = Integer.parseInt(p[0]); int minute = Integer.parseInt(p[1]); String hourText = hour >= 1 && hour <= 12 ? nativeNumber(hour) : number(hour); return (hourText + " 시 " + (minute == 0 ? "" : number(minute) + " 분")).trim(); }
  private static String nativeNumber(int value) {
    switch (value) {
      case 1: return "한"; case 2: return "두"; case 3: return "세"; case 4: return "네";
      case 5: return "다섯"; case 6: return "여섯"; case 7: return "일곱"; case 8: return "여덟";
      case 9: return "아홉"; case 10: return "열"; case 11: return "열한"; case 12: return "열두";
      default: return number(value);
    }
  }
  private static String currency(Matcher m) { String s = m.group().trim(); boolean dollar = s.startsWith("$"); Matcher n = Pattern.compile("\\d[\\d,]*(?:\\.\\d+)?").matcher(s); if (!n.find()) return s; return decimal(n) + (dollar ? " 달러" : " 원"); }
  private static String percent(Matcher m) { Matcher n = Pattern.compile("\\d[\\d,]*(?:\\.\\d+)?").matcher(m.group()); return n.find() ? decimal(n) + " 퍼센트" : m.group(); }
  private static String measurement(Matcher m) { String value = decimal(m.group(1)); String unit = m.group(2); if (unit.equals("km/h")) return "시속 " + value + " 킬로미터"; if (unit.equals("℃") || unit.equals("°C") || unit.equals("도씨")) return "섭씨 " + value + " 도"; return value + " " + unitName(unit); }
  private static String unitName(String unit) { switch (unit) { case "GHz": return "기가헤르츠"; case "GB": return "기가바이트"; case "MB": return "메가바이트"; case "kg": return "킬로그램"; case "mg": return "밀리그램"; case "km": return "킬로미터"; case "cm": return "센티미터"; case "mm": return "밀리미터"; case "mL": case "ml": return "밀리리터"; case "kcal": case "Kcal": return "킬로칼로리"; case "cc": return "씨씨"; case "g": return "그램"; case "m": return "미터"; case "L": case "l": return "리터"; case "V": return "볼트"; case "A": return "암페어"; case "W": return "와트"; case "Hz": return "헤르츠"; default: return unit; } }
  private static String decimal(Matcher m) { String[] p = m.group().replace(",", "").split("\\."); return number(p[0]) + (p.length > 1 ? " 점 " + digits(p[1], false) : ""); }
  private static String decimal(String s) { Matcher m = Pattern.compile("\\d[\\d,]*(?:\\.\\d+)?").matcher(s); return m.find() ? decimal(m) : s; }
  private static String version(Matcher m) {
    String raw = m.group();
    if (Character.isDigit(raw.charAt(0))) raw = "버전 " + raw;
    return com.yiwoosolution.koreantts.speech.SemanticNormalizers.INSTANCE.readVersion(raw);
  }
  private static String digits(String s, boolean gong) { StringBuilder out = new StringBuilder(); for (int i=0;i<s.length();i++) { if (i>0) out.append(' '); char c=s.charAt(i); out.append(c=='0'&&gong?'공':DIGITS.charAt(c-'0')); } return out.toString(); }
  private static String phone(Matcher m) { String[] groups = m.group().split("-"); StringBuilder out = new StringBuilder(); for (String group : groups) { if(out.length()>0) out.append(", "); out.append(digits(group,true).replace(" ", "")); } return out.toString(); }
  private static String ip(Matcher m) { String[] groups=m.group().split("\\."); StringBuilder out=new StringBuilder(); for(String group:groups){if(out.length()>0)out.append(" 점 ");out.append(digits(group,false));} return out.toString(); }
  private static String email(Matcher m) { String[] p=m.group().split("@",2); return lettersAndDigits(p[0])+" 골뱅이 "+domain(p[1]); }
  private static String url(Matcher m) { String s=m.group().replaceFirst("(?i)^https?://",""); String[] q=s.split("\\?",2); String[] path=q[0].split("/",2); StringBuilder out=new StringBuilder(domain(path[0])); if(path.length>1)out.append(" 슬래시 ").append(lettersAndDigits(path[1])); if(q.length>1)out.append(" 물음표 ").append(lettersAndDigits(q[1])); return out.toString(); }
  private static String domain(String s) { StringBuilder out=new StringBuilder(); for(String p:s.split("\\.")){if(p.isEmpty())continue;if(out.length()>0)out.append(" 점 ");out.append(lettersAndDigits(p));} return out.toString(); }
  private static String lettersAndDigits(String s) { StringBuilder out=new StringBuilder(); Matcher m=Pattern.compile("[A-Za-z]+|\\d+|.").matcher(s); while(m.find()){String t=m.group();if(out.length()>0)out.append(' ');if(t.matches("\\d+"))out.append(digits(t,true));else if(t.matches("[A-Za-z]+"))out.append(t.length()==1?letter(t.charAt(0)):english(t));else if(t.equals("."))out.append("점");else if(t.equals("_"))out.append("밑줄");}return out.toString(); }
  private static String identifier(Matcher m) { return lettersAndDigits(m.group().replace('-', ' ')); }
  private static String english(String token) { String lower=token.toLowerCase(Locale.US); switch(lower){case "example":return "이그잼플";case "test":return "테스트";case "user":return "유저";case "support":return "서포트";case "com":return "컴";case "android":return "안드로이드";case "google":return "구글";case "samsung":return "삼성";case "callback":return "콜백";case "spacex":return "스페이스 엑스";case "hello":return "헬로";case "world":return "월드";case "summer":return "서머";case "vacation":return "베케이션";case "computer":return "컴퓨터";case "coffee":return "커피";case "music":return "뮤직";case "network":return "네트워크";case "service":return "서비스";case "tts":return "티티에스";case "api":return "에이피아이";case "apk":return "에이피케이";case "cpu":return "씨피유";case "gpu":return "지피유";case "usb":return "유에스비";case "sms":return "에스엠에스";case "gps":return "지피에스";case "url":return "유알엘";case "pdf":return "피디에프";case "html":return "에이치티엠엘";case "http":return "에이치티티피";case "https":return "에이치티티피에스";case "ram":return "램";case "rom":return "롬";case "ssd":return "에스에스디";case "hdd":return "에이치디디";case "nfc":return "엔에프씨";case "lte":return "엘티이";case "vpn":return "브이피엔";case "ip":return "아이피";case "dns":return "디엔에스";case "sdk":return "에스디케이";case "ui":return "유아이";case "ux":return "유엑스";case "kbs":return "케이비에스";case "ap":return "에이피";case "rtf":return "알티에프";case "ai":return "에이아이";} return lexicalEnglishFallback(lower); }
  /** Converts unknown lowercase lexical words to Korean-readable syllable fragments instead of letter names. */
  private static String lexicalEnglishFallback(String word) {
    String s=word.toLowerCase(Locale.US);
    String[][] groups={{"tion","션"},{"sion","션"},{"ture","처"},{"ough","오"},{"augh","오"},{"eigh","에이"},{"ight","아이"},{"sh","시"},{"ch","치"},{"th","스"},{"ph","프"},{"wh","w"},{"qu","쿠"},{"ck","ㅋ"},{"ee","이"},{"ea","이"},{"oo","우"},{"ou","아우"},{"ow","아우"},{"ai","에이"},{"ay","에이"},{"er","어"},{"or","오어"},{"ar","아"},{"ir","어"},{"ur","어"}};
    for(String[] g:groups)s=s.replace(g[0],g[1]);
    StringBuilder out=new StringBuilder();
    for(int i=0;i<s.length();i++){char c=s.charAt(i); if(c>='가'&&c<='힣'){out.append(c);continue;} switch(c){case'a':out.append("애");break;case'e':out.append("에");break;case'i':out.append("이");break;case'o':out.append("오");break;case'u':out.append("어");break;case'y':out.append("이");break;case'b':out.append("브");break;case'c':out.append("ㅋ");break;case'd':out.append("드");break;case'f':out.append("프");break;case'g':out.append("그");break;case'h':out.append("ㅎ");break;case'j':out.append("즈");break;case'k':out.append("크");break;case'l':out.append("을");break;case'm':out.append("므");break;case'n':out.append("느");break;case'p':out.append("프");break;case'q':out.append("ㅋ");break;case'r':out.append("르");break;case's':out.append("스");break;case't':out.append("트");break;case'v':out.append("브");break;case'w':out.append("우");break;case'x':out.append("엑스");break;case'z':out.append("즈");break;default:out.append(c);}}
    return out.toString();
  }
  private static String replaceEnglish(String s) { return replace(s, Pattern.compile("[A-Za-z]+"), m -> english(m.group())); }
  static String applyPreLegacyExtensions(String value) {
    String out = replace(value == null ? "" : value, FULL_SLASH_DATE_EXTENSION,
        m -> number(m.group(1)) + "년 " + number(m.group(2)) + "월 " + number(m.group(3)) + "일");
    out = replace(out, FULL_HYPHEN_DATE_EXTENSION,
        m -> number(m.group(1)) + "년 " + number(m.group(2)) + "월 " + number(m.group(3)) + "일");
    out = replace(out, SUPERSCRIPT, m -> m.group(1) + "^" + superscriptDigits(m.group(2)));
    out = replace(out, TILDE_RANGE_EXTENSION, LegacyTextNormalizer::range);
    out = replace(out, SHORT_HYPHEN_RANGE_EXTENSION, LegacyTextNormalizer::range);
    out = replace(out, V_PREFIX_VERSION, LegacyTextNormalizer::version);
    out = replace(out, TIME_RANGE_EXTENSION, m -> time(m.group(1)) + "에서 " + time(m.group(2)));
    return replace(out, CLOCK_EXTENSION, m -> time(m.group()));
  }
  private static String range(Matcher m) {
    String unit = m.group(3);
    String left = rangeNumber(m.group(1));
    String right = rangeNumber(m.group(2));
    return left + "에서 " + right + (unit == null ? "" : " " + rangeUnit(unit));
  }
  private static String rangeNumber(String value) {
    try { return com.yiwoosolution.koreantts.speech.NumberReader.INSTANCE.decimal(value); }
    catch (RuntimeException ignored) { return number(value); }
  }
  private static String rangeUnit(String unit) {
    switch (unit) {
      case "km/h": return "킬로미터 매 시"; case "GHz": return "기가헤르츠"; case "GB": return "기가바이트";
      case "MB": return "메가바이트"; case "kcal": case "Kcal": return "킬로칼로리"; case "kg": return "킬로그램";
      case "mg": return "밀리그램"; case "km": return "킬로미터"; case "cm": return "센티미터";
      case "mm": return "밀리미터"; case "mL": case "ml": return "밀리리터"; case "cc": return "씨씨";
      case "Hz": return "헤르츠"; case "℃": case "°C": case "도씨": return "도"; case "g": return "그램";
      case "m": return "미터"; case "L": case "l": return "리터"; case "V": return "볼트";
      case "A": return "암페어"; case "W": return "와트"; case "%": return "퍼센트"; default: return unit;
    }
  }
  private static String superscriptDigits(String value) {
    StringBuilder out = new StringBuilder();
    for (int i = 0; i < value.length(); i++) {
      switch (value.charAt(i)) {
        case '⁰': out.append('0'); break; case '¹': out.append('1'); break; case '²': out.append('2'); break;
        case '³': out.append('3'); break; case '⁴': out.append('4'); break; case '⁵': out.append('5'); break;
        case '⁶': out.append('6'); break; case '⁷': out.append('7'); break; case '⁸': out.append('8'); break;
        case '⁹': out.append('9'); break; default: out.append(value.charAt(i));
      }
    }
    return out.toString();
  }
  static String adaptLegacyOutputForRaw59(String value) {
    value = replaceEnglish(value == null ? "" : value);
    value = value.replaceAll("[\\\"'‘’“”]", " ").replaceAll("[()\\[\\]{}]", " ");
    value = replaceSymbolsAndEmoji(value);
    value = value.replaceAll("[^가-힣ㄱ-ㅎㅏ-ㅣ !$,.?^_\\n]", " ");
    return value.replace('\n', ' ').replaceAll("[ \\t]+", " ").trim();
  }
  private static String letter(char c) { switch(Character.toUpperCase(c)){case'A':return"에이";case'B':return"비";case'C':return"씨";case'D':return"디";case'E':return"이";case'F':return"에프";case'G':return"지";case'H':return"에이치";case'I':return"아이";case'J':return"제이";case'K':return"케이";case'L':return"엘";case'M':return"엠";case'N':return"엔";case'O':return"오";case'P':return"피";case'Q':return"큐";case'R':return"알";case'S':return"에스";case'T':return"티";case'U':return"유";case'V':return"브이";case'W':return"더블유";case'X':return"엑스";case'Y':return"와이";case'Z':return"지";default:return String.valueOf(c);}}
  private static String replaceSymbolsAndEmoji(String value) {
    String out = value.replace("😀", "웃는 얼굴").replace("😂", "기쁨의 눈물").replace("❤️", "하트")
        .replace("❤", "하트").replace("👍", "좋아요").replace("✅", "확인").replace("⚠", "경고")
        .replace("→", "화살표").replace("←", "왼쪽 화살표").replace("©", "저작권").replace("™", "상표")
        .replace("#", " 샵 ").replace("@", " 골뱅이 ").replace("&", " 앤드 ").replace("+", " 플러스 ")
        .replace("=", " 이퀄 ").replace("/", " 슬래시 ").replace("\\\\", " 역슬래시 ").replace("|", " 세로줄 ")
        .replace("*", " 별표 ").replace("^", " 캐럿 ").replace("~", " 물결표 ");
    return out.replaceAll("[^\\p{L}\\p{N}\\s.,!?()\\[\\]{}_'\"%:;\\-]", " ");
  }
}
