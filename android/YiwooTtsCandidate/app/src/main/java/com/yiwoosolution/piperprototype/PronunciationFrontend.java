package com.yiwoosolution.piperprototype;

import android.content.Context;
import android.util.Log;
import java.io.InputStream;
import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Process-scoped Korean pronunciation resolver loaded once per application process. */
final class PronunciationFrontend {
  private static final String TAG = "YiwooPronunciation";
  private static final String ASSET = "pronunciation/runtime_lexicon_commercial_safe.bin";
  private static final byte[] MAGIC = "YIWOOLEX2".getBytes(StandardCharsets.US_ASCII);
  private static volatile PronunciationFrontend shared;
  private final LexiconTable lexicon;
  private final List<Map.Entry<String, String>> phrases;
  private final long loadMs;
  private final double assetOpenMs;
  private final double assetReadMs;
  private final double parseDecodeMs;
  private final double mapBuildMs;
  private final Context appContext;
  private volatile CmuDictTable cmu;
  private volatile long lastResolveUs;

  private static final Map<String, String> EXPLICIT = mapOf(
      "youtube", "유튜브", "google", "구글", "netflix", "넷플릭스", "pfizer", "화이자",
      "volvo", "볼보", "bluetooth", "블루투스", "wi-fi", "와이파이", "instagram", "인스타그램",
      "galaxy", "갤럭시", "android", "안드로이드", "windows", "윈도우", "microsoft", "마이크로소프트",
      "openai", "오픈에이아이", "chatgpt", "챗지피티", "spotify", "스포티파이",
      "notification", "알림", "settings", "설정", "example", "이그잼플", "com", "컴");
  private static final Map<String, String> PHRASES = mapOf(
      "notification settings", "알림 설정", "notification permission", "알림 권한");
  private static final Map<String, String> ACRONYMS = mapOf(
      "ai", "에이아이", "usb", "유에스비", "cpu", "씨피유", "gpu", "지피유", "ssd", "에스에스디", "hdmi", "에이치디엠아이",
      "ip", "아이피", "url", "유알엘", "http", "에이치티티피", "https", "에이치티티피에스", "dns", "디엔에스", "sdk", "에스디케이",
      "email", "이메일", "e-mail", "이메일", "ipv4", "아이피 버전 사");
  private static final Pattern LATIN_TOKEN = Pattern.compile("(?<![A-Za-z0-9-])[A-Za-z][A-Za-z0-9+.#'’]*(?:-[A-Za-z0-9+.#'’]+)*(?![A-Za-z0-9-])");

  private PronunciationFrontend(Context context) {
    appContext = context.getApplicationContext();
    long start = System.nanoTime();
    LexiconTable loaded;
    double openMs;
    double readMs;
    double decodeMs = 0.0;
    double buildMs = 0.0;
    try {
      long openStart = System.nanoTime();
      InputStream stream = context.getAssets().open(ASSET);
      openMs = elapsedMs(openStart);
      long readStart = System.nanoTime();
      byte[] bytes = readAll(stream);
      stream.close();
      readMs = elapsedMs(readStart);
      ByteBuffer buffer = ByteBuffer.wrap(bytes).order(ByteOrder.BIG_ENDIAN);
      byte[] magic = new byte[MAGIC.length]; buffer.get(magic);
      if (!java.util.Arrays.equals(MAGIC, magic)) throw new IllegalStateException("PRONUNCIATION_LEXICON_BAD_MAGIC");
      int version = buffer.getInt();
      if (version != 1) throw new IllegalStateException("PRONUNCIATION_LEXICON_UNSUPPORTED_VERSION=" + version);
      int count = buffer.getInt();
      if (count <= 0 || count > 1_000_000) throw new IllegalStateException("PRONUNCIATION_LEXICON_BAD_COUNT=" + count);
      loaded = new LexiconTable(count, true, bytes);
      for (int i = 0; i < count; i++) {
        int keyLength = buffer.getInt(); int valueLength = buffer.getInt();
        if (keyLength <= 0 || valueLength <= 0 || keyLength > buffer.remaining() || valueLength > buffer.remaining() - keyLength) {
          throw new IllegalStateException("PRONUNCIATION_LEXICON_BAD_ENTRY=" + i);
        }
        int keyOffset = buffer.position(); buffer.position(keyOffset + keyLength);
        int valueOffset = buffer.position(); buffer.position(valueOffset + valueLength);
        long decodeStart = System.nanoTime();
        // The build-time converter writes canonical lookup keys; avoid running
        // the regex-heavy normalizer once per entry during app startup.
        String key = new String(bytes, keyOffset, keyLength, StandardCharsets.UTF_8);
        decodeMs += elapsedMs(decodeStart);
        long buildStart = System.nanoTime();
        if (!key.isEmpty() && valueLength > 0) loaded.putAt(i, key, valueOffset, valueLength);
        buildMs += elapsedMs(buildStart);
      }
      if (buffer.hasRemaining() || loaded.size() != count) throw new IllegalStateException("PRONUNCIATION_LEXICON_ENTRY_COUNT_MISMATCH");
    } catch (Exception error) {
      throw new IllegalStateException("PRONUNCIATION_LEXICON_LOAD_FAILED", error);
    }
    lexicon = loaded;
    List<Map.Entry<String, String>> allPhrases = new ArrayList<>();
    allPhrases.addAll(PHRASES.entrySet());
    allPhrases.addAll(EXPLICIT.entrySet());
    phrases = Collections.unmodifiableList(new ArrayList<>(allPhrases));
    loadMs = (System.nanoTime() - start) / 1_000_000L;
    assetOpenMs = openMs; assetReadMs = readMs; parseDecodeMs = decodeMs; mapBuildMs = buildMs;
    Log.i(TAG, "LEXICON_READY entries=" + lexicon.size() + " assetOpenMs=" + assetOpenMs
        + " assetReadMs=" + assetReadMs + " parseDecodeMs=" + parseDecodeMs
        + " mapBuildMs=" + mapBuildMs + " loadMs=" + loadMs);
  }

