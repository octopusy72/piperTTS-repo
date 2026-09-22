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
      "notification", "노티피케이션", "settings", "세팅즈", "producer", "프로듀서",
      "test", "테스트", "normal", "노멀", "example", "이그잼플", "com", "컴");
  private static final Map<String, String> PHRASES = mapOf(
      "notification settings", "노티피케이션 세팅즈", "notification permission", "노티피케이션 퍼미션");
  private static final Map<String, String> ACRONYMS = mapOf(
      "ai", "에이아이", "usb", "유에스비", "cpu", "씨피유", "gpu", "지피유", "ssd", "에스에스디", "hdmi", "에이치디엠아이",
      "tv", "티브이",
      "ip", "아이피", "url", "유알엘", "http", "에이치티티피", "https", "에이치티티피에스", "dns", "디엔에스", "sdk", "에스디케이",
      "email", "이메일", "e-mail", "이메일", "ipv4", "아이피 버전 사");
  /** Case-sensitive baseball notation; lowercase war/era remain ordinary English words. */
  private static final Map<String, String> BASEBALL_ACRONYMS = mapOf(
      "OPS+", "오피에스 플러스", "ERA+", "이알에이 플러스", "wRC+", "더블유알씨 플러스",
      "OPS", "오피에스", "ERA", "이알에이", "RBI", "알비아이", "AVG", "에이브이지",
      "OBP", "오비피", "SLG", "에스엘지", "WHIP", "윕", "WAR", "워", "FIP", "에프아이피",
      "BABIP", "바빕", "WPA", "더블유피에이", "KBO", "케이비오", "MLB", "엠엘비",
      "NPB", "엔피비", "HR", "에이치알", "AB", "에이비", "PA", "피에이", "HBP", "에이치비피",
      "BB", "비비", "SO", "에스오", "SB", "에스비", "CS", "씨에스", "IP", "아이피",
      "SV", "에스브이", "HLD", "에이치엘디", "QS", "큐에스");
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
    for (Map.Entry<String, String> entry : BASEBALL_ACRONYMS.entrySet()) {
      out = replaceCaseSensitive(out, entry.getKey(), entry.getValue());
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
      if (phones != null) {
        String spoken = transliterate(phones);
        if (spoken != null) return spoken;
      }
    }
    return spellLetters(token);
  }

  private static String spellLetters(String token) {
    StringBuilder out = new StringBuilder();
    String[] names = {"에이","비","씨","디","이","에프","지","에이치","아이","제이","케이","엘","엠","엔","오","피","큐","알","에스","티","유","브이","더블유","엑스","와이","지"};
    for (int i=0;i<token.length();i++) { char c=Character.toUpperCase(token.charAt(i)); if(c>='A'&&c<='Z'){if(out.length()>0)out.append(' ');out.append(names[c-'A']);} }
    return out.toString();
  }

  static String transliterate(String value) {
    String result = LegacyCmuEnglishConverter.convertWord(
        java.util.Arrays.asList(value.trim().split("\\s+")));
    // Never pass raw ARPAbet or uncomposed jamo to the Korean phonemizer.
    return result.matches("[가-힣]+") ? result : null;
  }

  private static String replace(String text, String key, String value, boolean allowKoreanAdjacency) {
    String left = allowKoreanAdjacency ? "(?<![A-Za-z0-9-])" : "(?<![A-Za-z0-9-])";
    String right = "(?![A-Za-z0-9-])";
    return text.replaceAll("(?i)" + left + Pattern.quote(key) + right, Matcher.quoteReplacement(value));
  }

  private static String replaceCaseSensitive(String text, String key, String value) {
    String boundary = "[A-Za-z0-9+.#-]";
    return text.replaceAll("(?<!" + boundary + ")" + Pattern.quote(key) + "(?!" + boundary + ")",
        Matcher.quoteReplacement(value));
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
