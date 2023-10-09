package com.simon816.castremote;

import android.app.Activity;
import android.app.Fragment;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.SeekBar;
import android.widget.Spinner;
import com.simon816.castremote.youtube.YouTubeFragment;
import su.litvak.chromecast.api.v2.ChromeCast;
import su.litvak.chromecast.api.v2.MediaStatus;
import su.litvak.chromecast.api.v2.MediaStatus.PlayerState;
import su.litvak.chromecast.api.v2.Status;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

public class CastActivity extends Activity {

    public static final Map<String, String> APPS = new HashMap<>();
    public static final Map<String, Class<? extends Fragment>> APP_TO_FRAGMENT = new HashMap<>();
    {
        APPS.put("YouTube", "233637DE");
        APPS.put("Soundcloud", "B143C57E");

        APP_TO_FRAGMENT.put(APPS.get("YouTube"), YouTubeFragment.class);
        APP_TO_FRAGMENT.put(APPS.get("Soundcloud"), SoundcloudFragment.class);
    }

    public static final String APP_YOUTUBE = "233637DE";
    public static final String APP_SOUNDCLOUD_PLAYER = "B143C57E";

    ChromeCast chromeCast;
    boolean ready = false;

    public ChromeCast getChromeCast() {
        return chromeCast;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ready = false;
        setContentView(R.layout.cast_connecting);
        Intent intent = getIntent();
        String title = intent.getStringExtra("title");
        String appTitle = intent.getStringExtra("appTitle");
        String model = intent.getStringExtra("model");
        chromeCast = new ChromeCast(intent.getStringExtra("address"), intent.getIntExtra("port", -1));
        chromeCast.setName(intent.getStringExtra("name"));
        chromeCast.setAppsURL(intent.getStringExtra("appsURL"));
        chromeCast.setApplication(intent.getStringExtra("application"));
        System.out.println("Open chromecast " + chromeCast);
        new AsyncTask<Object, Object, Object[]>() {

            @Override
            protected Object[] doInBackground(Object... params) {
                try {
                    chromeCast.connect();
                    su.litvak.chromecast.api.v2.Status status = chromeCast.getStatus();
                    if (status.getRunningApp() != null && !status.getRunningApp().isIdleScreen) {
                        MediaStatus mediaStatus = chromeCast.getMediaStatus();
                        return new Object[] {status, mediaStatus};
                    }
                    return new Object[] {status};
                } catch (IOException e) {
                    // TODO Auto-generated catch block
                    e.printStackTrace();
                } catch (GeneralSecurityException e) {
                    // TODO Auto-generated catch block
                    e.printStackTrace();
                }
                return null;
            }

            @Override
            protected void onPreExecute() {
            }

            protected void onPostExecute(Object[] result) {
                if (result == null)
                    return;
                su.litvak.chromecast.api.v2.Status status = (su.litvak.chromecast.api.v2.Status) result[0];
                MediaStatus mediaStatus = null;
                if (result.length > 1) {
                    mediaStatus = (MediaStatus) result[1];
                }
                onConnected(status, mediaStatus);
            };
        }.execute();
    }

    protected void onConnected(Status status, MediaStatus mediaStatus) {
        setContentView(R.layout.cast_view);
        final Spinner appSelect = (Spinner) findViewById(R.id.app_launch_select);
        Object[] apps = APPS.keySet().toArray();
        Arrays.sort(apps);
        appSelect.setAdapter(new ArrayAdapter<>(getApplicationContext(), R.layout.spinner_text, apps));
        findViewById(R.id.launch_app).setOnClickListener(new OnClickListener() {

            @Override
            public void onClick(View v) {
                String appName = (String) appSelect.getSelectedItem();
                if (appName == null)
                    return;
                final String appId = APPS.get(appName);
                new AsyncTask<Object, Object, Object>() {

                    @Override
                    protected Object doInBackground(Object... params) {
                        try {
                            chromeCast.launchApp(appId);
                            // Youtube specific
                            chromeCast.send("urn:x-cast:com.google.youtube.mdx", new YoutubeMessage.GetMdxSessionStatus());
                        } catch (IOException e) {
                            // TODO Auto-generated catch block
                            e.printStackTrace();
                        }
                        return null;
                    }
                }.execute();
            }
        });
        findViewById(R.id.stop_app).setOnClickListener(new OnClickListener() {

            @Override
            public void onClick(View v) {
                new AsyncTask<Object, Object, Object>() {

                    @Override
                    protected Object doInBackground(Object... params) {
                        try {
                            chromeCast.stopApp();
                        } catch (IOException e) {
                            // TODO Auto-generated catch block
                            e.printStackTrace();
                        }
                        return null;
                    }
                }.execute();
            }
        });

        SeekBar volBar = (SeekBar) findViewById(R.id.volume_bar);
        int volume = (int) (status.volume.level * volBar.getMax());
        volBar.setProgress(volume);
        CheckBox muteBox = (CheckBox) findViewById(R.id.mute_button);
        muteBox.setChecked(status.volume.muted);
        System.out.println("Got status " + status);
        System.out.println("got media status " + mediaStatus);

        Button prevButton = (Button) findViewById(R.id.cast_previous);
        prevButton.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View v) {
                new Thread() {

                    public void run() {
                        try {
                            chromeCast.queueUpdate(-1);
                        } catch (IOException e) {
                            // TODO Auto-generated catch block
                            e.printStackTrace();
                        }
                    };
                }.start();
            }
        });
        final Button playButton = (Button) findViewById(R.id.cast_pause_play);
        playButton.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View v) {
                new AsyncTask<Object, Object, Boolean>() {

                    @Override
                    protected Boolean doInBackground(Object... params) {

                        try {
                            PlayerState state = chromeCast.getMediaStatus().playerState;
                            if (state != PlayerState.PLAYING) {
                                chromeCast.play();
                                return true;
                            } else {
                                chromeCast.pause();
                                return false;
                            }
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                        return null;
                    }

                    protected void onPostExecute(Boolean isPlaying) {
                        if (isPlaying == null)
                            return;
                        if (isPlaying) {
                            playButton.setText("⏸");
                        } else {
                            playButton.setText("⏵");
                        }
                    }
                }.execute();
            }
        });
        Button nextButton = (Button) findViewById(R.id.cast_next);
        nextButton.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View v) {
                new Thread() {

                    public void run() {
                        try {
                            chromeCast.queueUpdate(1);
                        } catch (IOException e) {
                            // TODO Auto-generated catch block
                            e.printStackTrace();
                        }
                    };
                }.start();
            }
        });

        Class<? extends Fragment> appFrag = APP_TO_FRAGMENT.get(status.getRunningApp().id);
        if (appFrag != null) {
            try {
                getFragmentManager().beginTransaction().add(R.id.controller_container, appFrag.newInstance()).commit();
            } catch (InstantiationException | IllegalAccessException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
        }
        ready = true;
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.cast, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();
        if (id == R.id.action_settings) {
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
}
