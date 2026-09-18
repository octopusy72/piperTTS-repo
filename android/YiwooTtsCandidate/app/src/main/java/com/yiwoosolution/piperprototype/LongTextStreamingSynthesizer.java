package com.yiwoosolution.piperprototype;

import android.util.Log;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/** Shared bounded producer/consumer stream for Voice Test and Android TTS. */
final class LongTextStreamingSynthesizer {
  private static final String TAG = "YiwooPiperKo";
  private static final int QUEUE_CAPACITY = 1;
  private static final ExecutorService PRODUCER = Executors.newSingleThreadExecutor(r -> {
    Thread t = new Thread(r, "yiwoo-chunk-producer"); t.setDaemon(true); return t;
  });
  interface ChunkListener {
    void onChunk(short[] pcm, int index, int total, double inferenceMs, int pauseMs) throws Exception;
    default void onStartupReserve(long millis) { }
  }
  interface Backend { void synthesizeChunks(String text, float rate, ChunkListener listener) throws Exception; }
  private final Backend backend;

  LongTextStreamingSynthesizer(PiperModelManager manager) { this.backend = manager::synthesizeChunks; }
  LongTextStreamingSynthesizer(EnglishModelManager manager) { this.backend = manager::synthesizeChunks; }
  LongTextStreamingSynthesizer(Backend backend) { this.backend = backend; }

  StreamHandle start(String text) { return start(text, 1f); }

  StreamHandle start(String text, float rate) {
    StreamHandle handle = new StreamHandle();
    handle.future = PRODUCER.submit(() -> {
      try {
        backend.synthesizeChunks(text, rate, new ChunkListener() {
          @Override public void onStartupReserve(long millis) { handle.startupReserveMs = millis; }
          @Override public void onChunk(short[] pcm, int index, int total, double inferenceMs, int pauseMs) throws Exception {
          if (handle.cancelled.get()) throw new CancellationException("STREAM_CANCELLED");
          long queueStarted = System.nanoTime();
          if (index == 0) handle.firstOfferedNs = queueStarted;
          if (index == 1) handle.secondProduced.countDown();
          Log.i(TAG, "QUEUE_PUT_START index=" + index + " total=" + total);
          handle.queue.put(new Chunk(pcm, index, total, inferenceMs, pauseMs));
          Log.i(TAG, "QUEUE_PUT_END index=" + index + " waitMs=" + ((System.nanoTime() - queueStarted) / 1_000_000.0) + " queue=" + handle.queue.size());
          }
        });
        handle.finish();
      } catch (CancellationException error) {
        handle.finish();
      } catch (Throwable error) {
        Log.e(TAG, "CHUNK_STREAM_PRODUCER_ERROR", error);
        handle.error = error;
        handle.finish();
      }
    });
    return handle;
  }

  static final class Chunk {
    final short[] pcm; final int index; final int total; final double inferenceMs; final int pauseMs;
    Chunk(short[] pcm, int index, int total, double inferenceMs, int pauseMs) { this.pcm = pcm; this.index = index; this.total = total; this.inferenceMs = inferenceMs; this.pauseMs = pauseMs; }
  }

  static final class StreamHandle {
    private final ArrayBlockingQueue<Object> queue = new ArrayBlockingQueue<>(QUEUE_CAPACITY);
    private final AtomicBoolean cancelled = new AtomicBoolean(false);
    private static final Object END = new Object();
    private volatile Throwable error;
    private volatile Future<?> future;
    private final CountDownLatch secondProduced = new CountDownLatch(1);
    private volatile long startupReserveMs, firstOfferedNs;

    Chunk take() throws Exception {
      Object value = queue.take();
      if (value == END) {
        if (error != null) throw new Exception("CHUNK_STREAM_FAILED", error);
        if (cancelled.get()) throw new CancellationException("STREAM_CANCELLED");
        return null;
      }
      Chunk chunk = (Chunk) value;
      if (chunk.index == 0 && chunk.total > 1 && startupReserveMs > 0) {
        long remaining = firstOfferedNs + TimeUnit.MILLISECONDS.toNanos(startupReserveMs) - System.nanoTime();
        if (remaining > 0) secondProduced.await(remaining, TimeUnit.NANOSECONDS);
        if (cancelled.get()) throw new CancellationException("STREAM_CANCELLED");
      }
      return chunk;
    }

    void finish() {
      secondProduced.countDown();
      if (cancelled.get()) return;
      try {
        // Backpressure must sleep, not consume a CPU core while audio drains.
        queue.put(END);
      } catch (InterruptedException interrupted) {
        Thread.currentThread().interrupt();
        if (!cancelled.get()) error = interrupted;
        queue.clear();
        queue.offer(END);
      }
    }

    void cancel() {
      if (!cancelled.compareAndSet(false, true)) return;
      secondProduced.countDown();
      queue.clear();
      Future<?> f = future;
      if (f != null) f.cancel(true);
      queue.offer(END);
    }
  }
}
