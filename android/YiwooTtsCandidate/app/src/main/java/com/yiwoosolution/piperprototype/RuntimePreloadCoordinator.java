package com.yiwoosolution.piperprototype;

import android.content.Context;
import android.util.Log;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Process-scoped readiness, including frontend and first inference. Never waits on the UI. */
final class RuntimePreloadCoordinator {
  private static final ExecutorService WORKER=Executors.newSingleThreadExecutor(r->new Thread(r,"yiwoo-runtime-prepare"));
  private static CompletableFuture<Void> korean,english;
  private RuntimePreloadCoordinator() {}
  static void ensureRuntimePreloaded(Context context) {
    Context app=context.getApplicationContext();
    if(EngineRetentionSettings.mode(app)==EngineRetentionSettings.Mode.ON_DEMAND)return;
    RuntimeRetentionService.ensureStarted(app);
    prepare(app,VoiceRegistry.selectedEnglish(app));
  }
  static synchronized CompletableFuture<Void> prepare(Context context,boolean en) {
    Context app=context.getApplicationContext();
    // On-demand requests initialize in synthesis itself; warming then immediately
    // releasing a session would double the first-request cold start.
    if(EngineRetentionSettings.mode(app)==EngineRetentionSettings.Mode.ON_DEMAND)
      return CompletableFuture.completedFuture(null);
    CompletableFuture<Void> existing=en?english:korean;
    boolean loaded=en?EnglishModelManager.shared(app).isReady():PiperModelManager.shared(app).isReady();
    if(existing!=null&&!existing.isCompletedExceptionally()&&(!existing.isDone()||loaded))return existing;
    CompletableFuture<Void> task=CompletableFuture.runAsync(()->{
      long start=android.os.SystemClock.elapsedRealtime();
      Log.i("YiwooRuntime","RUNTIME_PREPARE_BEGIN language="+(en?"en":"ko"));
      try {
        // Discard the warmup PCM; use the same singleton/session and production frontend.
        if(en)EnglishModelManager.shared(app).synthesizeChunks("Hello.",(pcm,index,total,ms,pause)->{});
        else {
          KoreanFrontend.normalize(app,"2026년 9월 17일, 속도는 80km입니다.");
          // Measure both a short and representative sentence on this device/session.
          PiperModelManager.shared(app).synthesize("안녕하세요. 음성 서비스를 사용할 준비가 되었습니다.");
        }
        Log.i("YiwooRuntime","RUNTIME_PREPARE_READY elapsedMs="+(android.os.SystemClock.elapsedRealtime()-start));
      } catch(Exception e){Log.e("YiwooRuntime","RUNTIME_PREPARE_FAILED",e);throw new CompletionException(e);}
    },WORKER);
    if(en)english=task;else korean=task;
    return task;
  }
  static synchronized boolean isPreparing() {
    return korean!=null&&!korean.isDone() || english!=null&&!english.isDone();
  }
}
