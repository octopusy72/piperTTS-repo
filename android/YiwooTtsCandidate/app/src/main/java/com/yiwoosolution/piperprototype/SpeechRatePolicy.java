package com.yiwoosolution.piperprototype;

/** Request-local ONNX duration scale, never a mutation of shared model state. */
final class SpeechRatePolicy {
  private SpeechRatePolicy() {}
  static float lengthScale(float configuredScale, float rate) {
    return configuredScale / SpeechPcmProcessor.bounded(rate);
  }
}
