package com.gogi.hisenseirexplorer;

import android.app.Activity;
import android.hardware.ConsumerIrManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.content.Context;
import android.graphics.Color;
import android.view.View;
import android.widget.*;
import java.util.*;

public class MainActivity extends Activity {
  ConsumerIrManager ir; Handler h=new Handler(Looper.getMainLooper());
  TextView status,progress,log; Spinner mode,freq,scan; SeekBar temp,fan; CheckBox swing,sleep,turbo,light;
  boolean running=false; int index=0; ArrayList<String> entries=new ArrayList<>();
  int carrier=38000;
  public void onCreate(Bundle b){super.onCreate(b);ir=(ConsumerIrManager)getSystemService(Context.CONSUMER_IR_SERVICE);
    LinearLayout root=new LinearLayout(this);root.setPadding(18,18,18,18);root.setOrientation(1);root.setBackgroundColor(Color.rgb(246,248,250));
    ScrollView sv=new ScrollView(this);sv.addView(root);setContentView(sv);
    add(root,"HISENSE IR EXPLORER",24,true);
    status=add(root,ir!=null&&ir.hasIrEmitter()?"IR predajnik dostupan":"NEMA IR PREDajnika",14,true);
    add(root,"Rezim rada",15,true);mode=spinner(root,new String[]{"AUTO","HEAT","COOL","DRY","FAN"});
    add(root,"Temperatura 16–30 °C",15,true);temp=bar(root,14,8);
    add(root,"Ventilator",15,true);fan=bar(root,5,0);
    swing=check(root,"SWING");sleep=check(root,"SLEEP");turbo=check(root,"TURBO");light=check(root,"LED displej ukljucen");
    Button send=button(root,"POSALJI PODESAVANJE");send.setOnClickListener(v->transmitState());
    add(root,"Automatsko testiranje",19,true);
    scan=spinner(root,new String[]{"Sweep frekvencija (30–56 kHz)","Skeniraj rezime rada","Skeniraj temperature 16–30","Skeniraj ventilator","Skeniraj dodatne funkcije"});
    LinearLayout row=new LinearLayout(this);Button start=button(row,"START AUTO SCAN"),stop=button(row,"STOP");root.addView(row);
    start.setOnClickListener(v->startScan());stop.setOnClickListener(v->stopScan());
    progress=add(root,"Spremno.",14,true);
    add(root,"Napredni parametri",19,true);freq=spinner(root,new String[]{"38 kHz","36 kHz","40 kHz","56 kHz"});freq.setSelection(0);
    Button save=button(root,"PRIKAZI LOG");save.setOnClickListener(v->new android.app.AlertDialog.Builder(this).setTitle("Log").setMessage(String.join("\n",entries)).setPositiveButton("OK",null).show());
    log=add(root,"IR: transmit only; telefon ne moze da potvrdi prijem klime.",12,false);
  }
  TextView add(LinearLayout p,String s,int z,boolean bold){TextView t=new TextView(this);t.setText(s);t.setTextSize(z);t.setTextColor(Color.DKGRAY);if(bold)t.setTypeface(null,1);t.setPadding(0,9,0,5);p.addView(t);return t;}
  Spinner spinner(LinearLayout p,String[] a){Spinner s=new Spinner(this);s.setAdapter(new ArrayAdapter<String>(this,android.R.layout.simple_spinner_dropdown_item,a));p.addView(s);return s;}
  SeekBar bar(LinearLayout p,int max,int val){SeekBar b=new SeekBar(this);b.setMax(max);b.setProgress(val);p.addView(b);return b;}
  CheckBox check(LinearLayout p,String s){CheckBox c=new CheckBox(this);c.setText(s);p.addView(c);return c;}
  Button button(LinearLayout p,String s){Button b=new Button(this);b.setText(s);b.setAllCaps(false);p.addView(b);return b;}
  int frequency(){return new int[]{38000,36000,40000,56000}[freq.getSelectedItemPosition()];}
  void transmitState(){int m=mode.getSelectedItemPosition(),t=16+temp.getProgress(),f=fan.getProgress();byte[] d=new byte[21];
    d[0]=(byte)0x83;d[1]=(byte)0x06;d[2]=(byte)((f&7)|4|(sleep.isChecked()?8:0)|(swing.isChecked()?128:0));
    d[3]=(byte)((m&7)|((t-16)<<4));d[5]=(byte)(turbo.isChecked()?0x90:0);
    d[6]=(byte)(light.isChecked()?0x80:0xA0);d[8]=(byte)(swing.isChecked()?0x40:0);d[15]=(byte)2;d[18]=(byte)0x38;
    d[13]=xor(d,2,11);d[20]=xor(d,14,19);
    send(make(d),frequency(),"Manual "+m+" "+t+"C");
  }
  int xor(byte[] a,int x,int y){int v=0;for(int i=x;i<=y;i++)v^=a[i]&255;return v;}
  int[] make(byte[] d){ArrayList<Integer> p=new ArrayList<>();p.add(9000);p.add(4600);
    for(int sec=0;sec<3;sec++){int st=sec==0?0:sec==1?6:14,n=sec==0?6:sec==1?8:7;
      for(int i=st;i<st+n;i++)for(int bit=0;bit<8;bit++){p.add(560);p.add(((d[i]>>bit)&1)==1?1680:600);}
      p.add(560);p.add(sec==2?100000:8000);}
    int[] a=new int[p.size()];for(int i=0;i<a.length;i++)a[i]=p.get(i);return a;
  }
  void send(int[] p,int hz,String label){if(ir==null||!ir.hasIrEmitter()){status.setText("Telefon nema dostupan IR");return;}
    try{ir.transmit(hz,p);status.setText("Poslato "+label+" @ "+hz/1000+" kHz");}catch(Exception e){status.setText("Greska: "+e.getMessage());}}
  void startScan(){if(running)return;running=true;index=0;entries.clear();step();}
  void step(){if(!running)return;int kind=scan.getSelectedItemPosition();int total=kind==0?14:kind==1?5:kind==2?15:kind==3?6:4;
    if(index>=total){stopScan();return;}
    int hz=kind==0?30000+index*2000:frequency();String label;
    if(kind==0)label="Carrier "+hz;
    else if(kind==1)label="Mode "+index;
    else if(kind==2)label="Cool "+(16+index)+"C";
    else if(kind==3)label="Fan "+index;
    else label=new String[]{"Swing","Sleep","Turbo","Light"}[index];
    byte[] d=new byte[21];d[0]=(byte)0x83;d[1]=(byte)6;d[2]=4;d[3]=(byte)(2|((24-16)<<4));d[6]=(byte)0x80;d[18]=(byte)0x38;d[15]=(byte)2;
    if(kind==1)d[3]=(byte)((index&7)|((24-16)<<4));
    if(kind==2)d[3]=(byte)(2|((16+index-16)<<4));
    if(kind==3)d[2]=(byte)(4|(index&7));
    if(kind==4){if(index==0)d[8]=0x40;if(index==1)d[2]|=8;if(index==2)d[5]=(byte)0x90;if(index==3)d[6]=(byte)0xA0;}
    d[13]=(byte)xor(d,2,11);d[20]=(byte)xor(d,14,19);
    send(make(d),hz,label);entries.add((index+1)+". "+label+" "+hz+"Hz");progress.setText("Test "+(index+1)+"/"+total+": "+label+" — posmatraj klimu; STOP prekida.");
    index++;h.postDelayed(()->step(),2500);
  }
  void stopScan(){running=false;h.removeCallbacksAndMessages(null);if(progress!=null)progress.setText("Zaustavljeno/zavrseno. Poslato: "+entries.size());}
  @Override protected void onDestroy(){stopScan();super.onDestroy();}
}
