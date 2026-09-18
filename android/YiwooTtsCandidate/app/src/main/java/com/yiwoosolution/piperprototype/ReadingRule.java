package com.yiwoosolution.piperprototype;

/** Portable user reading rule contract compatible with the legacy JSON format. */
final class ReadingRule {
  enum Type { EXACT_TEXT_CUSTOM, EXACT_TEXT_SPELL_OUT, CONTEXT_PREFIX_NUMBER }
  enum Language { ALL, KO_KR, EN_US }

  final String id;
  final boolean enabled;
  final Type type;
  final Language language;
  final String source;
  final String reading;
  final String prefix;
  final Integer digitCount;
  final boolean caseSensitive;

  ReadingRule(String id, boolean enabled, Type type, Language language, String source,
      String reading, String prefix, Integer digitCount, boolean caseSensitive) {
    this.id = id == null ? "" : id;
    this.enabled = enabled;
    this.type = type == null ? Type.EXACT_TEXT_CUSTOM : type;
    this.language = language == null ? Language.ALL : language;
    this.source = source == null ? "" : source;
    this.reading = reading == null ? "" : reading;
    this.prefix = prefix == null ? "" : prefix;
    this.digitCount = digitCount;
    this.caseSensitive = caseSensitive;
  }
}