  PronunciationFrontend(Map<String, String> testLexicon) {
    appContext = null;
    lexicon = new LexiconTable(testLexicon);
    List<Map.Entry<String, String>> allPhrases = new ArrayList<>();
    allPhrases.addAll(PHRASES.entrySet());
    allPhrases.addAll(EXPLICIT.entrySet());
    phrases = Collections.unmodifiableList(new ArrayList<>(allPhrases));
    loadMs = 0L;
    assetOpenMs = 0.0; assetReadMs = 0.0; parseDecodeMs = 0.0; mapBuildMs = 0.0;
  }

  static PronunciationFrontend shared(Context context) {
    PronunciationFrontend current = shared;
    if (current != null) return current;
    synchronized (PronunciationFrontend.class) {
      if (shared == null) shared = new PronunciationFrontend(context.getApplicationContext());
      return shared;
    }
  }

  static void preload(Context context) { shared(context); }
  static long loadMs(Context context) { return shared(context).loadMs; }
  static double assetOpenMs(Context context) { return shared(context).assetOpenMs; }
  static double assetReadMs(Context context) { return shared(context).assetReadMs; }
  static double parseDecodeMs(Context context) { return shared(context).parseDecodeMs; }
  static double mapBuildMs(Context context) { return shared(context).mapBuildMs; }
  static long lastResolveUs(Context context) { return shared(context).lastResolveUs; }

  String normalize(String input) {
    if (input == null || input.isEmpty()) return input == null ? "" : input;
    long start = System.nanoTime();
    // NFKC erases superscript semantics (x² -> x2); consume math first.
    String math = com.yiwoosolution.koreantts.speech.KoreanStructuredText.INSTANCE.normalizeMath(input);
    String out = Normalizer.normalize(math, Normalizer.Form.NFKC)
        .replace('\u2019', '\'').replace('\u2018', '\'')
        .replace('\u2010', '-').replace('\u2011', '-').replace('\u2013', '-');
    out = com.yiwoosolution.koreantts.speech.KoreanStructuredText.INSTANCE.normalize(out);
    for (Map.Entry<String, String> entry : phrases) {
      out = replace(out, entry.getKey(), entry.getValue(), true);
    }
    for (Map.Entry<String, String> entry : ACRONYMS.entrySet()) {
      out = replace(out, entry.getKey(), entry.getValue(), false);
    }
    out = replaceGtx(out);
    Matcher matcher = LATIN_TOKEN.matcher(out);
    StringBuffer result = new StringBuffer();
    while (matcher.find()) {
      String token = matcher.group();
      String value = lexicon.get(normalizeKey(token));
      if (value == null) value = fallbackLatin(token);
      if (value != null) matcher.appendReplacement(result, Matcher.quoteReplacement(value));
    }
    matcher.appendTail(result);
    lastResolveUs = (System.nanoTime() - start) / 1_000L;
    return result.toString();
  }

  private String fallbackLatin(String token) {
    // Do not reinterpret URLs, email addresses, or paths in the Korean frontend.
    if (token.indexOf(".") >= 0 || token.indexOf("/") >= 0 || token.indexOf("@") >= 0) return null;
    if (appContext != null) {
      CmuDictTable table = cmu;
      if (table == null) synchronized (this) { if (cmu == null) cmu = CmuDictTable.load(appContext); table = cmu; }
      String phones = table.get(token);
      if (phones != null) return transliterate(phones);
    }
    return spellLetters(token);
  }

