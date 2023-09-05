package com.simon816.castremote.youtube;

import android.app.Fragment;
import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.TextView;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.simon816.castremote.CastActivity;
import com.simon816.castremote.CastRemoteApp;
import com.simon816.castremote.R;
import com.simon816.castremote.YouTubeSession;
import com.simon816.castremote.YouTubeSession.EventType;
import com.simon816.castremote.YouTubeSession.YTVideo;
import com.simon816.castremote.YoutubeMessage;
import su.litvak.chromecast.api.v2.AppEvent;
import su.litvak.chromecast.api.v2.ChromeCast;
import su.litvak.chromecast.api.v2.ChromeCastSpontaneousEvent;
import su.litvak.chromecast.api.v2.ChromeCastSpontaneousEvent.SpontaneousEventType;
import su.litvak.chromecast.api.v2.ChromeCastSpontaneousEventListener;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class YouTubeFragment extends Fragment {

    static ObjectMapper jsonMapper;

    static {
        jsonMapper = new ObjectMapper();
        jsonMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }

    ChromeCast chromeCast;
    YouTubeSession session;
    Thread ytInteractionThread;
    boolean interactionIsSleeping = false;
    boolean running;
    boolean pausePlayIsPaused = true;
    List<YTVideo> lastKnownQueue;
    int lastKnownIndex = -1;
    Runnable seekBarTicker;
    Handler handler;
    int setSeek;
    Runnable delayedSeek = new Runnable() {

        @Override
        public void run() {
            sendInteraction(new YTInteraction.SeekInteraction(setSeek));
        }
    };

    static interface YTInteraction {

        public class SeekInteraction implements YTInteraction {

            private final float seconds;

            public SeekInteraction(float seconds) {
                this.seconds = seconds;
            }

            @Override
            public void run(YouTubeSession session) throws IOException {
                session.seek(seconds);
            }

        }

        public class RemoveVideoInteraction implements YTInteraction {

            private final String videoId;

            public RemoveVideoInteraction(String videoId) {
                this.videoId = videoId;
            }

            @Override
            public void run(YouTubeSession session) throws IOException {
                session.removeVideo(videoId);
            }

        }

        public class PlayQueueItemInteraction implements YTInteraction {

            private final String videoId;

            public PlayQueueItemInteraction(String videoId) {
                this.videoId = videoId;
            }

            @Override
            public void run(YouTubeSession session) throws IOException {
                session.jumpToVideoFromPlaylist(videoId);
            }

        }

        public class PlayNowInteraction implements YTInteraction {

            private final String videoId;

            public PlayNowInteraction(String videoId) {
                this.videoId = videoId;
            }

            @Override
            public void run(YouTubeSession session) throws IOException {
                session.playNow(videoId);
            }

        }

        public class PlayNextInteraction implements YTInteraction {

            private final String videoId;

            public PlayNextInteraction(String videoId) {
                this.videoId = videoId;
            }

            @Override
            public void run(YouTubeSession session) throws IOException {
                session.playNext(videoId);
            }

        }

        public class AddToQueueInteraction implements YTInteraction {

            private final String videoId;

            public AddToQueueInteraction(String videoId) {
                this.videoId = videoId;
            }

            @Override
            public void run(YouTubeSession session) throws IOException {
                session.addToQueue(videoId);
            }

        }

        YTInteraction PREVIOUS = new YTInteraction() {

            @Override
            public void run(YouTubeSession session) throws IOException {
                session.previousVideo();
            }
        };
        YTInteraction NEXT = new YTInteraction() {

            @Override
            public void run(YouTubeSession session) throws IOException {
                session.nextVideo();
            }
        };
        YTInteraction RESUME = new YTInteraction() {

            @Override
            public void run(YouTubeSession session) throws IOException {
                session.resumeVideo();
            }
        };
        YTInteraction PAUSE = new YTInteraction() {

            @Override
            public void run(YouTubeSession session) throws IOException {
                session.pauseVideo();
            }
        };

        void run(YouTubeSession session) throws IOException;
    }

    final Queue<YTInteraction> interactionQueue = new ConcurrentLinkedQueue<>();

    @Override
    public void onAttach(Context context) {
        super.onAttach(context);
        running = true;
        configureChromecast();

        new Thread() {

            public void run() {
                try {
                    chromeCast.send("urn:x-cast:com.google.youtube.mdx", new YoutubeMessage.GetMdxSessionStatus());
                } catch (IOException e) {
                    // TODO Auto-generated catch block
                    e.printStackTrace();
                }
            };
        }.start();
    }

    private void configureChromecast() {
        final String deviceName = "Simon";
        final String deviceId = ((CastRemoteApp) getActivity().getApplication()).deviceId;
        // bind chromecast from activity to this fragment
        // TODO handle non-chromecast case
        chromeCast = ((CastActivity) getActivity()).getChromeCast();
        chromeCast.registerListener(new ChromeCastSpontaneousEventListener() {

            @Override
            public void spontaneousEventReceived(ChromeCastSpontaneousEvent event) {
                if (event.getType() == SpontaneousEventType.APPEVENT) {
                    AppEvent data = event.getData(AppEvent.class);
                    if (data.namespace.equals("urn:x-cast:com.google.youtube.mdx")) {
                        try {
                            YoutubeMessage.YoutubeResponse resp = jsonMapper.readValue(data.message, YoutubeMessage.YoutubeResponse.class);
                            if (resp instanceof YoutubeMessage.MdxSessionStatus) {
                                String screenId = (String) resp.data.get("screenId");
                                session = new YouTubeSession(screenId, deviceName, deviceId);
                                ytInteractionThread = new Thread(new YouTubeInteraction(session));
                                ytInteractionThread.setDaemon(true);
                                ytInteractionThread.start();
                            }
                        } catch (JsonMappingException e) {
                            // TODO Auto-generated catch block
                            e.printStackTrace();
                        } catch (JsonProcessingException e) {
                            // TODO Auto-generated catch block
                            e.printStackTrace();
                        }
                    }
                }
            }
        });
    }

    void onDied() {
        getActivity().getFragmentManager().beginTransaction().remove(this).commit();
    }

    @Override
    public void onDetach() {
        running = false;
        super.onDetach();
    }

    void sendInteraction(YTInteraction interaction) {
        interactionQueue.add(interaction);
        // wake thread if sleeping
        if (interactionIsSleeping)
            ytInteractionThread.interrupt();
    }

    @Override
    public View onCreateView(final LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.youtube_layout, container, false);
        final Spinner modeSpinner = (Spinner) view.findViewById(R.id.yt_add_mode);
        modeSpinner.setAdapter(new ArrayAdapter<>(getContext(), R.layout.spinner_text, new String[] {
                "Queue",
                "Play Next",
                "Play Now",
        }));
        final EditText addVideoText = (EditText) view.findViewById(R.id.yt_add_video_text);
        view.findViewById(R.id.yt_add_to_queue).setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View v) {
                String videoId = getVideoId(addVideoText);
                if (videoId != null) {
                    addVideoText.setText("");
                    switch (modeSpinner.getSelectedItemPosition()) {
                        case 0: // Queue
                            sendInteraction(new YTInteraction.AddToQueueInteraction(videoId));
                            break;
                        case 1: // Play Next
                            sendInteraction(new YTInteraction.PlayNextInteraction(videoId));
                            break;
                        case 2: // Play Now
                            sendInteraction(new YTInteraction.PlayNowInteraction(videoId));
                            break;
                    }
                }
            }
        });
        view.findViewById(R.id.yt_next).setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View v) {
                sendInteraction(YTInteraction.NEXT);
            }
        });
        view.findViewById(R.id.yt_pause_play).setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View v) {
                if (pausePlayIsPaused) {
                    sendInteraction(YTInteraction.RESUME);
                } else {
                    sendInteraction(YTInteraction.PAUSE);
                }
                pausePlayIsPaused = !pausePlayIsPaused;
                updatePlayPaused(v);
            }
        });
        view.findViewById(R.id.yt_previous).setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View v) {
                sendInteraction(YTInteraction.PREVIOUS);
            }
        });
        ListView queueView = (ListView) view.findViewById(R.id.yt_queue);
        queueView.setAdapter(new ArrayAdapter<YTVideo>(getContext(), 0) {

            @Override
            public View getView(int position, View convertView, ViewGroup parent) {
                View view;
                if (convertView == null) {
                    view = inflater.inflate(R.layout.yt_video_queue_item, parent, false);
                } else {
                    view = convertView;
                }
                final YTVideo video = getItem(position);
                TextView titleView = (TextView) view.findViewById(R.id.yt_q_title);
                titleView.setText(video.title);
                TextView durationView = (TextView) view.findViewById(R.id.yt_q_duration);
                durationView.setText(video.length);
                if (lastKnownIndex != -1 && position < lastKnownIndex) {
                    titleView.setTextColor(getResources().getColor(R.color.color_disabled_text, null));
                    durationView.setTextColor(getResources().getColor(R.color.color_disabled_text, null));
                } else {
                    // otherwise, set to black
                    titleView.setTextColor(0xFF000000);
                    durationView.setTextColor(0xFF000000);
                }
                view.findViewById(R.id.yt_q_play).setOnClickListener(new View.OnClickListener() {

                    @Override
                    public void onClick(View v) {
                        sendInteraction(new YTInteraction.PlayQueueItemInteraction(video.videoId));
                    }
                });
                view.findViewById(R.id.yt_q_remove).setOnClickListener(new View.OnClickListener() {

                    @Override
                    public void onClick(View v) {
                        sendInteraction(new YTInteraction.RemoveVideoInteraction(video.videoId));
                    }
                });
                // TODO thumbnail
                return view;
            }
        });
        final SeekBar seekBar = (SeekBar) view.findViewById(R.id.yt_seek_bar);
        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
            }

            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser) {
                    setSeek = progress;
                    handler.removeCallbacks(delayedSeek);
                    handler.postDelayed(delayedSeek, 200);
                }
            }
        });
        handler = new Handler(Looper.getMainLooper());
        seekBarTicker = new Runnable() {

            @Override
            public void run() {
                if (pausePlayIsPaused || seekBar.getProgress() == seekBar.getMax())
                    return;
                seekBar.setProgress(seekBar.getProgress() + 1, true);
                handler.postDelayed(seekBarTicker, 1000);
            }
        };
        return view;
    }

    void updatePlayPaused(View v) {
        ((Button) v).setText(pausePlayIsPaused ? "⏵" : "⏸");
    }

    String getVideoId(EditText textbox) {
        String text = textbox.getText().toString().trim();
        if (text.startsWith("http")) {
            try {
                URL url = new URL(text);
                String query = url.getQuery();
                if (query != null) {
                    Pattern pat = Pattern.compile("([^&=]+)=([^&]*)");
                    Matcher matcher = pat.matcher(query);
                    while (matcher.find()) {
                        if ("v".equals(matcher.group(1)) && matcher.group(2) != null) {
                            return matcher.group(2);
                        }
                    }
                }
            } catch (MalformedURLException e) {
            }
        }
        return text;
    }

    void updateQueue(boolean doScroll) {
        if (lastKnownQueue == null)
            return;
        View view = getView();
        if (view == null)
            return;
        ListView queueView = (ListView) view.findViewById(R.id.yt_queue);
        @SuppressWarnings("unchecked")
        ArrayAdapter<YTVideo> adapter = (ArrayAdapter<YTVideo>) queueView.getAdapter();
        adapter.clear();
        adapter.addAll(lastKnownQueue);
        updateNowPlaying(view, doScroll);
    }

    void updateNowPlaying(View view, boolean doScroll) {
        int curIndex = lastKnownIndex;
        if (curIndex == -1)
            return;
        List<YTVideo> queue = lastKnownQueue;
        if (queue != null && curIndex < queue.size() - 1) {
            YTVideo video = queue.get(curIndex);
            ((TextView) view.findViewById(R.id.yt_title)).setText(video.title);
            ((TextView) view.findViewById(R.id.yt_duration)).setText(video.length);
            // TODO:
            // view.findViewById(R.id.yt_video_thumb);
            ListView queueView = (ListView) view.findViewById(R.id.yt_queue);
            if (doScroll)
                queueView.smoothScrollToPosition(lastKnownIndex);
        }
    }

    private class YouTubeInteraction implements Runnable, YouTubeSession.YTEventListener {

        private final YouTubeSession session;

        public YouTubeInteraction(YouTubeSession session) {
            this.session = session;
            session.addEventListener(this);
        }

        @Override
        public void onEvent(YouTubeSession session, EventType eventType) {
            switch (eventType) {
                case PLAYLIST:
                    try {
                        final boolean isFirst = lastKnownQueue == null;
                        lastKnownQueue = session.getQueue();
                        getActivity().runOnUiThread(new Runnable() {

                            @Override
                            public void run() {
                                updateQueue(isFirst);
                            }
                        });
                    } catch (IOException e) {
                        // TODO Auto-generated catch block
                        e.printStackTrace();
                    }
                    break;
                case NOW_PLAYING:
                    if (session.state.getPlayState() != YouTubeSession.YTState.STATE_IDLE) {
                        final int state = session.state.getPlayState();
                        final int curTime = (int) session.state.getCurrentTime();
                        final int seekEnd = (int) session.state.getSeekEndTime();
                        final int curIndex = session.state.getCurrentPlaylistIndex();
                        if (seekBarTicker != null) {
                            handler.removeCallbacks(seekBarTicker);
                            if (state != YouTubeSession.YTState.STATE_PAUSED) {
                                handler.postDelayed(seekBarTicker, 1000);
                            }
                        }
                        getActivity().runOnUiThread(new Runnable() {

                            @Override
                            public void run() {
                                View view = getView();
                                SeekBar seekBar = (SeekBar) view.findViewById(R.id.yt_seek_bar);
                                seekBar.setMax(seekEnd);
                                seekBar.setProgress(curTime, true);
                                pausePlayIsPaused = state == YouTubeSession.YTState.STATE_PAUSED;
                                updatePlayPaused(view.findViewById(R.id.yt_pause_play));
                                boolean indexChanged = curIndex != lastKnownIndex;
                                lastKnownIndex = curIndex;
                                updateNowPlaying(view, indexChanged);
                            }
                        });
                    } else {
                        getActivity().runOnUiThread(new Runnable() {

                            @Override
                            public void run() {
                                View view = getView();
                                ((TextView) view.findViewById(R.id.yt_title)).setText("");
                                SeekBar seekBar = (SeekBar) view.findViewById(R.id.yt_seek_bar);
                                seekBar.setMax(0);
                                seekBar.setProgress(0);
                                pausePlayIsPaused = true;
                                updatePlayPaused(view.findViewById(R.id.yt_pause_play));
                            }
                        });
                    }
                    break;
                case VOLUME:
                    break;
                case ADVERT:
                    break;
            }

        }

        public void run() {
            try {
                while (running) {
                    mainLoop();
                }
            } catch (Exception e) {
                e.printStackTrace();
                getActivity().runOnUiThread(new Runnable() {

                    @Override
                    public void run() {
                        onDied();
                    }
                });
            }
        };

        private void mainLoop() throws IOException {
            YTInteraction action;
            while ((action = interactionQueue.poll()) != null) {
                action.run(session);
            }
            if (session.pollEvents()) {
                try {
                    interactionIsSleeping = true;
                    Thread.sleep(5000);
                    interactionIsSleeping = false;
                    session.getNowPlaying();
                } catch (InterruptedException e) {
                    System.out.println("Thread woken");
                    interactionIsSleeping = false;
                }
            }
        }
    }
}
