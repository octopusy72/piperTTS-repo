package com.yiwoosolution.piperprototype;

import android.content.Context;
import android.content.SharedPreferences;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import org.json.JSONArray;
import org.json.JSONObject;

/** SharedPreferences/JSON repository compatible with the legacy reading_rules store. */
final class ReadingRuleRepository {
  static final int MAX_RULES = 500, MAX_SOURCE = 200, MAX_READING = 500;
  private static final String PREFS = "reading_rules", KEY = "rules_json";
  private static final Pattern PROTECTED_SOURCE = Pattern.compile("(?i)^(?:[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}|https?://\\S+|\\d{1,3}(?:\\.\\d{1,3}){3}|0\\d{1,2}-\\d{3,4}-\\d{4})$");
  private final SharedPreferences prefs;

  ReadingRuleRepository(Context context) { prefs = context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE); }

  synchronized boolean enabled() { return loadEnabled(); }
  synchronized void setEnabled(boolean value) {
    try { JSONObject root = loadRoot(); root.put("enabled", value); save(root); } catch (Exception ignored) {}
  }
  synchronized List<ReadingRule> list() {
    JSONArray array = loadRoot().optJSONArray("rules");
    if (array == null) return Collections.emptyList();
    List<ReadingRule> out = new ArrayList<>();
    for (int i = 0; i < array.length(); i++) { ReadingRule rule = parse(array.optJSONObject(i)); if (rule != null) out.add(rule); }
    return out;
  }
  synchronized boolean add(ReadingRule rule) { return saveRule(rule, null); }
  synchronized boolean update(ReadingRule rule) { return saveRule(rule, rule.id); }
  synchronized String exportJson() {
    try {
      JSONObject root = new JSONObject().put("schemaVersion", 1).put("format", "yiwoo-reading-rules");
      JSONArray array = new JSONArray(); for (ReadingRule rule : list()) array.put(toJson(rule, null));
      root.put("rules", array); return root.toString(2);
    } catch (Exception e) { return ""; }
  }
  synchronized int importJson(String json) {
    try {
      JSONObject root = new JSONObject(json); if (root.optInt("schemaVersion", -1) != 1 || !"yiwoo-reading-rules".equals(root.optString("format"))) return 0;
      JSONArray array = root.optJSONArray("rules"); if (array == null || array.length() > MAX_RULES) return 0;
      int added = 0; for (int i = 0; i < array.length(); i++) { ReadingRule rule = parse(array.optJSONObject(i)); if (rule != null && add(rule)) added++; }
      return added;
    } catch (Exception e) { return 0; }
  }
  synchronized int importJson(InputStream input) {
    try { java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream(); byte[] b = new byte[8192]; int n, total = 0; while ((n = input.read(b)) >= 0) { total += n; if (total > 1024 * 1024) return 0; out.write(b, 0, n); } return importJson(new String(out.toByteArray(), StandardCharsets.UTF_8)); }
    catch (Exception e) { return 0; }
  }
  synchronized void delete(String id) {
    JSONObject root = loadRoot(); JSONArray old = root.optJSONArray("rules"); JSONArray next = new JSONArray();
    if (old != null) for (int i = 0; i < old.length(); i++) if (!id.equals(old.optJSONObject(i).optString("id"))) next.put(old.optJSONObject(i));
    try { root.put("rules", next); save(root); } catch (Exception ignored) {}
  }

  private boolean saveRule(ReadingRule rule, String editingId) {
    if (!valid(rule)) return false;
    List<ReadingRule> current = list();
    for (ReadingRule peer : current) {
      if (editingId != null && editingId.equals(peer.id)) continue;
      if (sameMatcher(peer, rule)) return false;
    }
    JSONObject root = loadRoot(); JSONArray old = root.optJSONArray("rules"); JSONArray next = new JSONArray(); boolean replaced = false;
    if (old != null) for (int i = 0; i < old.length(); i++) {
      JSONObject item = old.optJSONObject(i); if (item == null) continue;
      if (editingId != null && editingId.equals(item.optString("id"))) { next.put(toJson(rule, editingId)); replaced = true; } else next.put(item);
    }
    if (!replaced) next.put(toJson(rule, rule.id.isEmpty() ? UUID.randomUUID().toString() : rule.id));
    if (next.length() > MAX_RULES) return false;
    try { root.put("rules", next); save(root); return true; } catch (Exception e) { return false; }
  }

  private boolean valid(ReadingRule r) {
    if (r == null || (r.source.trim().isEmpty() && r.prefix.trim().isEmpty())) return false;
    if (r.source.length() > MAX_SOURCE || r.prefix.length() > MAX_SOURCE || r.reading.length() > MAX_READING) return false;
    if ((r.type == ReadingRule.Type.EXACT_TEXT_CUSTOM || r.type == ReadingRule.Type.EXACT_TEXT_SPELL_OUT) && PROTECTED_SOURCE.matcher(r.source.trim()).matches()) return false;
    if (r.type == ReadingRule.Type.CONTEXT_PREFIX_NUMBER && Pattern.compile("(?i)(pin|otp|password|passcode|verification\\s+code|security\\s+code|비밀번호|인증번호|인증\\s*코드)").matcher(r.prefix).find()) return false;
    return r.digitCount == null || (r.digitCount >= 1 && r.digitCount <= 32);
  }
  private boolean sameMatcher(ReadingRule a, ReadingRule b) {
    return a.type == b.type && a.language == b.language && a.source.equals(b.source) && a.prefix.equals(b.prefix)
        && java.util.Objects.equals(a.digitCount, b.digitCount) && a.caseSensitive == b.caseSensitive;
  }
  private JSONObject loadRoot() { try { return new JSONObject(prefs.getString(KEY, "{}")); } catch (Exception e) { return new JSONObject(); } }
  private boolean loadEnabled() { return loadRoot().optBoolean("enabled", true); }
  private void save(JSONObject root) { prefs.edit().putString(KEY, root.toString()).apply(); }
  private JSONObject toJson(ReadingRule r, String id) {
    try {
      return new JSONObject().put("id", id).put("enabled", r.enabled).put("type", r.type.name()).put("language", r.language.name())
          .put("source", r.source).put("reading", r.reading).put("prefix", r.prefix).put("digitCount", r.digitCount).put("caseSensitive", r.caseSensitive);
    } catch (Exception e) { return new JSONObject(); }
  }
  private ReadingRule parse(JSONObject j) {
    if (j == null) return null;
    try {
      Integer count = j.isNull("digitCount") ? null : j.optInt("digitCount");
      return new ReadingRule(j.optString("id", UUID.randomUUID().toString()), j.optBoolean("enabled", true),
          ReadingRule.Type.valueOf(j.optString("type")), ReadingRule.Language.valueOf(j.optString("language")),
          j.optString("source"), j.optString("reading"), j.optString("prefix"), count, j.optBoolean("caseSensitive", true));
    } catch (Exception e) { return null; }
  }
}
