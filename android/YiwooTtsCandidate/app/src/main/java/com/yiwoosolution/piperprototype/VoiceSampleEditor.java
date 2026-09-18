package com.yiwoosolution.piperprototype;

import android.content.Context;
import android.view.MotionEvent;
import android.widget.EditText;

/** Give the bounded text field its own drag, releasing it at the scroll edges. */
public final class VoiceSampleEditor extends EditText {
  private float lastY;
  public VoiceSampleEditor(Context context, android.util.AttributeSet attrs) {
    // XML scrollbars initializes ScrollBarDrawable as well as its flags on API 30.
    super(context, attrs);
    setScrollBarStyle(SCROLLBARS_INSIDE_INSET);
    setOverScrollMode(OVER_SCROLL_IF_CONTENT_SCROLLS);
  }
  @Override public boolean onTouchEvent(MotionEvent event) {
    int action = event.getActionMasked();
    boolean keep = false;
    if (action == MotionEvent.ACTION_DOWN) {
      keep = canScrollVertically(1) || canScrollVertically(-1);
    } else if (action == MotionEvent.ACTION_MOVE) {
      keep = canScrollVertically(event.getY() < lastY ? 1 : -1);
    }
    if (getParent() != null) getParent().requestDisallowInterceptTouchEvent(keep);
    lastY = event.getY();
    return super.onTouchEvent(event);
  }
}
