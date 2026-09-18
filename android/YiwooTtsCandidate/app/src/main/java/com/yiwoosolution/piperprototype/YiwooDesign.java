package com.yiwoosolution.piperprototype;

import android.content.Context;
import android.graphics.*;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.content.res.ColorStateList;
import android.view.Gravity;
import android.widget.*;

/** View-based product design tokens. No runtime or preference dependencies. */
final class YiwooDesign {
  static final class YiwooColors {
    static final int FOREST=0xff123f35, EMERALD=0xff267460, MINT=0xffe5eee7;
    static final int CREAM=0xfff6f5ee, SURFACE=0xfffffef9, INK=0xff193b32, MUTED=0xff708279;
  }
  static final class YiwooTypography { static final int TITLE=25, CARD=19, BODY=14, CAPTION=12; }
  static final class YiwooSpacing { static final int EDGE=18, GAP=10, CARD=18; }
  static final class YiwooShapes { static final int CARD=22, TILE=18; }
  static int dp(Context c,int n){return Math.round(n*c.getResources().getDisplayMetrics().density);}
  static GradientDrawable shape(Context c,int color,int radius){GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(dp(c,radius));return d;}
  static TextView text(Context c,String value,int size,int color,boolean bold){
    TextView v=new TextView(c);v.setText(value);v.setTextSize(size);v.setTextColor(color);
    v.setTypeface(Typeface.create(bold?"sans-serif-medium":"sans-serif",Typeface.NORMAL));
    v.setIncludeFontPadding(false);v.setLineSpacing(dp(c,3),1);return v;
  }
  static LinearLayout surface(Context c){LinearLayout v=new LinearLayout(c);v.setOrientation(LinearLayout.VERTICAL);int p=dp(c,YiwooSpacing.CARD);v.setPadding(p,p,p,p);v.setBackground(shape(c,YiwooColors.SURFACE,YiwooShapes.CARD));v.setElevation(dp(c,1));return v;}
  static ImageView icon(Context c,int resource,boolean dark){ImageView v=new ImageView(c);v.setImageResource(resource);v.setColorFilter(dark?0xffeff9f2:YiwooColors.EMERALD);v.setBackground(shape(c,dark?0xff398371:YiwooColors.MINT,40));int p=dp(c,11);v.setPadding(p,p,p,p);return v;}
  static void tint(CompoundButton v){v.setButtonTintList(ColorStateList.valueOf(YiwooColors.EMERALD));}
  static void tint(SeekBar v){v.setProgressTintList(ColorStateList.valueOf(YiwooColors.EMERALD));v.setThumbTintList(ColorStateList.valueOf(YiwooColors.EMERALD));}

  /** Vector-like organic waves and leaves, clipped to the surface, not a raster backdrop. */
  static final class Organic extends Drawable {
    private final Paint paint=new Paint(3);private final boolean dark;private final float radius;
    Organic(Context c,boolean dark,int radius){this.dark=dark;this.radius=dp(c,radius);}
    @Override public void draw(Canvas canvas){
      Rect b=getBounds();float w=b.width(),h=b.height();int save=canvas.save();canvas.translate(b.left,b.top);
      Path clip=new Path();clip.addRoundRect(new RectF(0,0,w,h),radius,radius,Path.Direction.CW);canvas.clipPath(clip);
      paint.setShader(new LinearGradient(0,0,w,h,dark?new int[]{0xff123f35,0xff205c4d,0xff367761}:new int[]{0xfffffef9,0xfffafaf4,0xffedf3eb},null,Shader.TileMode.CLAMP));canvas.drawRect(0,0,w,h,paint);paint.setShader(null);
      for(int i=0;i<3;i++){Path wave=new Path();float y=h*(.83f+i*.055f);wave.moveTo(-w*.1f,y);wave.cubicTo(w*.25f,h*1.15f,w*.7f,h*.64f,w*1.1f,y);wave.lineTo(w*1.1f,h);wave.lineTo(-w*.1f,h);wave.close();paint.setColor(dark?0x0c4ed0aa:0x0e387c65);canvas.drawPath(wave,paint);}
      canvas.save();canvas.translate(w*.93f,h*.53f);canvas.rotate(-28);paint.setColor(dark?0x297fb08c:0x15669275);
      Path leaf=new Path();leaf.moveTo(0,0);leaf.cubicTo(-w*.075f,-h*.11f,-w*.055f,-h*.23f,0,-h*.31f);leaf.cubicTo(w*.035f,-h*.16f,w*.04f,-h*.07f,0,0);canvas.drawPath(leaf,paint);canvas.rotate(72);canvas.scale(.8f,.8f);canvas.drawPath(leaf,paint);canvas.restore();
      canvas.restoreToCount(save);
    }
    @Override public void setAlpha(int alpha){} @Override public void setColorFilter(ColorFilter filter){} @Override public int getOpacity(){return PixelFormat.OPAQUE;}
  }
}