  private static String spellLetters(String token) {
    StringBuilder out = new StringBuilder();
    String[] names = {"에이","비","씨","디","이","에프","지","에이치","아이","제이","케이","엘","엠","엔","오","피","큐","알","에스","티","유","브이","더블유","엑스","와이","지"};
    for (int i=0;i<token.length();i++) { char c=Character.toUpperCase(token.charAt(i)); if(c>='A'&&c<='Z'){if(out.length()>0)out.append(' ');out.append(names[c-'A']);} }
    return out.toString();
  }

  private static String transliterate(String value) {
    String[] ps=value.split("\\s+"); StringBuilder out=new StringBuilder();
    for(int i=0;i<ps.length;i++) {
      String p=ps[i].replaceAll("[0-2]$",""); String onset=initial(p); String nucleus=null; String coda="";
      if (isVowel(p)) { nucleus=vowel(p); onset=""; }
      else if (i+1<ps.length && isVowel(ps[i+1].replaceAll("[0-2]$",""))) { nucleus=vowel(ps[++i].replaceAll("[0-2]$","")); }
      else { if(out.length()>0) out.append(' '); out.append(spellPhone(p)); continue; }
      if(i+1<ps.length) { String next=ps[i+1].replaceAll("[0-2]$",""); if(!isVowel(next)){ if(i+2>=ps.length || !isVowel(ps[i+2].replaceAll("[0-2]$",""))){coda=coda(next);i++;} } }
      if(out.length()>0) out.append(' '); out.append(compose(onset,nucleus,coda));
    }
    return out.toString();
  }
  private static boolean isVowel(String p){return p.matches("AA|AE|AH|AO|AW|AY|EH|ER|EY|IH|IY|OW|OY|UH|UW|AX|IX|UX");}
  private static String vowel(String p){if(p.equals("AE"))return "애";if(p.equals("EH")||p.equals("EY"))return "에";if(p.equals("AO")||p.equals("OW"))return "오";if(p.equals("UH")||p.equals("UW"))return "우";if(p.equals("IH")||p.equals("IY")||p.equals("EY"))return "이";if(p.equals("AH")||p.equals("ER")||p.equals("AX")||p.equals("IX"))return "어";return "아";}
  private static String initial(String p){if(p.equals("B"))return "ㅂ";if(p.equals("P"))return "ㅍ";if(p.equals("D"))return "ㄷ";if(p.equals("T"))return "ㅌ";if(p.equals("G"))return "ㄱ";if(p.equals("K"))return "ㅋ";if(p.equals("F")||p.equals("V"))return "ㅂ";if(p.equals("S")||p.equals("SH"))return "ㅅ";if(p.equals("Z")||p.equals("ZH")||p.equals("JH"))return "ㅈ";if(p.equals("CH"))return "ㅊ";if(p.equals("M"))return "ㅁ";if(p.equals("N"))return "ㄴ";if(p.equals("NG"))return "ㅇ";if(p.equals("L")||p.equals("R"))return "ㄹ";if(p.equals("HH"))return "ㅎ";return "ㅇ";}
  private static String coda(String p){if(p.equals("B")||p.equals("P")||p.equals("F")||p.equals("V"))return "ㅂ";if(p.equals("D")||p.equals("T")||p.equals("S")||p.equals("Z")||p.equals("SH")||p.equals("CH")||p.equals("JH"))return "ㅅ";if(p.equals("G")||p.equals("K"))return "ㄱ";if(p.equals("M"))return "ㅁ";if(p.equals("N"))return "ㄴ";if(p.equals("NG"))return "ㅇ";if(p.equals("L")||p.equals("R"))return "ㄹ";return "";}
  private static String spellPhone(String p){return p.equals("F")?"에프":p.equals("V")?"브이":p;}
  private static String compose(String onset,String vowel,String coda){int ii=onset.equals("ㄱ")?0:onset.equals("ㄴ")?2:onset.equals("ㄷ")?3:onset.equals("ㄹ")?5:onset.equals("ㅁ")?6:onset.equals("ㅂ")?7:onset.equals("ㅅ")?9:onset.equals("ㅈ")?12:onset.equals("ㅊ")?14:onset.equals("ㅋ")?15:onset.equals("ㅌ")?16:onset.equals("ㅍ")?17:onset.equals("ㅎ")?18:11;String[] vs={"아","애","어","에","오","우","이"};int vi=java.util.Arrays.asList(vs).indexOf(vowel);if(vi<0)vi=2;String[] fs={"","ㄱ","ㄴ","ㄷ","ㄹ","ㅁ","ㅂ","ㅅ","ㅇ"};int fi=java.util.Arrays.asList(fs).indexOf(coda);if(fi<0)fi=0;return String.valueOf((char)(0xAC00+(ii*21+vi)*28+fi));}

