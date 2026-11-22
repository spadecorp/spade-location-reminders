package com.spadecorp.locationreminders;

import android.app.*;
import android.content.Intent;
import android.media.*;
import android.os.*;
import android.speech.tts.*;
import java.util.*;

public class SpeechService extends Service {
    private final Handler main=new Handler(Looper.getMainLooper());
    private final ArrayDeque<String> queue=new ArrayDeque<>();
    private TextToSpeech tts;
    private AudioManager audio;
    private boolean ready, speaking, focused;
    private final AudioManager.OnAudioFocusChangeListener focusListener=change->{if(change<0) stopSelf();};
    @Override public void onCreate(){
        super.onCreate();
        audio=getSystemService(AudioManager.class);
        Fences.channels(this);
        startForeground(14,Fences.notification(this,"speech","Location reminder","Reading your task aloud").setSilent(true).build());
        // Avoid speaking private tasks in silent mode or under Do Not Disturb.
        NotificationManager nm=getSystemService(NotificationManager.class);
        if(audio.getRingerMode()!=AudioManager.RINGER_MODE_NORMAL || nm.getCurrentInterruptionFilter()!=NotificationManager.INTERRUPTION_FILTER_ALL){
            stopSelf();return;
        }
        focused=audio.requestAudioFocus(focusListener,AudioManager.STREAM_NOTIFICATION,AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)==AudioManager.AUDIOFOCUS_REQUEST_GRANTED;
        if(!focused){stopSelf();return;}
        main.postDelayed(this::stopSelf,45000);
        tts=new TextToSpeech(this,status->main.post(()->{
            if(tts==null || status!=TextToSpeech.SUCCESS){stopSelf();return;}
            Voice voice=tts.getVoice();
            if(voice==null || voice.isNetworkConnectionRequired()){
                Set<Voice> voices=tts.getVoices();
                Voice offline=voices==null?null:voices.stream().filter(v->!v.isNetworkConnectionRequired() && v.getLocale().getLanguage().equals(Locale.getDefault().getLanguage())).findFirst().orElse(null);
                if(offline==null || tts.setVoice(offline)==TextToSpeech.ERROR){stopSelf();return;}
            }
            tts.setAudioAttributes(new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_NOTIFICATION_EVENT).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build());
            tts.setOnUtteranceProgressListener(new UtteranceProgressListener(){
                public void onStart(String id){}
                public void onDone(String id){next();}
                public void onError(String id){next();}
                private void next(){main.post(()->{speaking=false;readNext();});}
            });
            ready=true;readNext();
        }));
    }
    @Override public int onStartCommand(Intent intent,int flags,int startId){
        ArrayList<String> tasks=intent==null?null:intent.getStringArrayListExtra("tasks");
        if(tasks!=null)queue.addAll(tasks);
        readNext();return START_NOT_STICKY;
    }
    private void readNext(){
        if(!ready || speaking)return;
        String line=queue.poll();
        if(line==null){stopSelf();return;}
        speaking=true;
        if(tts.speak(line,TextToSpeech.QUEUE_FLUSH,null,UUID.randomUUID().toString())==TextToSpeech.ERROR)stopSelf();
    }
    @Override public void onDestroy(){
        ready=false;main.removeCallbacksAndMessages(null);
        if(tts!=null){tts.stop();tts.shutdown();tts=null;}
        if(focused)audio.abandonAudioFocus(focusListener);
        stopForeground(true);super.onDestroy();
    }
    @Override public IBinder onBind(Intent intent){return null;}
}
