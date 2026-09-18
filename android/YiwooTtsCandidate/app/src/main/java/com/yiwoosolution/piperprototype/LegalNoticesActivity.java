package com.yiwoosolution.piperprototype;

import android.app.Activity;
import android.os.Bundle;
import android.graphics.Typeface;
import android.view.Gravity;
import android.widget.ScrollView;
import android.widget.TextView;
import java.io.InputStream;

/** Displays only notices shipped with this Candidate build. */
public final class LegalNoticesActivity extends Activity {
  @Override public void onCreate(Bundle state) {
    super.onCreate(state);
    ScrollView scroll = new ScrollView(this);
    TextView text = new TextView(this);
    text.setTextSize(14f);
    text.setTypeface(Typeface.DEFAULT);
    text.setGravity(Gravity.START);
    int pad = (int) (20 * getResources().getDisplayMetrics().density);
    text.setPadding(pad, pad, pad, pad);
    String contents;
    try (InputStream in = getAssets().open("NOTICE")) {
      byte[] bytes = new byte[in.available()];
      int offset = 0;
      int count;
      while ((count = in.read(bytes, offset, bytes.length - offset)) > 0) offset += count;
      contents = new String(bytes, 0, offset, "UTF-8");
    } catch (Exception error) {
      contents = "현재 빌드에 포함된 고지 내용을 불러올 수 없습니다.";
    }
    text.setText(contents);
    scroll.addView(text);
    setTitle("오픈소스 고지");
    setContentView(scroll);
  }
}