  private static String replace(String text, String key, String value, boolean allowKoreanAdjacency) {
    String left = allowKoreanAdjacency ? "(?<![A-Za-z0-9-])" : "(?<![A-Za-z0-9-])";
    String right = "(?![A-Za-z0-9-])";
    return text.replaceAll("(?i)" + left + Pattern.quote(key) + right, Matcher.quoteReplacement(value));
  }

  private static String replaceGtx(String text) {
    Matcher matcher = Pattern.compile("(?i)(?<![A-Za-z0-9-])GTX-([ABCZ])(?![A-Za-z0-9-])").matcher(text);
    StringBuffer out = new StringBuffer();
    while (matcher.find()) {
      String suffix = matcher.group(1).toUpperCase(Locale.ROOT);
      String spoken = suffix.equals("A") ? "지티엑스 에이" : suffix.equals("B") ? "지티엑스 비" : suffix.equals("C") ? "지티엑스 씨" : "지티엑스 지";
      matcher.appendReplacement(out, Matcher.quoteReplacement(spoken));
    }
    matcher.appendTail(out);
    return out.toString();
  }

  private static String normalizeKey(String value) {
    return Normalizer.normalize(value == null ? "" : value, Normalizer.Form.NFKC)
        .trim().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
  }

  private static byte[] readAll(InputStream stream) throws Exception {
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    byte[] buffer = new byte[8192]; int count;
    while ((count = stream.read(buffer)) >= 0) if (count > 0) out.write(buffer, 0, count);
    return out.toByteArray();
  }

  private static double elapsedMs(long startNs) { return (System.nanoTime() - startNs) / 1_000_000.0; }

  private static Map<String, String> mapOf(String... values) {
    Map<String, String> map = new LinkedHashMap<>();
    for (int i = 0; i + 1 < values.length; i += 2) map.put(values[i], values[i + 1]);
    return map;
  }

  private static final class LexiconTable {
    private final String[] keys;
    private final String[] values;
    private final byte[] rawValues;
    private final int[] valueOffsets;
    private final int[] valueLengths;
    private final boolean sorted;
    private int size;

    LexiconTable(int expected) {
      int capacity = 1;
      while (capacity < expected * 2) capacity <<= 1;
      keys = new String[capacity]; values = new String[capacity]; rawValues = null;
      valueOffsets = null; valueLengths = null; sorted = false;
    }

    LexiconTable(int expected, boolean sorted) {
      keys = new String[expected]; values = new String[expected]; rawValues = null;
      valueOffsets = null; valueLengths = null; this.sorted = sorted;
    }

    LexiconTable(int expected, boolean sorted, byte[] rawValues) {
      keys = new String[expected]; values = null; this.rawValues = rawValues;
      valueOffsets = new int[expected]; valueLengths = new int[expected]; this.sorted = sorted;
    }

    LexiconTable(Map<String, String> source) {
      this(source.size());
      for (Map.Entry<String, String> entry : source.entrySet()) put(entry.getKey(), entry.getValue());
    }

    void put(String key, String value) {
      int index = key.hashCode() & (keys.length - 1);
      while (keys[index] != null && !keys[index].equals(key)) index = (index + 1) & (keys.length - 1);
      if (keys[index] == null) { keys[index] = key; size++; }
      values[index] = value;
    }

    void putAt(int index, String key, String value) {
      keys[index] = key; values[index] = value; size = index + 1;
    }

    void putAt(int index, String key, int valueOffset, int valueLength) {
      keys[index] = key; valueOffsets[index] = valueOffset; valueLengths[index] = valueLength; size = index + 1;
    }

    String get(String key) {
      if (sorted) {
        int index = java.util.Arrays.binarySearch(keys, 0, size, key);
        if (index < 0) return null;
        return rawValues == null ? values[index] : new String(rawValues, valueOffsets[index], valueLengths[index], StandardCharsets.UTF_8);
      }
      int index = key.hashCode() & (keys.length - 1);
      while (keys[index] != null) {
        if (keys[index].equals(key)) return rawValues == null ? values[index] : new String(rawValues, valueOffsets[index], valueLengths[index], StandardCharsets.UTF_8);
        index = (index + 1) & (keys.length - 1);
      }
      return null;
    }

    int size() { return size; }
  }
}
