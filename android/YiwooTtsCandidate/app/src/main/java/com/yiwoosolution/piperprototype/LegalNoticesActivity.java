package com.yiwoosolution.piperprototype;

import android.app.Activity;
import android.os.Bundle;
import android.graphics.Typeface;
import android.view.Gravity;
import android.widget.ScrollView;
import android.widget.TextView;
import java.io.InputStream;
import java.io.ByteArrayOutputStream;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.ArrayAdapter;
import android.widget.AdapterView;
import android.view.View;

/** Displays only notices shipped with this Candidate build. */
public final class LegalNoticesActivity extends Activity {
  private TextView text;
  private static final String[] FILES = {"NOTICE", "licenses/GPL-3.0.txt", "licenses/ONNX_THIRD_PARTY.txt", "licenses/ESPEAK_BSD2.txt", "licenses/UNICODE.txt", "licenses/APACHE-2.0.txt", "licenses/CMUDICT.txt"};
  private static final String[] TITLES = {"고지", "GNU GPL v3", "ONNX Runtime 구성요소", "eSpeak BSD", "Unicode", "Apache 2.0", "CMUdict"};
  @Override public void onCreate(Bundle state) {
    super.onCreate(state);
    ScrollView scroll = new ScrollView(this);
    text = new TextView(this);
    text.setTextSize(14f);
    text.setTypeface(Typeface.DEFAULT);
    text.setGravity(Gravity.START);
    int pad = (int) (20 * getResources().getDisplayMetrics().density);
    text.setPadding(pad, pad, pad, pad);
    showLicense(0);
    scroll.addView(text);
    setTitle("오픈소스 고지");
    LinearLayout root = new LinearLayout(this);
    root.setOrientation(LinearLayout.VERTICAL);
    Spinner picker = new Spinner(this);
    picker.setContentDescription("라이선스 선택");
    ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, TITLES);
    adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
    picker.setAdapter(adapter);
    picker.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
      @Override public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
        showLicense(position);
        scroll.scrollTo(0, 0);
      }
      @Override public void onNothingSelected(AdapterView<?> parent) { }
    });
    root.addView(picker, new LinearLayout.LayoutParams(-1, -2));
    root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
    setContentView(root);
  }

  private void showLicense(int index) {
    try (InputStream in = getAssets().open(FILES[index]); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
      byte[] buffer = new byte[8192]; int count;
      while ((count = in.read(buffer)) != -1) out.write(buffer, 0, count);
      text.setText(out.toString("UTF-8"));
      text.scrollTo(0, 0);
    } catch (Exception error) {
      text.setText("현재 빌드에 포함된 고지 내용을 불러올 수 없습니다.");
    }
  }
}
