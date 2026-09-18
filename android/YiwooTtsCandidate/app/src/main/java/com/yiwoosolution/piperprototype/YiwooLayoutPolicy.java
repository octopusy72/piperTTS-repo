package com.yiwoosolution.piperprototype;

/** Decisions use available window dp, never device model or physical pixels. */
final class YiwooLayoutPolicy {
  static final int RAIL_DP=148;
  static final int MAX_CONTENT_DP=1080;
  static boolean dashboard(int widthDp,boolean landscape){return landscape&&widthDp>=560;}
  static boolean twoColumns(int contentWidthDp,float fontScale){
    return contentWidthDp-36>=2*136*Math.max(1f,fontScale)+10;
  }
}
