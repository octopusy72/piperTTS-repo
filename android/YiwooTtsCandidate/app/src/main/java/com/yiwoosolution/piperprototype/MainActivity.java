package com.yiwoosolution.piperprototype;

import com.yiwoosolution.koreantts.R;
import android.util.Log;
import android.app.*; import android.content.*; import android.content.pm.*; import android.graphics.Color; import android.graphics.Typeface; import android.graphics.drawable.GradientDrawable; import android.media.AudioFormat; import android.media.AudioManager; import android.media.AudioTrack; import android.os.Bundle; import android.os.Handler; import android.os.Looper; import android.provider.Settings; import android.speech.tts.*; import android.text.InputType; import android.view.*; import android.view.inputmethod.EditorInfo; import android.widget.*; import java.util.*; import java.util.concurrent.ExecutorService; import java.util.concurrent.Executors;

/** Product information architecture: short home, settings hub, focused detail pages. */
public final class MainActivity extends Activity {
  private static java.lang.ref.WeakReference<MainActivity> voiceScreen = new java.lang.ref.WeakReference<>(null);
  static boolean voiceTestActive(){MainActivity screen=voiceScreen.get();return screen!=null&&!screen.isDestroyed()&&(screen.voicePlaying||screen.streamingSynthesis);}
  private static final int INK=YiwooDesign.YiwooColors.INK, MUTED=YiwooDesign.YiwooColors.MUTED, GREEN=YiwooDesign.YiwooColors.EMERALD, DEEP=YiwooDesign.YiwooColors.FOREST, PALE=YiwooDesign.YiwooColors.MINT, BG=YiwooDesign.YiwooColors.CREAM, GOLD=0xffbe8934;
  private TextToSpeech tts; private TextView status; private LinearLayout root, contentRoot; private int page=0;
  private boolean wide; private AlertDialog loadingDialog; private boolean waitingForRuntime, pendingReadyPlay; private double requestInferenceMs, requestProcessingMs; private long playbackRequestStart, firstWriteMs; private int firstUnderruns, emptyChunkStarts; private boolean playerModelRate; private long requestSpeechSamples; private EditText voiceEditor; private TextView voiceState, voiceRtf, voiceTotal; private ImageButton voicePlay; private final Handler voiceHandler=new Handler(Looper.getMainLooper()); private int playbackDurationMs; private boolean voicePlaying, voicePaused, streamingSynthesis, voiceEnglish; private AudioTrack audioTrack; private short[] playerPcm; private String playerText; private int playerPauseMs=-1; private float playerRate=1f, playerPitch=1f; private boolean playerEnglish; private long voiceGeneration; private final List<short[]> streamChunks=new ArrayList<>(); private final ExecutorService synthesisExecutor=Executors.newSingleThreadExecutor(); private PiperModelManager playerManager; private EnglishModelManager englishPlayerManager; private LongTextStreamingSynthesizer streamSynthesizer, englishPlayerStreamSynthesizer; private LongTextStreamingSynthesizer.StreamHandle activeStream;
  private int presetIndex; private boolean customVoiceInput; private TextView presetMeta, presetCounter;
  static final class Preset { final String category,title,text; Preset(String c,String t,String x){category=c;title=t;text=x;} }
  private Preset p(String c,String t,String x){return new Preset(c,t,x);}
  private List<Preset> koreanPresets(){return Arrays.asList(
    p("금융","계좌와 이체","계좌 잔액은 3억 2,500만 원입니다. 오늘 송금할 금액은 120만 원이고 이자율은 3.2퍼센트입니다. 결제 예정일은 2026년 9월 18일입니다."),
    p("숫자와 단위","측정값","공장까지 거리는 1,200km이고 차량 속도는 시속 80km입니다. 상자 무게는 21.5kg, 저장 용량은 256GB입니다."),
    p("수학","계산과 수식","삼백칠십이에서 백이십을 빼면 이백오십이입니다. 3/4에 1/4을 더하면 1이 되고, 비율은 1:2입니다."),
    p("택배","배송 안내","택배가 오늘 오후 3시 30분에 도착할 예정입니다. 운송장 번호 010-1234-5678을 확인하고 문 앞에 놓아 주세요."),
    p("공장","생산 현황","오늘 생산량은 1만 3천 개이며 불량률은 0.8퍼센트입니다. 2번 라인은 점검을 위해 오후 10시에 멈춥니다."),
    p("공항","탑승 안내","서울행 항공편 372번은 6번 게이트에서 오후 2시 15분에 출발합니다. 수하물은 20kg까지 무료입니다."),
    p("철도","열차 안내","GTX-A 열차가 5분 후 도착합니다. 승강장 2번에서 내린 뒤 3호선으로 환승해 주세요."),
    p("병원","예약 안내","진료 예약일은 9월 18일 오전 10시입니다. 체온은 36.5도이고 다음 검사는 2주 후에 진행합니다."),
    p("날씨","일기예보","오늘 서울의 기온은 21.5도이고 비 올 확률은 40퍼센트입니다. 오후에는 바람이 초속 5미터로 불겠습니다."),
    p("뉴스","주요 소식","정부는 새로운 교통 정책을 발표했습니다. 전국 평균 물가는 2.3퍼센트 상승했고 내일 국회에서 논의됩니다."),
    p("일정","날짜와 시간","회의는 2026년 9월 18일 금요일 오후 3시 30분에 시작합니다. 준비 자료는 오전 11시까지 보내 주세요."),
    p("쇼핑","주문과 결제","주문 금액은 128,000원이고 배송비는 무료입니다. 카드 결제는 완료되었으며 상품은 3일 이내 도착합니다."),
    p("연락처","주소와 전화번호","주소는 서울특별시 중구 세종대로 110입니다. 문의 사항은 02-1234-5678로 연락해 주세요."),
    p("IT","기기 안내","USB-C 케이블을 연결하고 CPU 사용량을 확인해 주세요. 현재 RTF는 0.72이며 프로그램 버전은 2.1.0입니다."),
    p("자동차","내비게이션","목적지까지 12km 남았습니다. 다음 교차로에서 우회전하고 제한 속도 시속 60km를 지켜 주세요."),
    p("안전","재난 안내","화재가 발생하면 엘리베이터를 사용하지 말고 비상 계단으로 이동하세요. 119에 신고하고 안내 방송을 따라 주세요."),
    p("교육","수업 안내","오늘 수업에서는 인공지능과 네트워크를 배웁니다. 숙제는 20쪽까지 읽고 다음 주 월요일에 제출하세요."),
    p("업무","회의 일정","이번 주 매출은 지난주보다 12퍼센트 증가했습니다. 팀 회의는 내일 오전 9시에 시작하고 안건은 3가지입니다."),
    p("일상","생활 대화","오늘은 맑습니다. 커피 한 잔을 마시고 산책을 다녀오겠습니다. 저녁에는 가족과 영화를 볼 예정입니다."),
    p("종합","스트레스 테스트","2026년 9월 18일, 서울역 3번 출구에서 만나요. 1,200km 여행과 3/4 비율, USB-C와 GTX-A 안내를 한 번에 확인합니다.")
  );}
  private List<Preset> englishPresets(){return Arrays.asList(
    p("Finance","Account transfer","Your account balance is three hundred twenty-five thousand dollars. The transfer is due on September 18th, 2026, with an interest rate of 3.2 percent."),
    p("Numbers & Measurements","Measurements","The factory is 1,200 kilometers away. The package weighs 21.5 kilograms and has a storage capacity of 256 gigabytes."),
    p("Mathematics","Calculations","Subtract one hundred twenty from three hundred seventy-two. Three-fourths plus one-fourth equals one, and the ratio is one to two."),
    p("Delivery","Shipping update","Your package will arrive at 3:30 PM today. Please check tracking number 010-1234-5678 and leave the box by the front door."),
    p("Manufacturing","Production report","Today we produced thirteen thousand units, with a defect rate of 0.8 percent. Line two will stop at 10 PM for inspection."),
    p("Airport","Boarding announcement","Flight 372 to Seoul departs from gate A12 at 2:15 PM. One checked bag up to 20 kilograms is included."),
    p("Train & Transit","Train announcement","The GTX-A train will arrive in five minutes. Exit at platform two and transfer to Line 3."),
    p("Hospital","Appointment reminder","Your appointment is on September 18th at 10 AM. Your temperature is 36.5 degrees Celsius, and the next test is in two weeks."),
    p("Weather","Forecast","The temperature in Seoul is 21.5 degrees today, with a 40 percent chance of rain. Winds will reach five meters per second."),
    p("News","Daily briefing","The government announced a new transportation policy. Consumer prices rose by 2.3 percent, and parliament will discuss it tomorrow."),
    p("Dates & Scheduling","Calendar","The meeting starts on Friday, September 18th, 2026, at 3:30 PM. Please send the preparation materials by 11 AM."),
    p("Shopping & Payment","Order confirmation","Your order total is 128 dollars, and shipping is free. Card payment is complete, and delivery is expected within three days."),
    p("Address & Phone","Contact details","The address is 110 Sejong-daero, Jung-gu, Seoul. Call 02-1234-5678 if you need assistance."),
    p("IT & Devices","Device instructions","Connect the USB-C cable and check CPU usage. The current RTF is 0.72, and the software version is 2.1.0."),
    p("Navigation","Driving directions","You have 12 kilometers remaining. Turn right at the next intersection and observe the 60 kilometer per hour speed limit."),
    p("Emergency & Safety","Emergency guidance","If there is a fire, do not use the elevator. Move to the emergency stairs, call 911, and follow the announcements."),
    p("Education","Class announcement","Today we will study artificial intelligence and networks. Read page 20 and submit your homework next Monday."),
    p("Business & Meetings","Business update","This week's sales increased by 12 percent from last week. The team meeting starts at 9 AM tomorrow with three agenda items."),
    p("Daily Conversation","Everyday plans","The weather is clear today. I will have a coffee and take a walk, then watch a movie with my family tonight."),
    p("Comprehensive","Stress test","Meet me at Exit 3 of Seoul Station on September 18th, 2026. We will review a 1,200 kilometer trip, USB-C, GTX-A, and version 2.1.")
  );}
  private List<Preset> activePresets(){return voiceEnglish?VoiceTestPresetCatalog.english():VoiceTestPresetCatalog.korean();}
  private void updatePresetHeader(){if(presetMeta==null||presetCounter==null)return;if(customVoiceInput){presetMeta.setText("사용자 입력");presetCounter.setText("직접 작성");return;}List<Preset> list=activePresets();Preset q=list.get(Math.floorMod(presetIndex,list.size()));presetMeta.setText(q.category+" · "+q.title);presetCounter.setText((Math.floorMod(presetIndex,list.size())+1)+" / "+list.size());}
  private void loadPreset(boolean stop){if(stop&&(voicePlaying||voicePaused||streamingSynthesis))stopVoice();customVoiceInput=false;List<Preset> list=activePresets();presetIndex=Math.floorMod(presetIndex,list.size());voiceEditor.setText(list.get(presetIndex).text);updatePresetHeader();}
  private void chooseVoicePreset(){
    List<Preset> presets=activePresets();List<String> categories=new ArrayList<>();for(Preset p:presets)if(!categories.contains(p.category))categories.add(p.category);
    new AlertDialog.Builder(this).setTitle("예문 분류 선택").setItems(categories.toArray(new String[0]),(dialog,which)->{
      List<Integer> indices=new ArrayList<>();List<String> titles=new ArrayList<>();for(int i=0;i<presets.size();i++)if(presets.get(i).category.equals(categories.get(which))){indices.add(i);titles.add(presets.get(i).title);}
      new AlertDialog.Builder(this).setTitle(categories.get(which)).setItems(titles.toArray(new String[0]),(d,index)->{presetIndex=indices.get(index);loadPreset(true);}).setNegativeButton("취소",null).show();
    }).setNegativeButton("취소",null).show();
  }
  @Override public void onCreate(Bundle b){super.onCreate(b); RuntimePreloadCoordinator.ensureRuntimePreloaded(this); voiceEnglish=getSharedPreferences("product_settings",MODE_PRIVATE).getBoolean("voice_language_english",false); playerManager=PiperModelManager.shared(this); englishPlayerManager=EnglishModelManager.shared(this); streamSynthesizer=new LongTextStreamingSynthesizer(playerManager); englishPlayerStreamSynthesizer=new LongTextStreamingSynthesizer(englishPlayerManager); tts=new TextToSpeech(this,r->{if(r==TextToSpeech.SUCCESS){tts.setLanguage(Locale.KOREA);}},getPackageName()); navigate(b==null?0:b.getInt("ui_page",0)); if(b!=null&&page==8&&voiceEditor!=null)voiceEditor.setText(b.getString("ui_draft",voiceEditor.getText().toString())); if((getApplicationInfo().flags&android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE)!=0){int screen=getIntent().getIntExtra("ui_screen",-1);if(screen>=0)navigate(screen);int orientation=getIntent().getIntExtra("ui_orientation",-1);if(orientation>=0)setRequestedOrientation(orientation);}if(EngineRetentionSettings.mode(this)!=EngineRetentionSettings.Mode.ON_DEMAND)root.post(()->prepareRuntime(false));String direct=getIntent().getStringExtra("voice_test_direct_probe"); if(direct!=null && (getApplicationInfo().flags&android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE)!=0){root.post(()->startDirectProbe(direct));} else {String probe=getIntent().getStringExtra("voice_test_probe");if(probe!=null){root.post(()->{setVoiceLanguage(false);speechDialog();voiceEditor.setText(probe);speakFromStart();});}}}
  private void startDirectProbe(final String text){
    Log.i("YiwooPiperKo","PROBE_DIRECT_START text="+text);
    synthesisExecutor.execute(()->{int callbacks=0,samples=0;try{for(String item:text.split("\\|\\|\\|",-1)){if(item.trim().isEmpty())continue;short[] pcm=playerManager.synthesize(item.trim());callbacks++;samples+=pcm.length;Log.i("YiwooPiperKo","PROBE_AUDIO_CALLBACK index="+(callbacks-1)+" samples="+pcm.length);}Log.i("YiwooPiperKo","PROBE_DIRECT_DONE callbacks="+callbacks+" samples="+samples);}catch(Throwable e){Log.e("YiwooPiperKo","PROBE_DIRECT_ERROR callbacks="+callbacks+" samples="+samples,e);}});
  }
  private int dp(int n){return(int)(n*getResources().getDisplayMetrics().density+.5f);} private TextView txt(String s,float z,int c,boolean bold){TextView v=new TextView(this);v.setText(s);v.setTextSize(z);v.setTextColor(c);v.setTypeface(Typeface.create(bold?"sans-serif-medium":"sans-serif",Typeface.NORMAL));v.setIncludeFontPadding(false);v.setLineSpacing(dp(2),1);return v;} private LinearLayout.LayoutParams lp(int w,int h,int l,int t,int r,int b){LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(w,h);p.setMargins(dp(l),dp(t),dp(r),dp(b));return p;} private GradientDrawable bg(int c,int rad){GradientDrawable d=new GradientDrawable();d.setColor(c);d.setCornerRadius(dp(rad));return d;}
  private void navigate(int target){switch(target){case 1:showSettings();break;case 2:detailVoice();break;case 3:detailNotifications();break;case 4:detailRules();break;case 5:detailSelected();break;case 6:detailEngine();break;case 7:detailInfo();break;case 8:speechDialog();break;case 9:detailTime();break;default:showMain();}}
  @Override protected void onNewIntent(Intent intent){super.onNewIntent(intent);setIntent(intent);if((getApplicationInfo().flags&android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE)!=0){String probe=intent.getStringExtra("voice_test_probe");if(probe!=null){stopVoice();playerPcm=null;setVoiceLanguage(intent.getBooleanExtra("voice_test_english",false));speechDialog();voiceEditor.setText(probe);speakFromStart();return;}int target=intent.getIntExtra("ui_screen",page);navigate(target);int orientation=intent.getIntExtra("ui_orientation",-1);if(orientation>=0)setRequestedOrientation(orientation);}}
  @Override protected void onSaveInstanceState(Bundle state){state.putInt("ui_page",page);state.putInt("voice_preset",presetIndex);state.putBoolean("voice_custom",customVoiceInput);if(voiceEditor!=null)state.putString("ui_draft",voiceEditor.getText().toString());super.onSaveInstanceState(state);}
  @Override protected void onRestoreInstanceState(Bundle state){super.onRestoreInstanceState(state);presetIndex=state.getInt("voice_preset",0);customVoiceInput=state.getBoolean("voice_custom",false);if(page==8)updatePresetHeader();}
  private void base(String title,boolean back){
    if(voicePlaying||voicePaused||streamingSynthesis)stopVoice();
    wide=YiwooLayoutPolicy.dashboard(getResources().getConfiguration().screenWidthDp,getResources().getConfiguration().orientation==android.content.res.Configuration.ORIENTATION_LANDSCAPE);
    getWindow().setStatusBarColor(wide?BG:DEEP);getWindow().setNavigationBarColor(BG);
    getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR|(wide?View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR:0));
    root=new LinearLayout(this);root.setOrientation(wide?LinearLayout.HORIZONTAL:LinearLayout.VERTICAL);root.setBackgroundColor(BG);
    if(wide){
      ScrollView navScroll=new ScrollView(this);navScroll.setFillViewport(true);navScroll.setBackgroundColor(PALE);
      LinearLayout rail=new LinearLayout(this);rail.setPadding(dp(12),dp(18),dp(12),dp(18));rail.setOrientation(LinearLayout.VERTICAL);
      TextView logo=txt("YIWOO TTS",19,DEEP,true);logo.setLetterSpacing(.06f);rail.addView(logo,lp(-1,-2,8,0,0,18));
      railItem(rail,R.drawable.ic_home,"홈",0);railItem(rail,R.drawable.ic_voice,"음성 테스트",8);railItem(rail,R.drawable.ic_notifications,"알림 읽기",3);railItem(rail,R.drawable.ic_voice,"음성 설정",2);railItem(rail,R.drawable.ic_rules,"읽기 규칙",4);railItem(rail,R.drawable.ic_text,"선택한 텍스트 읽기",5);railItem(rail,R.drawable.ic_memory,"성능 및 시스템",6);railItem(rail,R.drawable.ic_info,"앱 정보",7);
      railItem(rail,R.drawable.ic_time,"시간 알려주기",9);
      View timeNav=rail.getChildAt(rail.getChildCount()-1);rail.removeView(timeNav);rail.addView(timeNav,4);
      navScroll.addView(rail);root.addView(navScroll,new LinearLayout.LayoutParams(dp(YiwooLayoutPolicy.RAIL_DP),-1));
    }
    ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);scroll.setClipToPadding(false);contentRoot=new LinearLayout(this){@Override protected void onMeasure(int width,int height){super.onMeasure(MeasureSpec.makeMeasureSpec(Math.min(MeasureSpec.getSize(width),dp(YiwooLayoutPolicy.MAX_CONTENT_DP)),MeasureSpec.EXACTLY),height);}};contentRoot.setOrientation(LinearLayout.VERTICAL);contentRoot.setPadding(0,wide?dp(8):0,0,dp(24));scroll.addView(contentRoot,new FrameLayout.LayoutParams(-1,-2,Gravity.TOP|Gravity.CENTER_HORIZONTAL));
    root.addView(scroll,wide?new LinearLayout.LayoutParams(0,-1,1):new LinearLayout.LayoutParams(-1,0,1));
    if(back){
      LinearLayout bar=new LinearLayout(this);bar.setGravity(Gravity.CENTER_VERTICAL);
      Button backButton=action("‹");backButton.setTextSize(30);backButton.setContentDescription("홈으로");backButton.setOnClickListener(v->showMain());bar.addView(backButton,lp(dp(42),dp(54),0,0,4,0));
      bar.addView(txt(title,24,INK,true),new LinearLayout.LayoutParams(0,-2,1));contentRoot.addView(bar,lp(-1,-2,18,12,18,6));
    }
    if(android.os.Build.VERSION.SDK_INT>=35){root.setOnApplyWindowInsetsListener((v,insets)->{android.graphics.Insets bars=insets.getInsets(WindowInsets.Type.systemBars()|WindowInsets.Type.displayCutout());v.setPadding(bars.left,bars.top,bars.right,bars.bottom);return insets;});}
    setContentView(root);
  }
  private void railItem(LinearLayout rail,int icon,String label,int target){
    LinearLayout item=new LinearLayout(this);item.setGravity(Gravity.CENTER_VERTICAL);item.setPadding(dp(10),dp(9),dp(6),dp(9));item.setMinimumHeight(dp(44));item.setBackground(bg(page==target?GREEN:Color.TRANSPARENT,12));
    ImageView image=new ImageView(this);image.setImageResource(icon);image.setColorFilter(page==target?Color.WHITE:DEEP);item.addView(image,lp(dp(18),dp(18),0,0,9,0));item.addView(txt(label,12,page==target?Color.WHITE:INK,page==target),new LinearLayout.LayoutParams(0,-2,1));item.setOnClickListener(v->navigate(target));rail.addView(item,lp(-1,-2,0,1,0,1));
  }
  private LinearLayout surface(){return YiwooDesign.surface(this);}
  private void section(String value){contentRoot.addView(txt(value,14,INK,true),lp(-1,-2,20,20,20,10));}
  private LinearLayout card(){LinearLayout c=surface();contentRoot.addView(c,lp(-1,-2,18,0,18,12));return c;}
  private void intro(String value){contentRoot.addView(txt(value,14,MUTED,false),lp(-1,-2,22,0,22,20));}
  private void setStatus(String s){if(status!=null)status.setText(s);}
  private void setVoiceLanguage(boolean english){voiceEnglish=english;getSharedPreferences("product_settings",MODE_PRIVATE).edit().putBoolean("voice_language_english",english).apply();RuntimePreloadCoordinator.ensureRuntimePreloaded(this);}
  @Override protected void onResume(){super.onResume();RuntimePreloadCoordinator.ensureRuntimePreloaded(this);RuntimeRetentionService.refresh(this);if(page==9){TimeAnnouncementScheduler.ensureScheduled(this);detailTime();}}
  private String appVersion(){try{return getPackageManager().getPackageInfo(getPackageName(),0).versionName;}catch(Exception e){return "-";}}
  private String memoryLabel(){EngineRetentionSettings.Mode m=EngineRetentionSettings.mode(this);return m==EngineRetentionSettings.Mode.ALWAYS?"항상 유지":m==EngineRetentionSettings.Mode.TIMED?EngineRetentionSettings.idleMinutes(this)+"분 유지":"사용할 때 준비";}
  private LinearLayout hero(){
    LinearLayout c=surface();c.setBackground(new YiwooDesign.Organic(this,false,22));LinearLayout row=new LinearLayout(this);row.setGravity(Gravity.CENTER_VERTICAL);row.addView(YiwooDesign.icon(this,R.drawable.ic_voice,false),lp(dp(44),dp(44),0,0,12,0));LinearLayout copy=new LinearLayout(this);copy.setOrientation(LinearLayout.VERTICAL);copy.addView(txt("현재 음성",12,GREEN,true));copy.addView(txt(voiceEnglish?"영어":"한국어",21,INK,true),lp(-1,-2,0,5,0,0));row.addView(copy,new LinearLayout.LayoutParams(0,-2,1));c.addView(row);
    c.addView(txt("속도 "+String.format(Locale.KOREA,"%.2f배",SpeechPlaybackSettings.rate(this))+"\n메모리  "+memoryLabel(),12,MUTED,false),lp(-1,-2,0,16,0,0));c.setOnClickListener(v->detailVoice());c.setContentDescription("현재 음성, 음성 설정 열기");return c;
  }
  private LinearLayout primaryTest(){
    LinearLayout c=surface();c.setBackground(new YiwooDesign.Organic(this,true,22));c.setGravity(Gravity.CENTER_VERTICAL);c.setOrientation(LinearLayout.HORIZONTAL);c.setMinimumHeight(dp(110));if(wide)c.setPadding(dp(14),dp(14),dp(14),dp(14));
    c.addView(YiwooDesign.icon(this,R.drawable.ic_voice,true),lp(dp(wide?32:54),dp(wide?32:54),0,0,wide?10:14,0));LinearLayout copy=new LinearLayout(this);copy.setOrientation(LinearLayout.VERTICAL);copy.addView(txt("음성 테스트",wide?17:21,Color.WHITE,true));copy.addView(txt("다양한 예문으로\n음성을 들어보세요.",wide?11:13,0xffdcebe1,false),lp(-1,-2,0,6,0,0));c.addView(copy,new LinearLayout.LayoutParams(0,-2,1));c.setOnClickListener(v->speechDialog());return c;
  }
  private LinearLayout tile(int icon,String title,String desc,Runnable action){
    LinearLayout c=surface();c.setPadding(dp(15),dp(14),dp(15),dp(14));c.addView(YiwooDesign.icon(this,icon,false),lp(dp(36),dp(36),0,0,0,10));c.addView(txt(title,15,INK,true));if(!desc.isEmpty())c.addView(txt(desc,12,MUTED,false),lp(-1,-2,0,6,0,0));c.setOnClickListener(v->action.run());return c;
  }
  private void pair(LinearLayout parent,View left,View right){
    int available=getResources().getConfiguration().screenWidthDp-(wide?YiwooLayoutPolicy.RAIL_DP:0);float font=getResources().getConfiguration().fontScale;if(!YiwooLayoutPolicy.twoColumns(available,font)){parent.addView(left,lp(-1,-2,18,0,18,10));parent.addView(right,lp(-1,-2,18,0,18,10));return;}
    LinearLayout row=new LinearLayout(this);row.setBaselineAligned(false);LinearLayout.LayoutParams a=new LinearLayout.LayoutParams(0,-1,1);a.setMargins(0,0,dp(5),0);LinearLayout.LayoutParams b=new LinearLayout.LayoutParams(0,-1,1);b.setMargins(dp(5),0,0,0);row.addView(left,a);row.addView(right,b);parent.addView(row,lp(-1,-2,18,0,18,10));
  }
  private void detailColumns(int start,int split){
    if(!wide||!YiwooLayoutPolicy.twoColumns(getResources().getConfiguration().screenWidthDp-YiwooLayoutPolicy.RAIL_DP,getResources().getConfiguration().fontScale))return;
    LinearLayout left=new LinearLayout(this),right=new LinearLayout(this);left.setOrientation(LinearLayout.VERTICAL);right.setOrientation(LinearLayout.VERTICAL);
    int index=start;while(contentRoot.getChildCount()>start){View child=contentRoot.getChildAt(start);LinearLayout.LayoutParams p=(LinearLayout.LayoutParams)child.getLayoutParams();contentRoot.removeViewAt(start);p.leftMargin=0;p.rightMargin=0;(index++<split?left:right).addView(child,p);}pair(contentRoot,left,right);
  }
  private void showMain(){page=0;base("",false);
    LinearLayout brand=new LinearLayout(this);brand.setOrientation(LinearLayout.VERTICAL);brand.setPadding(dp(24),dp(wide?12:24),dp(24),dp(wide?12:22));brand.setBackground(new YiwooDesign.Organic(this,!wide,0));
    TextView logo=txt(wide?"좋은 하루예요!":"YIWOO TTS",wide?22:27,wide?DEEP:Color.WHITE,true);logo.setLetterSpacing(wide?0:.08f);brand.addView(logo);brand.addView(txt("오프라인으로, 더 자연스럽게",13,wide?MUTED:0xffd5e5db,false),lp(-1,-2,0,9,0,0));contentRoot.addView(brand,lp(-1,-2,0,0,0,16));
    if(wide)pair(contentRoot,hero(),primaryTest());else{contentRoot.addView(hero(),lp(-1,-2,18,0,18,12));contentRoot.addView(primaryTest(),lp(-1,-2,18,0,18,12));}
    if(wide){pair(contentRoot,tile(R.drawable.ic_notifications,"알림 읽기",NotificationSettings.enabled(this)&&hasAccess()?"자동 읽기 켜짐":"필요한 알림을 음성으로",()->detailNotifications()),tile(R.drawable.ic_voice,"음성 설정","나에게 맞는 음성",()->detailVoice()));pair(contentRoot,tile(R.drawable.ic_time,"시간 알려주기",TimeAnnouncementSettings.summary(this),()->detailTime()),tile(R.drawable.ic_rules,"읽기 규칙","나만의 발음과 표현",()->detailRules()));pair(contentRoot,tile(R.drawable.ic_text,"선택한 텍스트 읽기","선택한 글을 목소리로",()->detailSelected()),tile(R.drawable.ic_memory,"성능 및 시스템","음성 준비와 시스템 연결",()->detailEngine()));}
    else{
      LinearLayout notification=surface();notification.setOrientation(LinearLayout.HORIZONTAL);notification.setGravity(Gravity.CENTER_VERTICAL);notification.addView(YiwooDesign.icon(this,R.drawable.ic_notifications,false),lp(dp(42),dp(42),0,0,12,0));LinearLayout copy=new LinearLayout(this);copy.setOrientation(LinearLayout.VERTICAL);copy.addView(txt("알림 읽기",17,INK,true));copy.addView(txt(NotificationSettings.enabled(this)&&hasAccess()?"알림을 자동으로 읽고 있어요":"앱 알림을 음성으로 들어보세요",12,MUTED,false),lp(-1,-2,0,5,0,0));notification.addView(copy,new LinearLayout.LayoutParams(0,-2,1));notification.setOnClickListener(v->detailNotifications());contentRoot.addView(notification,lp(-1,-2,18,0,18,0));
      LinearLayout time=surface();time.setOrientation(LinearLayout.HORIZONTAL);time.setGravity(Gravity.CENTER_VERTICAL);time.addView(YiwooDesign.icon(this,R.drawable.ic_time,false),lp(dp(42),dp(42),0,0,12,0));LinearLayout timeCopy=new LinearLayout(this);timeCopy.setOrientation(LinearLayout.VERTICAL);timeCopy.addView(txt("시간 알려주기",17,INK,true));timeCopy.addView(txt(TimeAnnouncementSettings.summary(this),12,MUTED,false),lp(-1,-2,0,5,0,0));time.addView(timeCopy,new LinearLayout.LayoutParams(0,-2,1));time.setOnClickListener(v->detailTime());contentRoot.addView(time,lp(-1,-2,18,12,18,0));
      section("설정 바로가기");pair(contentRoot,tile(R.drawable.ic_voice,"음성 설정","",()->detailVoice()),tile(R.drawable.ic_rules,"읽기 규칙","",()->detailRules()));pair(contentRoot,tile(R.drawable.ic_text,"선택한 텍스트","",()->detailSelected()),tile(R.drawable.ic_memory,"성능 및 시스템","",()->detailEngine()));
    }
    LinearLayout footer=new LinearLayout(this);footer.setOrientation(LinearLayout.VERTICAL);footer.setGravity(Gravity.CENTER);footer.setPadding(dp(20),dp(22),dp(20),dp(24));footer.setBackground(new YiwooDesign.Organic(this,false,0));TextView message=txt("더 많은 이야기가\n당신의 하루를 부드럽게 채우도록",14,MUTED,false);message.setGravity(Gravity.CENTER);footer.addView(message);Button settings=action("모든 설정   ·   앱 정보 "+appVersion());settings.setGravity(Gravity.CENTER);settings.setTextSize(12);settings.setOnClickListener(v->showSettings());footer.addView(settings);Button info=action("YIWOO Solution  ·  앱 정보");info.setGravity(Gravity.CENTER);info.setTextSize(12);info.setOnClickListener(v->detailInfo());footer.addView(info);contentRoot.addView(footer,lp(-1,-2,0,12,0,0));
  }
  private Button action(String s){Button b=new Button(this);b.setText(s);b.setTextSize(15);b.setTextColor(GREEN);b.setGravity(Gravity.CENTER);b.setAllCaps(false);b.setMinHeight(dp(48));b.setPadding(dp(6),0,dp(6),0);b.setBackground(bg(Color.TRANSPARENT,14));b.setForeground(new android.graphics.drawable.RippleDrawable(android.content.res.ColorStateList.valueOf(0x33267460),null,bg(Color.WHITE,14)));return b;}
  private void showSettings(){page=1;base("설정",true);intro("나에게 편안한 읽기 경험을 만들어보세요.");pair(contentRoot,tile(R.drawable.ic_voice,"음성 설정","언어와 문장 간격",()->detailVoice()),tile(R.drawable.ic_notifications,"알림 읽기","앱별 읽기 정책",()->detailNotifications()));pair(contentRoot,tile(R.drawable.ic_rules,"읽기 규칙","발음과 표현",()->detailRules()),tile(R.drawable.ic_time,"시간 알려주기",TimeAnnouncementSettings.summary(this),()->detailTime()));pair(contentRoot,tile(R.drawable.ic_text,"선택한 텍스트","다른 앱에서 읽기",()->detailSelected()),tile(R.drawable.ic_memory,"성능 및 시스템","음성 준비와 시스템 연결",()->detailEngine()));contentRoot.addView(tile(R.drawable.ic_info,"앱 정보","버전과 라이선스",()->detailInfo()),lp(-1,-2,18,0,18,10));}

  private void requestStatusNotificationPermission(){
    if(android.os.Build.VERSION.SDK_INT>=33 && checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED)
      requestPermissions(new String[]{android.Manifest.permission.POST_NOTIFICATIONS},9031);
  }
  private void detailTime(){
    page=9;base("시간 알려주기",true);intro("원하는 간격으로 현재 시간을 음성으로 알려드립니다.");
    LinearLayout enabled=card();enabled.setBackground(new YiwooDesign.Organic(this,false,22));
    Switch on=new Switch(this);on.setText("시간 알려주기");on.setTextSize(19);on.setTextColor(INK);on.setMinHeight(dp(52));on.setChecked(TimeAnnouncementSettings.enabled(this));enabled.addView(on);
    enabled.addView(txt("알람이 울리거나 다른 음성을 읽고 있을 때는 이번 안내를 건너뜁니다.",13,MUTED,false),lp(-1,-2,0,8,0,0));
    on.setOnCheckedChangeListener((button,value)->{TimeAnnouncementSettings.setEnabled(this,value);if(value)requestStatusNotificationPermission();detailTime();});
    LinearLayout period=card();period.addView(txt("알려주는 주기",17,INK,true));
    RadioGroup modes=new RadioGroup(this);RadioButton hourly=new RadioButton(this),interval=new RadioButton(this);hourly.setId(View.generateViewId());interval.setId(View.generateViewId());hourly.setText("정각 알림 · 매시 정각");interval.setText("간격 지정 · 시간과 분으로 설정");hourly.setMinHeight(dp(48));interval.setMinHeight(dp(48));modes.addView(hourly);modes.addView(interval);period.addView(modes);modes.check(TimeAnnouncementSettings.hourly(this)?hourly.getId():interval.getId());
    modes.setOnCheckedChangeListener((group,id)->{TimeAnnouncementSettings.setPeriod(this,id==hourly.getId(),TimeAnnouncementSettings.minutes(this));detailTime();});
    if(!TimeAnnouncementSettings.hourly(this)){
      LinearLayout fields=new LinearLayout(this);fields.setGravity(Gravity.CENTER_VERTICAL);
      EditText hours=new EditText(this),minutes=new EditText(this);hours.setInputType(InputType.TYPE_CLASS_NUMBER);minutes.setInputType(InputType.TYPE_CLASS_NUMBER);hours.setSingleLine();minutes.setSingleLine();hours.setContentDescription("안내 간격 시간");minutes.setContentDescription("안내 간격 분");hours.setText(String.valueOf(TimeAnnouncementSettings.minutes(this)/60));minutes.setText(String.valueOf(TimeAnnouncementSettings.minutes(this)%60));
      fields.addView(hours,new LinearLayout.LayoutParams(0,dp(52),1));fields.addView(txt("시간",14,INK,false),lp(-2,-2,5,0,12,0));fields.addView(minutes,new LinearLayout.LayoutParams(0,dp(52),1));fields.addView(txt("분",14,INK,false),lp(-2,-2,5,0,0,0));period.addView(fields,lp(-1,-2,0,8,0,0));
      period.addView(txt("1분부터 24시간까지 지정할 수 있습니다.",12,MUTED,false));Button save=action("간격 저장");period.addView(save);save.setOnClickListener(v->{try{int h=Integer.parseInt(hours.getText().toString().trim()),m=Integer.parseInt(minutes.getText().toString().trim());if(h<0||h>24||m<0||m>59||h*60+m<1||h*60+m>1440)throw new IllegalArgumentException();TimeAnnouncementSettings.setPeriod(this,false,h*60+m);Toast.makeText(this,"안내 간격을 저장했습니다.",Toast.LENGTH_SHORT).show();detailTime();}catch(IllegalArgumentException e){Toast.makeText(this,"시간은 0~24, 분은 0~59로 입력해 주세요. 전체 간격은 1분~24시간입니다.",Toast.LENGTH_LONG).show();}});
    }
    LinearLayout info=card();info.addView(txt("이렇게 알려드려요",17,INK,true));info.addView(txt("정각 한 시입니다.\n한 시 삼십 분입니다.",16,GREEN,false),lp(-1,-2,0,10,0,12));
    if(TimeAnnouncementSettings.enabled(this)&&TimeAnnouncementSettings.next(this)>0)info.addView(txt("다음 안내 · "+new java.text.SimpleDateFormat("M월 d일 HH:mm",Locale.KOREA).format(new Date(TimeAnnouncementSettings.next(this))),13,INK,false),lp(-1,-2,0,0,0,10));
    info.addView(txt("무음·진동·방해 금지 모드와 통화 중에는 읽지 않습니다. 절전 상태에서는 안내가 늦어지거나 건너뛸 수 있습니다.",12,MUTED,false));
    if(!TimeAnnouncementScheduler.exactAllowed(this)){Button permission=action("정확한 시간 알림 허용");info.addView(permission);permission.setOnClickListener(v->{try{startActivity(new Intent(android.provider.Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,android.net.Uri.parse("package:"+getPackageName())));}catch(ActivityNotFoundException ignored){Toast.makeText(this,"기기 설정에서 알람 및 리마인더 권한을 확인해 주세요.",Toast.LENGTH_LONG).show();}});info.addView(txt("정확한 시각에 안내하려면 알람 및 리마인더 권한을 허용해 주세요.",12,MUTED,false));}
    detailColumns(2,3);
  }
  private LinearLayout playbackControl(String title, boolean pitch) {
    LinearLayout c=surface(); c.addView(txt(title,16,INK,true));
    float current=pitch?SpeechPlaybackSettings.pitch(this):SpeechPlaybackSettings.rate(this);
    TextView value=txt(String.format(Locale.KOREA,"%.2f배",current),13,MUTED,false);
    SeekBar bar=new SeekBar(this); bar.setMax(30);
    bar.setProgress(Math.round((Math.max(.5f,Math.min(2f,current))-.5f)/.05f));
    bar.setEnabled(!SpeechPlaybackSettings.followsSystem(this)); YiwooDesign.tint(bar);
    c.addView(bar,lp(-1,dp(44),0,8,0,0));c.addView(value);
    bar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){
      public void onProgressChanged(SeekBar b,int p,boolean user){
        if(user){float v=.5f+p*.05f;SpeechPlaybackSettings.set(MainActivity.this,pitch,v);
          value.setText(String.format(Locale.KOREA,"%.2f배",v));}
      }
      public void onStartTrackingTouch(SeekBar b){}
      public void onStopTrackingTouch(SeekBar b){}
    });return c;
  }
  private void detailVoice(){page=2;base("음성 설정",true);intro("내 일상에 맞는 목소리로.");
    contentRoot.addView(hero(),lp(-1,-2,18,0,18,14));LinearLayout segmented=surface();segmented.setPadding(dp(5),dp(5),dp(5),dp(5));segmented.setOrientation(LinearLayout.HORIZONTAL);
    for(int i=0;i<2;i++){final boolean english=i==1;Button b=action(english?"영어":"한국어");b.setBackground(bg(voiceEnglish==english?GREEN:Color.TRANSPARENT,16));b.setTextColor(voiceEnglish==english?Color.WHITE:MUTED);b.setSelected(voiceEnglish==english);b.setOnClickListener(v->{setVoiceLanguage(english);detailVoice();});segmented.addView(b,new LinearLayout.LayoutParams(0,dp(48),1));}contentRoot.addView(segmented,lp(-1,-2,18,0,18,18));
    LinearLayout controls=surface();Switch system=new Switch(this);system.setText("Android 시스템 설정 따르기");system.setMinHeight(dp(48));system.setChecked(SpeechPlaybackSettings.followsSystem(this));controls.addView(system);controls.addView(txt("끄면 이 앱의 속도와 음높이를 직접 조절합니다. 다른 앱의 TTS 요청은 해당 앱 설정을 따릅니다.",12,MUTED,false));system.setOnCheckedChangeListener((b,v)->{SpeechPlaybackSettings.followSystem(this,v);detailVoice();});contentRoot.addView(controls,lp(-1,-2,18,0,18,12));
    contentRoot.addView(playbackControl("말하기 속도",false),lp(-1,-2,18,0,18,12));contentRoot.addView(playbackControl("음높이",true),lp(-1,-2,18,0,18,12));
    LinearLayout pause=card();pause.addView(txt("문장 사이 간격",16,INK,true));TextView value=txt(SentenceBoundaryPausePolicy.pauseMs(this)+" ms",13,MUTED,false);SeekBar ps=new SeekBar(this);YiwooDesign.tint(ps);ps.setMax(SentenceBoundaryPausePolicy.MAX_MS/SentenceBoundaryPausePolicy.STEP_MS);ps.setProgress(SentenceBoundaryPausePolicy.pauseMs(this)/SentenceBoundaryPausePolicy.STEP_MS);pause.addView(ps,lp(-1,dp(44),0,8,0,0));pause.addView(value);ps.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){public void onProgressChanged(SeekBar b,int p,boolean user){value.setText(p*SentenceBoundaryPausePolicy.STEP_MS+" ms");if(user)SentenceBoundaryPausePolicy.setPauseMs(MainActivity.this,p*SentenceBoundaryPausePolicy.STEP_MS);}public void onStartTrackingTouch(SeekBar b){}public void onStopTrackingTouch(SeekBar b){}});
    Button sample=action("샘플 음성 들어보기");sample.setBackground(bg(DEEP,18));sample.setTextColor(Color.WHITE);sample.setOnClickListener(v->speechDialog());contentRoot.addView(sample,lp(-1,dp(54),18,6,18,0));detailColumns(2,5);
  }
  private void detailNotifications(){page=3;base("알림 읽기",true);intro("앱 알림을 자동으로 읽어드립니다.");
    LinearLayout enable=card();enable.setBackground(new YiwooDesign.Organic(this,false,22));enable.addView(YiwooDesign.icon(this,R.drawable.ic_notifications,false),lp(dp(46),dp(46),0,0,0,14));Switch on=new Switch(this);on.setText("알림 자동 읽기");on.setTextColor(INK);on.setTextSize(19);on.setMinHeight(dp(48));on.setChecked(NotificationSettings.enabled(this)&&hasAccess());enable.addView(on);
    on.setOnCheckedChangeListener((b,v)->{if(v&&!hasAccess()){showTextDialog("알림 접근 권한 필요","알림 접근 권한을 허용해 주세요.",this::openNotificationSettings);b.setChecked(false);}else{NotificationSettings.setEnabled(this,v);if(v)requestStatusNotificationPermission();}});
    Button access=action("알림 접근 설정 열기");enable.addView(access);access.setOnClickListener(v->openNotificationSettings());
    LinearLayout reading=surface();reading.addView(txt("읽는 내용",16,INK,true));Switch full=new Switch(this);full.setText("알림 내용을 전체 읽기");full.setChecked(NotificationSettings.fullContent(this));full.setMinHeight(dp(54));reading.addView(full);full.setOnCheckedChangeListener((b,v)->NotificationSettings.setFullContent(this,v));reading.addView(txt("꺼두면 간결한 알림으로 전달합니다.",12,MUTED,false));
    LinearLayout exception=surface();exception.addView(txt("예외 상황",16,INK,true));Switch respect=new Switch(this);respect.setText("무음·진동·방해 금지\n모드에서는 읽지 않기");respect.setMinHeight(dp(64));respect.setChecked(NotificationSettings.respectRinger(this));exception.addView(respect);respect.setOnCheckedChangeListener((b,v)->NotificationSettings.setRespectRinger(this,v));
    {contentRoot.addView(reading,lp(-1,-2,18,0,18,12));contentRoot.addView(exception,lp(-1,-2,18,0,18,12));}
    LinearLayout apps=card();apps.addView(txt("읽을 앱",16,INK,true));RadioGroup mode=new RadioGroup(this);RadioButton all=new RadioButton(this);all.setId(View.generateViewId());all.setText("모든 앱 · 제외 앱은 읽지 않음");RadioButton only=new RadioButton(this);only.setId(View.generateViewId());only.setText("선택한 앱만");mode.addView(all);mode.addView(only);(NotificationSettings.MODE_ALLOW.equals(NotificationSettings.mode(this))?only:all).setChecked(true);apps.addView(mode);
    TextView explanation=txt("",13,MUTED,false);apps.addView(explanation);Button choose=action("");apps.addView(choose);
    Runnable updateApps=()->{boolean allowed=NotificationSettings.MODE_ALLOW.equals(NotificationSettings.mode(this));int count=NotificationSettings.packages(this).size();choose.setText((allowed?"읽을 앱 선택":"제외할 앱 선택")+" · "+count+"개");explanation.setText(allowed?"체크한 앱의 알림만 읽습니다. 선택하지 않으면 읽지 않습니다.":"아래에서 체크한 앱은 읽지 않고, 나머지 앱의 알림을 읽습니다.");};
    updateApps.run();choose.setOnClickListener(v->NotificationAppPicker.show(this,updateApps));mode.setOnCheckedChangeListener((g,id)->{NotificationSettings.setMode(this,id==only.getId()?NotificationSettings.MODE_ALLOW:NotificationSettings.MODE_EXCLUDE);updateApps.run();});detailColumns(2,3);
  }
  private void detailRules(){
    page=4;base("읽기 규칙",true);section("사용자 읽기 규칙");
    final ReadingRuleRepository repository=new ReadingRuleRepository(this);
    LinearLayout c=card();
    Switch enabled=new Switch(this); enabled.setText("사용자 읽기 규칙 사용"); enabled.setChecked(repository.enabled()); c.addView(enabled);
    enabled.setOnCheckedChangeListener((button, checked)->repository.setEnabled(checked));
    c.addView(txt("특정 단어, 기호와 숫자의 읽는 방법을 직접 지정합니다.",14,MUTED,false),lp(-1,-2,0,4,0,8));
    Button add=action("규칙 추가"); c.addView(add); add.setOnClickListener(v->showRuleEditor(repository));
    LinearLayout transfer=new LinearLayout(this); transfer.setGravity(Gravity.RIGHT);
    Button export=action("내보내기"); Button importRules=action("가져오기"); transfer.addView(export,lp(dp(96),dp(44),0,0,4,0)); transfer.addView(importRules,lp(dp(96),dp(44),4,0,0,0)); c.addView(transfer,lp(-1,-2,0,4,0,0));
    export.setOnClickListener(v->{ClipboardManager clipboard=(ClipboardManager)getSystemService(Context.CLIPBOARD_SERVICE);clipboard.setPrimaryClip(ClipData.newPlainText("YIWOO 읽기 규칙",repository.exportJson()));Toast.makeText(this,"규칙을 클립보드에 복사했습니다.",Toast.LENGTH_SHORT).show();});
    importRules.setOnClickListener(v->{ClipboardManager clipboard=(ClipboardManager)getSystemService(Context.CLIPBOARD_SERVICE);if(clipboard.hasPrimaryClip()){String json=clipboard.getPrimaryClip().getItemAt(0).coerceToText(this).toString();int added=repository.importJson(json);Toast.makeText(this,added+"개 규칙을 가져왔습니다.",Toast.LENGTH_SHORT).show();detailRules();}else Toast.makeText(this,"가져올 규칙이 없습니다.",Toast.LENGTH_SHORT).show();});
    List<ReadingRule> rules=repository.list();
    if(rules.isEmpty()) c.addView(txt("등록된 규칙이 없습니다.",14,MUTED,false),lp(-1,-2,0,12,0,0));
    for(ReadingRule rule:rules){
      LinearLayout row=new LinearLayout(this); row.setGravity(Gravity.CENTER_VERTICAL);
      String label=rule.type==ReadingRule.Type.CONTEXT_PREFIX_NUMBER?rule.prefix+" 숫자":rule.source;
      TextView name=txt(label+" → "+(rule.reading.isEmpty()?"숫자 단위 읽기":rule.reading),15,INK,false); row.addView(name,new LinearLayout.LayoutParams(0,-2,1));
      Switch active=new Switch(this); active.setChecked(rule.enabled); active.setContentDescription("규칙 사용"); active.setOnCheckedChangeListener((button,checked)->{repository.update(new ReadingRule(rule.id,checked,rule.type,rule.language,rule.source,rule.reading,rule.prefix,rule.digitCount,rule.caseSensitive)); detailRules();}); row.addView(active,lp(dp(58),dp(44),2,0,0,0));
      Button remove=action("삭제"); remove.setTextSize(13); row.addView(remove,lp(dp(64),dp(44),4,0,0,0)); remove.setOnClickListener(v->{repository.delete(rule.id);detailRules();});
      row.setOnClickListener(v->showRuleEditor(repository,rule)); c.addView(row,lp(-1,-2,0,8,0,0));
    }
  }
  private void showRuleEditor(ReadingRuleRepository repository){
    showRuleEditor(repository,null);
  }
  private void showRuleEditor(ReadingRuleRepository repository, ReadingRule existing){
    LinearLayout box=new LinearLayout(this); box.setOrientation(LinearLayout.VERTICAL); box.setPadding(dp(24),dp(8),dp(24),0);
    Spinner type=new Spinner(this); type.setAdapter(new ArrayAdapter<String>(this,android.R.layout.simple_spinner_dropdown_item,new String[]{"문구를 원하는 발음으로 읽기","문구를 철자대로 읽기","특정 문맥의 숫자를 한 자리씩 읽기"})); box.addView(type);
    EditText source=new EditText(this); source.setHint("원문 또는 접두어"); source.setText(existing==null?"":(existing.type==ReadingRule.Type.CONTEXT_PREFIX_NUMBER?existing.prefix:existing.source)); box.addView(source);
    EditText reading=new EditText(this); reading.setHint("읽을 발음"); reading.setText(existing==null?"":existing.reading); box.addView(reading);
    EditText count=new EditText(this); count.setHint("숫자 자릿수 (선택)"); count.setInputType(android.text.InputType.TYPE_CLASS_NUMBER); count.setText(existing==null||existing.digitCount==null?"":String.valueOf(existing.digitCount)); box.addView(count);
    Spinner language=new Spinner(this); language.setAdapter(new ArrayAdapter<String>(this,android.R.layout.simple_spinner_dropdown_item,new String[]{"모든 언어","한국어","영어 (미국)"})); language.setSelection(existing==null?0:existing.language.ordinal()); box.addView(language);
    Switch caseSensitive=new Switch(this); caseSensitive.setText("대문자와 소문자를 구분해서 적용"); caseSensitive.setChecked(existing==null||existing.caseSensitive); box.addView(caseSensitive);
    if(existing!=null) type.setSelection(existing.type==ReadingRule.Type.EXACT_TEXT_CUSTOM?0:existing.type==ReadingRule.Type.EXACT_TEXT_SPELL_OUT?1:2);
    AlertDialog dialog=new AlertDialog.Builder(this).setTitle(existing==null?"읽기 규칙 추가":"읽기 규칙 편집").setView(box).setNegativeButton("취소",null).setPositiveButton("저장",null).create();
    dialog.setOnShowListener(x->dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{
      int selected=type.getSelectedItemPosition(); ReadingRule.Type kind=selected==0?ReadingRule.Type.EXACT_TEXT_CUSTOM:selected==1?ReadingRule.Type.EXACT_TEXT_SPELL_OUT:ReadingRule.Type.CONTEXT_PREFIX_NUMBER;
      String src=source.getText().toString().trim(); String read=reading.getText().toString().trim(); Integer digits=count.getText().length()==0?null:Integer.valueOf(count.getText().toString());
      ReadingRule.Language scope=ReadingRule.Language.values()[language.getSelectedItemPosition()];
      ReadingRule rule=new ReadingRule(existing==null?"":existing.id,existing==null||existing.enabled,kind,scope,kind==ReadingRule.Type.CONTEXT_PREFIX_NUMBER?"":src,read,kind==ReadingRule.Type.CONTEXT_PREFIX_NUMBER?src:"",digits,caseSensitive.isChecked());
      boolean saved=existing==null?repository.add(rule):repository.update(rule);
      if(saved){dialog.dismiss();detailRules();}else Toast.makeText(this,"규칙이 중복되었거나 형식이 올바르지 않습니다.",Toast.LENGTH_SHORT).show();
    })); dialog.show();
  }
  private void detailSelected(){page=5;base("선택한 텍스트 읽기",true);section("선택한 텍스트 읽기");LinearLayout c=card();Switch enabled=new Switch(this);enabled.setText("선택한 텍스트 읽기");enabled.setChecked(getSharedPreferences("product_settings",MODE_PRIVATE).getBoolean("process_text_enabled",false));c.addView(enabled);enabled.setOnCheckedChangeListener((b,v)->getSharedPreferences("product_settings",MODE_PRIVATE).edit().putBoolean("process_text_enabled",v).apply());c.addView(txt("다른 앱에서 선택한 텍스트를 음성으로 읽습니다.",16,INK,false),lp(-1,-2,0,8,0,0));}
  private void detailEngine(){
    page=6;base("성능 및 시스템",true);intro("음성 준비 방식과 Android 시스템 연동을 설정합니다.");
    section("음성 메모리 유지");
    EngineRetentionSettings.Mode current=EngineRetentionSettings.mode(this);
    String[] titles={"사용할 때 준비","일정 시간 유지","항상 유지"};
    String[] descriptions={"필요할 때 음성을 준비해 메모리를 아낍니다.","마지막 사용 후 잠시 준비 상태를 유지합니다.","다음 발화를 위해 음성을 준비해 둡니다."};
    EngineRetentionSettings.Mode[] options=EngineRetentionSettings.Mode.values();
    for(int i=0;i<options.length;i++){final EngineRetentionSettings.Mode option=options[i];LinearLayout c=card();boolean selected=current==option;c.setBackground(bg(selected?PALE:YiwooDesign.YiwooColors.SURFACE,22));LinearLayout line=new LinearLayout(this);line.setGravity(Gravity.CENTER_VERTICAL);TextView marker=txt(selected?"●":"○",22,GREEN,false);line.addView(marker,lp(dp(32),-2,0,0,8,0));LinearLayout copy=new LinearLayout(this);copy.setOrientation(LinearLayout.VERTICAL);copy.addView(txt(titles[i],17,INK,true));copy.addView(txt(descriptions[i],13,MUTED,false),lp(-1,-2,0,7,0,0));line.addView(copy,new LinearLayout.LayoutParams(0,-2,1));c.addView(line);c.setContentDescription(titles[i]+(selected?", 선택됨":""));c.setSelected(selected);c.setOnClickListener(v->{EngineRetentionSettings.setMode(this,option);detailEngine();});}
    if(current==EngineRetentionSettings.Mode.TIMED){LinearLayout timer=card();TextView minutes=txt("유휴 시간 "+EngineRetentionSettings.idleMinutes(this)+"분",15,INK,true);timer.addView(minutes);SeekBar idle=new SeekBar(this);YiwooDesign.tint(idle);idle.setMax(59);idle.setProgress(EngineRetentionSettings.idleMinutes(this)-1);timer.addView(idle,lp(-1,dp(48),0,8,0,0));idle.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){public void onProgressChanged(SeekBar b,int p,boolean user){minutes.setText("유휴 시간 "+(p+1)+"분");if(user)EngineRetentionSettings.setIdleMinutes(MainActivity.this,p+1);}public void onStartTrackingTouch(SeekBar b){}public void onStopTrackingTouch(SeekBar b){}});}
    section("Android 연동");LinearLayout system=card();Button settings=action("Android 음성 합성 설정");settings.setOnClickListener(v->openTtsSettings());system.addView(settings);Button info=action("앱 정보 · "+appVersion());info.setOnClickListener(v->detailInfo());system.addView(info);detailColumns(2,current==EngineRetentionSettings.Mode.TIMED?7:6);
  }
  private void detailInfo(){page=7;base("정보",true);section("앱 정보");LinearLayout about=card();about.addView(txt("YIWOO TTS",20,INK,true));about.addView(txt("버전 "+appVersion(),14,MUTED,false),lp(-1,-2,0,6,0,0));about.addView(txt("개발",14,INK,true),lp(-1,-2,0,18,0,0));about.addView(txt("Yiwoo Solution",14,MUTED,false),lp(-1,-2,0,3,0,0));about.addView(txt("지원 언어",14,INK,true),lp(-1,-2,0,16,0,0));about.addView(txt("한국어, 영어",14,MUTED,false),lp(-1,-2,0,3,0,0));section("기능");LinearLayout c=card();TextView legal=action("오픈소스 라이선스");c.addView(legal);legal.setOnClickListener(v->startActivity(new Intent(this,LegalNoticesActivity.class)));TextView t=action("Android 음성 합성 설정");c.addView(t);t.setOnClickListener(v->openTtsSettings());TextView d=action("진단 정보");c.addView(d);d.setOnClickListener(v->showTextDialog("진단 정보",diagnostics()));}
  private String formatTime(int ms){int sec=Math.max(0,ms/1000);return String.format(Locale.US,"%02d:%02d",sec/60,sec%60);}
  private void prepareRuntime(boolean playAfter){
    if(playAfter)pendingReadyPlay=true;if(waitingForRuntime)return;
    java.util.concurrent.CompletableFuture<Void> ready=RuntimePreloadCoordinator.prepare(this,voiceEnglish);
    if(ready.isDone()&&!ready.isCompletedExceptionally()){if(playAfter)speakFromStart();return;}
    waitingForRuntime=true;
    if(voiceState!=null)voiceState.setText("시스템 로딩 중");
    loadingDialog=new AlertDialog.Builder(this).setTitle("시스템 로딩 중").setMessage("음성 모듈을 준비하고 있습니다. 준비가 끝나면 알려드립니다.").setPositiveButton("화면에서 기다리기",null).create();
    loadingDialog.show();
    ready.whenComplete((ignored,error)->runOnUiThread(()->{
      waitingForRuntime=false;if(isDestroyed()||isFinishing())return;
      if(loadingDialog!=null){loadingDialog.dismiss();loadingDialog=null;}
      if(error!=null){if(voiceState!=null)voiceState.setText("음성 준비 실패");showTextDialog("음성 준비 실패","다시 재생을 눌러 준비를 재시도할 수 있습니다.");return;}
      if(voiceState!=null)voiceState.setText("준비됨");
      boolean play=pendingReadyPlay;pendingReadyPlay=false;if(play&&page==8)speakFromStart();
    }));
  }
  private void updateVoiceRtf(){if(requestSpeechSamples>0){double rtf=(requestInferenceMs+requestProcessingMs)/(requestSpeechSamples*1000.0/playerSampleRate());voiceRtf.setText(String.format(Locale.US,"합성 RTF %.2f",rtf));}else voiceRtf.setText("RTF --");}
  private final Runnable completionTick=new Runnable(){@Override public void run(){if(!voicePlaying||audioTrack==null)return;int total=playerPcm==null?0:playerPcm.length;if(total>0&&audioTrack.getPlaybackHeadPosition()>=total){finishPlayback();return;}voiceHandler.postDelayed(this,100);}};
    private int playerSampleRate(){return voiceEnglish?englishPlayerManager.sampleRate():VoiceRegistry.ACTIVE.sampleRate;}

    private void speakFromStart(){java.util.concurrent.CompletableFuture<Void> ready=RuntimePreloadCoordinator.prepare(this,voiceEnglish);if(!ready.isDone()||ready.isCompletedExceptionally()){prepareRuntime(true);return;}String text=voiceEditor.getText().toString().trim();if(text.isEmpty()){voiceState.setText("문장을 입력하세요");return;}stopVoice();final boolean debug=(getApplicationInfo().flags&android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE)!=0;final boolean modelRate=!debug||getIntent().getBooleanExtra("voice_test_model_rate",true);final float rate=debug&&getIntent().hasExtra("voice_test_rate_percent")?SpeechPcmProcessor.bounded(getIntent().getIntExtra("voice_test_rate_percent",100)/100f):SpeechPlaybackSettings.rate(this),pitch=debug&&getIntent().hasExtra("voice_test_pitch_percent")?SpeechPcmProcessor.bounded(getIntent().getIntExtra("voice_test_pitch_percent",100)/100f):SpeechPlaybackSettings.pitch(this);final boolean english=voiceEnglish;if(playerPcm!=null&&text.equals(playerText)&&playerPauseMs==SentenceBoundaryPausePolicy.pauseMs(this)&&playerEnglish==english&&playerRate==rate&&playerPitch==pitch&&playerModelRate==modelRate){startPlayback();return;}final long generation=++voiceGeneration;final long requestStarted=android.os.SystemClock.elapsedRealtime();Log.i("YiwooUi","VOICE_TEST_REQUEST rate="+rate+" pitch="+pitch+" modelRate="+modelRate);playbackRequestStart=requestStarted;firstWriteMs=0;firstUnderruns=0;emptyChunkStarts=0;requestProcessingMs=0;requestInferenceMs=0;requestSpeechSamples=0;streamChunks.clear();playerPcm=null;playerText=null;playerPauseMs=-1;streamingSynthesis=true;voicePaused=false;voiceState.setText("합성 중...");voiceRtf.setText("측정 중");voiceTotal.setText("--");voicePlay.setImageResource(com.yiwoosolution.koreantts.R.drawable.ic_pause);startStreamingTrack();voicePlaying=true;final LongTextStreamingSynthesizer.StreamHandle stream=(voiceEnglish?englishPlayerStreamSynthesizer:streamSynthesizer).start(text,modelRate?rate:1f);activeStream=stream;synthesisExecutor.execute(()->{try{LongTextStreamingSynthesizer.Chunk chunk;while((chunk=stream.take())!=null){if(generation!=voiceGeneration||audioTrack==null)throw new IllegalStateException("STALE_VOICE_REQUEST");long processingStart=System.nanoTime();short[] pcm=SpeechPcmProcessor.process(chunk.pcm,playerSampleRate(),modelRate?1f:rate,pitch);requestProcessingMs+=(System.nanoTime()-processingStart)/1000000.0;if(chunk.index==0)Log.i("YiwooUi","FIRST_AUDIO_MS="+(android.os.SystemClock.elapsedRealtime()-requestStarted));requestInferenceMs+=chunk.inferenceMs;requestSpeechSamples+=pcm.length;if(chunk.index==0){firstWriteMs=android.os.SystemClock.elapsedRealtime();firstUnderruns=audioTrack.getUnderrunCount();}else{long queued=0;synchronized(streamChunks){for(short[] p:streamChunks)queued+=p.length;}if(audioTrack.getPlaybackHeadPosition()>=queued)emptyChunkStarts++;}int result=audioTrack.write(pcm,0,pcm.length);if(result<0)throw new IllegalStateException("AUDIO_WRITE_FAILED:"+result);android.util.Log.i("YiwooPiperKo","PLAYBACK_WRITE type=SPEECH index="+chunk.index+" inputFrames="+chunk.pcm.length+" frames="+pcm.length);synchronized(streamChunks){streamChunks.add(pcm);}if(chunk.pauseMs>0){short[] silence=SentenceBoundaryPausePolicy.silence(playerSampleRate(),chunk.pauseMs);android.util.Log.i("YiwooPiperKo","PAUSE_PCM pauseMs="+chunk.pauseMs+" sampleRate="+playerSampleRate()+" frames="+silence.length+" samples="+silence.length);if(silence.length>0){result=audioTrack.write(silence,0,silence.length);if(result<0)throw new IllegalStateException("AUDIO_WRITE_FAILED:"+result);android.util.Log.i("YiwooPiperKo","PLAYBACK_WRITE type=SENTENCE_SILENCE frames="+silence.length);synchronized(streamChunks){streamChunks.add(silence);}}}if(chunk.index==0)runOnUiThread(()->{if(generation==voiceGeneration)voiceState.setText("재생 중");});}synchronized(streamChunks){int n=0;for(short[] p:streamChunks)n+=p.length;playerPcm=new short[n];int o=0;for(short[] p:streamChunks){System.arraycopy(p,0,playerPcm,o,p.length);o+=p.length;}}playerText=text;playerModelRate=modelRate;playerRate=rate;playerPitch=pitch;playerEnglish=english;playerPauseMs=SentenceBoundaryPausePolicy.pauseMs(this);streamingSynthesis=false;runOnUiThread(()->{if(generation!=voiceGeneration)return;playbackDurationMs=playerPcm.length*1000/playerSampleRate();voiceTotal.setText(formatTime(playbackDurationMs));updateVoiceRtf();voiceState.setText("재생 중");});}catch(Throwable error){stream.cancel();android.util.Log.e("YiwooPiperKo","VOICE_TEST_STREAM_ERROR request="+generation,error);runOnUiThread(()->{if(generation==voiceGeneration){streamingSynthesis=false;voicePlaying=false;voiceState.setText("오류");voicePlay.setImageResource(com.yiwoosolution.koreantts.R.drawable.ic_play);}});}});}
  private void startStreamingTrack(){TimeAnnouncementPlayer.interrupt("VOICE_TEST");releaseAudioTrack();int min=AudioTrack.getMinBufferSize(playerSampleRate(),AudioFormat.CHANNEL_OUT_MONO,AudioFormat.ENCODING_PCM_16BIT);audioTrack=new AudioTrack(AudioManager.STREAM_MUSIC,playerSampleRate(),AudioFormat.CHANNEL_OUT_MONO,AudioFormat.ENCODING_PCM_16BIT,Math.max(min,min*4),AudioTrack.MODE_STREAM);audioTrack.play();voiceHandler.post(completionTick);}
  private void startPlayback(){if(playerPcm==null||playerPcm.length==0)return;releaseAudioTrack();int min=AudioTrack.getMinBufferSize(playerSampleRate(),AudioFormat.CHANNEL_OUT_MONO,AudioFormat.ENCODING_PCM_16BIT);int buffer=Math.max(min,playerPcm.length*2);audioTrack=new AudioTrack(AudioManager.STREAM_MUSIC,playerSampleRate(),AudioFormat.CHANNEL_OUT_MONO,AudioFormat.ENCODING_PCM_16BIT,buffer,AudioTrack.MODE_STREAM);audioTrack.write(playerPcm,0,playerPcm.length);audioTrack.play();voicePlaying=true;voicePaused=false;voiceState.setText("재생 중");voicePlay.setImageResource(com.yiwoosolution.koreantts.R.drawable.ic_pause);voiceHandler.post(completionTick);}
  private void resumePlayback(){if(audioTrack==null)return;audioTrack.play();voicePlaying=true;voicePaused=false;voiceState.setText("재생 중");voicePlay.setImageResource(com.yiwoosolution.koreantts.R.drawable.ic_pause);voiceHandler.post(completionTick);}
  private void pauseVoice(){if(audioTrack!=null&&voicePlaying){audioTrack.pause();voicePlaying=false;voicePaused=true;voiceHandler.removeCallbacks(completionTick);voiceState.setText("일시정지");voicePlay.setImageResource(com.yiwoosolution.koreantts.R.drawable.ic_play);}}
    private void finishPlayback(){int playedFrames=audioTrack==null?0:audioTrack.getPlaybackHeadPosition();android.util.Log.i("YiwooPiperKo","VOICE_TEST_PLAYBACK_COMPLETE queuedFrames="+(playerPcm==null?0:playerPcm.length)+" playedFrames="+playedFrames);Log.i("YiwooUi","RATE_PLAYBACK_DONE totalMs="+(android.os.SystemClock.elapsedRealtime()-playbackRequestStart)+" playSpanMs="+(android.os.SystemClock.elapsedRealtime()-firstWriteMs)+" nominalMs="+((playerPcm==null?0:playerPcm.length)*1000.0/playerSampleRate())+" inferenceMs="+requestInferenceMs+" processingMs="+requestProcessingMs+" speechFrames="+requestSpeechSamples+" underruns="+(audioTrack==null?-1:audioTrack.getUnderrunCount()-firstUnderruns)+" emptyChunkStarts="+emptyChunkStarts);voicePlaying=false;voicePaused=false;voiceHandler.removeCallbacks(completionTick);if(audioTrack!=null){audioTrack.stop();audioTrack.release();audioTrack=null;}if(voiceState!=null)voiceState.setText("완료");if(voicePlay!=null)voicePlay.setImageResource(com.yiwoosolution.koreantts.R.drawable.ic_play);}
  private void releaseAudioTrack(){voiceHandler.removeCallbacks(completionTick);if(audioTrack!=null){try{audioTrack.stop();}catch(Exception ignored){}audioTrack.release();audioTrack=null;}voicePlaying=false;voicePaused=false;}
  private void stopVoice(){pendingReadyPlay=false;voiceGeneration++;streamingSynthesis=false;if(activeStream!=null){activeStream.cancel();activeStream=null;}if(tts!=null)tts.stop();releaseAudioTrack();if(voiceTotal!=null)voiceTotal.setText(playerPcm==null?"--":formatTime(playerPcm.length*1000/playerSampleRate()));if(voiceState!=null)voiceState.setText("준비됨");if(voicePlay!=null)voicePlay.setImageResource(com.yiwoosolution.koreantts.R.drawable.ic_play);}
  private ImageButton mediaButton(int icon,String description,int size){ImageButton b=new ImageButton(this);b.setImageResource(icon);b.setContentDescription(description);b.setBackground(new android.graphics.drawable.RippleDrawable(android.content.res.ColorStateList.valueOf(0x33267460),bg(Color.TRANSPARENT,24),null));b.setColorFilter(GREEN);b.setPadding(dp(12),dp(12),dp(12),dp(12));b.setScaleType(ImageView.ScaleType.CENTER_INSIDE);b.setLayoutParams(new LinearLayout.LayoutParams(dp(size),dp(size)));return b;}
  private void speechDialog(){
    voiceScreen = new java.lang.ref.WeakReference<>(this);
    page=8;base("음성 테스트",true);intro("다양한 예문으로 음성을 들어보세요.");
    LinearLayout sample=surface();sample.setBackground(new YiwooDesign.Organic(this,false,22));
    LinearLayout header=new LinearLayout(this);header.setGravity(Gravity.CENTER_VERTICAL);presetMeta=txt("",14,INK,true);presetCounter=txt("",12,MUTED,false);header.addView(presetMeta,new LinearLayout.LayoutParams(0,-2,1));header.addView(presetCounter,lp(-2,-2,10,0,0,0));sample.addView(header);
    LinearLayout inputActions=new LinearLayout(this);Button selectPreset=action("예문 선택"),customInput=action("사용자 입력");inputActions.addView(selectPreset,new LinearLayout.LayoutParams(0,dp(48),1));inputActions.addView(customInput,new LinearLayout.LayoutParams(0,dp(48),1));sample.addView(inputActions);selectPreset.setOnClickListener(v->chooseVoicePreset());customInput.setOnClickListener(v->{stopVoice();customVoiceInput=true;voiceEditor.setText("");updatePresetHeader();voiceEditor.requestFocus();((android.view.inputmethod.InputMethodManager)getSystemService(INPUT_METHOD_SERVICE)).showSoftInput(voiceEditor,android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT);});
    voiceEditor=(VoiceSampleEditor)getLayoutInflater().inflate(R.layout.voice_sample_editor,sample,false);voiceEditor.setHint(voiceEnglish?"영어 문장을 입력하세요":"테스트할 문장을 입력하세요");voiceEditor.setGravity(Gravity.TOP|Gravity.START);voiceEditor.setTextSize(17);voiceEditor.setTextColor(INK);voiceEditor.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_FLAG_MULTI_LINE|InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);voiceEditor.setBackgroundColor(Color.TRANSPARENT);voiceEditor.setPadding(0,dp(10),dp(8),dp(8));voiceEditor.setMinLines(3);voiceEditor.setMaxLines(6);voiceEditor.setImeOptions(EditorInfo.IME_FLAG_NO_EXTRACT_UI);sample.addView(voiceEditor,lp(-1,-2,0,4,0,0));
    LinearLayout player=surface();player.setGravity(Gravity.CENTER_HORIZONTAL);player.setBackground(bg(Color.TRANSPARENT,0));player.setElevation(0);if(wide)player.setPadding(dp(8),dp(18),dp(8),dp(18));
    voiceState=txt("준비됨",14,GREEN,true);voiceTotal=txt("--",12,MUTED,false);voiceRtf=txt("",12,MUTED,false);
    player.setPadding(dp(8),dp(4),dp(8),dp(4));player.addView(voiceState,lp(-2,-2,0,0,0,4));
    voiceTotal.setGravity(Gravity.CENTER);voiceRtf.setGravity(Gravity.CENTER);player.addView(voiceTotal);player.addView(voiceRtf,lp(-2,-2,0,4,0,0));
    LinearLayout controls=new LinearLayout(this);controls.setGravity(Gravity.CENTER);ImageButton restart=mediaButton(R.drawable.ic_restart,"처음부터 재생",48);int playSize=52;voicePlay=mediaButton(R.drawable.ic_play,"재생 또는 일시정지",playSize);voicePlay.setBackground(new android.graphics.drawable.RippleDrawable(android.content.res.ColorStateList.valueOf(0x66ffffff),bg(DEEP,30),null));voicePlay.setColorFilter(Color.WHITE);voicePlay.setPadding(dp(14),dp(14),dp(14),dp(14));ImageButton stop=mediaButton(R.drawable.ic_stop,"정지",48);controls.addView(restart,lp(dp(48),dp(48),0,0,0,0));controls.addView(voicePlay,lp(dp(playSize),dp(playSize),8,6,8,6));controls.addView(stop,lp(dp(wide?36:48),dp(48),0,0,0,0));player.addView(controls);
    LinearLayout navigation=new LinearLayout(this);Button previous=action("‹  이전");Button next=action("다음  ›");previous.setBackground(bg(YiwooDesign.YiwooColors.SURFACE,24));next.setBackground(bg(YiwooDesign.YiwooColors.SURFACE,24));navigation.addView(previous,new LinearLayout.LayoutParams(0,dp(48),1));navigation.addView(next,new LinearLayout.LayoutParams(0,dp(48),1));sample.addView(navigation,lp(-1,-2,0,4,0,0));TextView language=txt(voiceEnglish?"영어":"한국어",12,MUTED,false);player.addView(language,lp(-2,-2,0,10,0,0));
    if(wide)pair(contentRoot,sample,player);else{contentRoot.addView(sample,lp(-1,-2,18,0,18,12));contentRoot.addView(player,lp(-1,-2,18,0,18,0));}
    previous.setOnClickListener(v->{presetIndex--;loadPreset(true);});next.setOnClickListener(v->{presetIndex++;loadPreset(true);});restart.setOnClickListener(v->{TimeAnnouncementPlayer.interrupt("VOICE_TEST");speakFromStart();});voicePlay.setOnClickListener(v->{v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);TimeAnnouncementPlayer.interrupt("VOICE_TEST");if(voicePlaying)pauseVoice();else if(voicePaused)resumePlayback();else speakFromStart();});stop.setOnClickListener(v->stopVoice());
    voiceEditor.addTextChangedListener(new android.text.TextWatcher(){public void beforeTextChanged(CharSequence s,int st,int c,int a){}public void onTextChanged(CharSequence s,int st,int b,int c){if(voicePlaying||voicePaused)stopVoice();}public void afterTextChanged(android.text.Editable e){}});
    loadPreset(false);contentRoot.setFocusableInTouchMode(true);contentRoot.requestFocus();root.post(()->((ScrollView)contentRoot.getParent()).scrollTo(0,0));
  }
  private boolean hasAccess(){String s=Settings.Secure.getString(getContentResolver(),"enabled_notification_listeners");return s!=null&&s.contains(new ComponentName(this,YiwooNotificationListenerService.class).flattenToString());} private void openNotificationSettings(){try{startActivity(new Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS"));}catch(Exception ignored){}} private void openTtsSettings(){try{startActivity(new Intent("android.settings.TTS_SETTINGS"));}catch(Exception ignored){}}
  private void showTextDialog(String t,String m){showTextDialog(t,m,null);} private void showTextDialog(String t,String m,Runnable r){AlertDialog.Builder b=new AlertDialog.Builder(this).setTitle(t).setMessage(m).setPositiveButton("확인",null);if(r!=null)b.setNegativeButton("설정 열기",(d,w)->r.run());b.show();} private String diagnostics(){VoiceDescriptor v=VoiceRegistry.ACTIVE;PiperModelManager m=PiperModelManager.shared(this);double inference=PiperModelManager.lastInferenceMs();int samples=PiperModelManager.lastPcmSamples();double duration=samples>0?samples/(double)v.sampleRate:0;double rtf=duration>0?inference/(duration*1000.0):Double.NaN;return "엔진 상태: "+m.lifecycleState()+"\n모델 준비: "+m.isReady()+"\n유지 정책: "+m.retentionMode()+"\n유휴 시간: "+m.retentionMinutes()+"분\n최근 사용: "+m.lastUseElapsedMs()+"\n언어: "+v.locale+"\nFrontend: "+v.frontendVersion+"\nVocab: "+v.vocabVersion+"\n최근 추론: "+(Double.isFinite(inference)?String.format(Locale.US,"%.1f ms",inference):"-")+"\n최근 오디오 길이: "+String.format(Locale.US,"%.2f 초",duration)+"\n최근 Neural RTF: "+(Double.isFinite(rtf)?String.format(Locale.US,"%.3f",rtf):"-");}
  private void copyDiagnostics(){ClipboardManager c=(ClipboardManager)getSystemService(Context.CLIPBOARD_SERVICE);c.setPrimaryClip(ClipData.newPlainText("YIWOO TTS 진단 정보",diagnostics()));}
  @Override public void onBackPressed(){if(page!=0)showMain();else super.onBackPressed();} @Override protected void onDestroy(){if(loadingDialog!=null)loadingDialog.dismiss();stopVoice();synthesisExecutor.shutdownNow();if(tts!=null){tts.stop();tts.shutdown();}super.onDestroy();}
}
