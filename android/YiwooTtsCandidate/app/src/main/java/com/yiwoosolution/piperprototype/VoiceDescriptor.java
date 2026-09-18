package com.yiwoosolution.piperprototype;

/** Immutable metadata for a selectable prototype voice. */
final class VoiceDescriptor {
  final String voiceId, displayName, locale, speakerId, architecture, frontendVersion, vocabVersion, modelPath;
  final String status;
  final int sampleRate;
  final boolean enabled, modelAvailable;

  VoiceDescriptor(String voiceId, String displayName, String locale, String speakerId,
      String architecture, String frontendVersion, String vocabVersion, String modelPath,
      int sampleRate, boolean enabled, boolean modelAvailable, String status) {
    this.voiceId = voiceId; this.displayName = displayName; this.locale = locale;
    this.speakerId = speakerId; this.architecture = architecture;
    this.frontendVersion = frontendVersion; this.vocabVersion = vocabVersion;
    this.modelPath = modelPath; this.sampleRate = sampleRate;
    this.enabled = enabled; this.modelAvailable = modelAvailable;
    this.status = status;
  }
}
