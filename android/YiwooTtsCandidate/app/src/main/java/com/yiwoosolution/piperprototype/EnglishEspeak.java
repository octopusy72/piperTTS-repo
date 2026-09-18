package com.yiwoosolution.piperprototype;

import android.content.Context;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;

/** Minimal eSpeak-ng frontend bridge; waveform synthesis remains Piper ONNX. */
final class EnglishEspeak {
  static { System.loadLibrary("yiwoo_espeak"); }
  private static boolean initialized;

  private EnglishEspeak() {}

  static synchronized void ensureReady(Context context) throws Exception {
    if (initialized) return;
    File data = new File(context.getFilesDir(), "espeak-ng-data");
    copyTree(context, "espeak-ng-data", data);
    if (!nativeInit(data.getAbsolutePath())) throw new IllegalStateException("ESPEAK_INIT_FAILED");
    initialized = true;
  }

  static synchronized String phonemize(Context context, String text) throws Exception {
    ensureReady(context);
    String result = nativePhonemize(text);
    if (result == null) throw new IllegalStateException("ESPEAK_PHONEMIZE_FAILED");
    return result;
  }

  private static void copyTree(Context context, String assetRoot, File output) throws Exception {
    if (output.exists() && new File(output, "phontab").exists()) return;
    copyEntry(context, assetRoot, output);
  }

  private static void copyEntry(Context context, String assetPath, File output) throws Exception {
    String[] children = context.getAssets().list(assetPath);
    if (children != null && children.length > 0) {
      if (!output.exists() && !output.mkdirs()) throw new IllegalStateException("ESPEAK_DATA_DIR_FAILED");
      for (String child : children) copyEntry(context, assetPath + "/" + child, new File(output, child));
      return;
    }
    if (!output.getParentFile().exists() && !output.getParentFile().mkdirs()) throw new IllegalStateException("ESPEAK_DATA_PARENT_FAILED");
    try (InputStream in = context.getAssets().open(assetPath); FileOutputStream out = new FileOutputStream(output)) {
      byte[] buffer = new byte[8192]; int count;
      while ((count = in.read(buffer)) != -1) out.write(buffer, 0, count);
    }
  }

  private static native boolean nativeInit(String dataPath);
  static native String nativePhonemize(String text);
}
