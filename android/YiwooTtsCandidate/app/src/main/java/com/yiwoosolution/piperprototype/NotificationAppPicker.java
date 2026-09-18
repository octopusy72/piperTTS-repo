package com.yiwoosolution.piperprototype;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.*;
import java.text.Collator;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;

/** Edits the existing package filter; cancel never persists draft selections. */
final class NotificationAppPicker {
  private static final class App {
    final String key, name;
    final Bitmap icon;
    App(String key,String name,Bitmap icon){this.key=key;this.name=name;this.icon=icon;}
  }
  static AlertDialog show(Activity activity,Runnable saved) {
    boolean allow=NotificationSettings.MODE_ALLOW.equals(NotificationSettings.mode(activity));
    Set<String> selected=new HashSet<>(NotificationSettings.packages(activity));
    LinearLayout panel=new LinearLayout(activity);panel.setOrientation(LinearLayout.VERTICAL);
    int pad=dp(activity,20);panel.setPadding(pad,0,pad,0);
    TextView help=new TextView(activity);help.setText(allow?"체크한 앱의 알림만 읽습니다.":"체크한 앱의 알림은 읽지 않습니다.");help.setTextSize(14);
    panel.addView(help);
    TextView count=new TextView(activity);count.setPadding(0,dp(activity,12),0,dp(activity,12));panel.addView(count);
    Runnable update=()->count.setText((allow?"읽을 앱 ":"제외할 앱 ")+selected.size()+"개 선택");update.run();
    ListView list=new ListView(activity);list.setTag("notification_app_list");list.setDividerHeight(0);list.setEmptyView(null);
    int height=Math.min(dp(activity,420),activity.getResources().getDisplayMetrics().heightPixels/2);
    panel.addView(list,new LinearLayout.LayoutParams(-1,height));
    TextView loading=new TextView(activity);loading.setText("앱 목록을 불러오는 중입니다.");panel.addView(loading);
    AlertDialog dialog=new AlertDialog.Builder(activity).setTitle(allow?"읽을 앱 선택":"제외할 앱 선택")
        .setView(panel).setNegativeButton("취소",null).setPositiveButton("저장",(d,w)->{
          NotificationSettings.setPackages(activity,selected);saved.run();
        }).create();
    ExecutorService worker=Executors.newSingleThreadExecutor();
    dialog.setOnDismissListener(d->worker.shutdownNow());dialog.show();dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(false);
    worker.execute(()->{
      List<App> apps=new ArrayList<>();PackageManager pm=activity.getPackageManager();
      Set<String> initial=new HashSet<>(selected);
      for(ApplicationInfo info:pm.getInstalledApplications(0)) {
        if(Thread.currentThread().isInterrupted())return;
        if(pm.getLaunchIntentForPackage(info.packageName)==null&&!initial.contains(info.packageName))continue;
        try {
          Drawable drawable=info.loadIcon(pm);int size=dp(activity,40);
          Bitmap bitmap=Bitmap.createBitmap(size,size,Bitmap.Config.ARGB_8888);
          drawable.setBounds(0,0,size,size);drawable.draw(new Canvas(bitmap));
          apps.add(new App(info.packageName,info.loadLabel(pm).toString(),bitmap));
        } catch(RuntimeException removed) { /* Package may disappear while enumerating. */ }
      }
      Collator collator=Collator.getInstance(Locale.getDefault());apps.sort((a,b)->collator.compare(a.name,b.name));
      activity.runOnUiThread(()->{
        if(!dialog.isShowing()||activity.isDestroyed())return;
        loading.setText(apps.isEmpty()?"선택 가능한 앱이 없습니다.":"");loading.setVisibility(apps.isEmpty()?View.VISIBLE:View.GONE);
        list.setAdapter(new BaseAdapter(){
          public int getCount(){return apps.size();}
          public Object getItem(int position){return apps.get(position);}
          public long getItemId(int position){return position;}
          public View getView(int position,View reusable,ViewGroup parent){
            LinearLayout row;
            if(reusable==null){
              row=new LinearLayout(activity);row.setGravity(Gravity.CENTER_VERTICAL);row.setPadding(0,dp(activity,8),0,dp(activity,8));row.setMinimumHeight(dp(activity,64));
              ImageView icon=new ImageView(activity);icon.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);row.addView(icon,new LinearLayout.LayoutParams(dp(activity,40),dp(activity,40)));
              CheckBox label=new CheckBox(activity);label.setTextSize(16);label.setPadding(dp(activity,12),0,0,0);label.setClickable(false);label.setFocusable(false);label.setButtonTintList(android.content.res.ColorStateList.valueOf(YiwooDesign.YiwooColors.EMERALD));row.addView(label,new LinearLayout.LayoutParams(0,-2,1));
            }else row=(LinearLayout)reusable;
            App app=apps.get(position);((ImageView)row.getChildAt(0)).setImageBitmap(app.icon);
            CheckBox label=(CheckBox)row.getChildAt(1);label.setText(app.name);label.setChecked(selected.contains(app.key));
            return row;
          }
        });
        list.setOnItemClickListener((parent,view,position,id)->{
          String key=apps.get(position).key;if(!selected.remove(key))selected.add(key);
          ((BaseAdapter)list.getAdapter()).notifyDataSetChanged();update.run();
        });
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(true);
      });
      worker.shutdown();
    });
    return dialog;
  }
  private static int dp(Activity a,int value){return Math.round(value*a.getResources().getDisplayMetrics().density);}
}
