package com.yiwoosolution.piperprototype;

import android.content.Context;
import android.util.Log;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.Arrays;
import java.util.Locale;

/** Lazy, primary-pronunciation CMUdict table. It is loaded only for an OOV Latin token. */
final class CmuDictTable {
  private static final String TAG = "YiwooPronunciation";
  private static final byte[] MAGIC = "YIWOOCMU1".getBytes(StandardCharsets.US_ASCII);
  private final String[] keys;
  private final byte[] bytes;
  private final int[] offsets;
  private final int[] lengths;
  private final int size;
  final long loadMs;

  private CmuDictTable(Context context) {
    long start = System.nanoTime();
    try {
      InputStream in = context.getAssets().open("pronunciation/cmudict_runtime.bin");
      byte[] raw = readAll(in); in.close();
      ByteBuffer b = ByteBuffer.wrap(raw).order(ByteOrder.BIG_ENDIAN);
      byte[] magic = new byte[MAGIC.length]; b.get(magic);
      if (!Arrays.equals(magic, MAGIC)) throw new IllegalStateException("CMUDICT_BAD_MAGIC");
      int version=b.getInt(); if (version != 1) throw new IllegalStateException("CMUDICT_BAD_VERSION="+version);
      int count=b.getInt(); if (count<=0 || count>1000000) throw new IllegalStateException("CMUDICT_BAD_COUNT="+count);
      keys=new String[count]; offsets=new int[count]; lengths=new int[count]; bytes=raw; size=count;
      String previous="";
      for (int i=0;i<count;i++) {
        int kl=b.getInt(), vl=b.getInt();
        if (kl<=0 || vl<=0 || kl>b.remaining() || vl>b.remaining()-kl) throw new IllegalStateException("CMUDICT_BAD_ENTRY="+i);
        int ko=b.position(); b.position(ko+kl); int vo=b.position(); b.position(vo+vl);
        keys[i]=new String(raw,ko,kl,StandardCharsets.UTF_8);
        if (i>0 && previous.compareTo(keys[i])>=0) throw new IllegalStateException("CMUDICT_UNSORTED="+i);
        previous=keys[i]; offsets[i]=vo; lengths[i]=vl;
      }
      if (b.hasRemaining()) throw new IllegalStateException("CMUDICT_TRAILING_BYTES");
      loadMs=(System.nanoTime()-start)/1_000_000L;
      Log.i(TAG,"CMUDICT_READY entries="+count+" loadMs="+loadMs);
    } catch (Exception e) { throw new IllegalStateException("CMUDICT_LOAD_FAILED",e); }
  }
  static CmuDictTable load(Context c) { return new CmuDictTable(c.getApplicationContext()); }
  String get(String word) {
    String key=Normalizer.normalize(word,Normalizer.Form.NFKC).trim().toLowerCase(Locale.ROOT);
    int i=Arrays.binarySearch(keys,0,size,key); if (i<0) return null;
    return new String(bytes,offsets[i],lengths[i],StandardCharsets.US_ASCII);
  }
  int size(){return size;}
  private static byte[] readAll(InputStream in) throws Exception { ByteArrayOutputStream o=new ByteArrayOutputStream(); byte[] b=new byte[8192]; int n; while((n=in.read(b))>=0) if(n>0)o.write(b,0,n); return o.toByteArray(); }
}
