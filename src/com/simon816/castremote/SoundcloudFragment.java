package com.simon816.castremote;

import android.app.Fragment;
import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.BaseAdapter;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import su.litvak.chromecast.api.v2.ChromeCast;
import su.litvak.chromecast.api.v2.ChromeCastSpontaneousEvent;
import su.litvak.chromecast.api.v2.ChromeCastSpontaneousEvent.SpontaneousEventType;
import su.litvak.chromecast.api.v2.ChromeCastSpontaneousEventListener;
import su.litvak.chromecast.api.v2.Media;
import su.litvak.chromecast.api.v2.MediaStatus;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class SoundcloudFragment extends Fragment {

    static final ObjectMapper jsonMapper = new ObjectMapper();
    private static final String DESKTOP_UA = "Mozilla/5.0 (X11; Linux x86_64; rv:109.0) Gecko/20100101 Firefox/117.0";
    private static final URL API_BASE;
    private ChromeCast chromeCast;
    private String clientId;
    private Map<String, SoundcloudItem> itemMap = new HashMap<>();
    Queue<SoundcloudItem> resolveQueue = new ConcurrentLinkedQueue<>();
    private Thread resolverThread;

    static {
        try {
            API_BASE = new URL("https://api-v2.soundcloud.com/");
        } catch (MalformedURLException e) {
            throw new RuntimeException(e);
        }
    }

    static class SoundcloudItem {

        public final String urn;
        private String title;
        boolean resolved = false;

        public SoundcloudItem(String urn) {
            this.urn = urn;
        }

        public String getDisplayText() {
            if (title != null) {
                return this.title;
            }
            return this.urn;
        }

        void fromApiData(JsonNode data) {
            this.title = data.get("title").textValue();
            this.resolved = true;
        }
    }

    @Override
    public void onAttach(Context context) {
        super.onAttach(context);
        chromeCast = ((CastActivity) getActivity()).getChromeCast();
        chromeCast.registerListener(new ChromeCastSpontaneousEventListener() {

            @Override
            public void spontaneousEventReceived(ChromeCastSpontaneousEvent event) {
                System.out.println("Chromecast event: " + event.getType() + " " + event.getData());
                if (event.getType() == SpontaneousEventType.MEDIA_STATUS) {
                    MediaStatus status = event.getData(MediaStatus.class);
                    if (status.media.customData != null && status.media.customData.size() > 0) {
                        System.out.println(status.media.customData);
                        @SuppressWarnings("unchecked")
                        final Map<String, Object> queueStatus = (Map<String, Object>) status.media.customData.get("queue_status");
                        getActivity().runOnUiThread(new Runnable() {

                            @Override
                            public void run() {
                                updateQueue(queueStatus);
                            }
                        });
                        Object track = status.media.customData.get("track");
                        System.out.println(track);
                    }
                }
            }
        });
        resolverThread = new Thread(new Runnable() {

            @Override
            public void run() {
                while (true) {
                    List<SoundcloudItem> itemList = new ArrayList<>();
                    SoundcloudItem item;
                    while ((item = resolveQueue.poll()) != null && itemList.size() < 50) {
                        itemList.add(item);
                    }
                    if (itemList.size() > 0) {
                        resolveItems(itemList);
                        getActivity().runOnUiThread(new Runnable() {

                            @Override
                            public void run() {
                                ListView queueView = (ListView) getActivity().findViewById(R.id.sc_queue);
                                BaseAdapter adapter = (BaseAdapter) queueView.getAdapter();
                                adapter.notifyDataSetChanged();
                            }
                        });
                    }
                    try {
                        Thread.sleep(1000);
                    } catch (InterruptedException e) {
                        break;
                    }
                }
            }

        });
        resolverThread.setDaemon(true);
    }

    void resolveItems(List<SoundcloudItem> itemList) {
        Map<String, String> query = new HashMap<>();
        query.put("ids", itemList.stream().map(new Function<SoundcloudItem, String>() {

            @Override
            public String apply(SoundcloudItem t) {
                return t.urn.substring(t.urn.lastIndexOf(':') + 1);
            }
        }).collect(Collectors.joining(",")));

        try {
            JsonNode resp = apiCall("tracks", query);
            for (JsonNode item : resp) {
                SoundcloudItem trackItem = itemMap.get(item.get("urn").textValue());
                if (trackItem == null) {
                    continue;
                }
                trackItem.fromApiData(item);
            }
        } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
    }

    private JsonNode apiCall(String endpoint, Map<String, String> query) throws IOException {
        query.put("client_id", clientId);
        URL url = new URL(API_BASE, endpoint + "?" + Utils.urlEncodeParams(query));
        HttpURLConnection con = (HttpURLConnection) url.openConnection();
        try (InputStream stream = con.getInputStream()) {
            return jsonMapper.readTree(stream);
        }
    }

    void updateQueue(Map<String, Object> queueStatus) {
        View view = getView();
        if (view == null)
            return;
        Integer currentIndex = (Integer) queueStatus.get("current_index");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> queue = (List<Map<String, Object>>) queueStatus.get("queue");
        ListView queueView = (ListView) view.findViewById(R.id.sc_queue);
        @SuppressWarnings("unchecked")
        ArrayAdapter<SoundcloudItem> adapter = (ArrayAdapter<SoundcloudItem>) queueView.getAdapter();
        adapter.clear();
        for (Map<String, Object> item : queue) {
            adapter.add(getOrCreateItem((String) item.get("urn")));
        }
        if (currentIndex != null)
            queueView.smoothScrollToPosition(currentIndex);
    }

    private SoundcloudItem getOrCreateItem(String urn) {
        if (itemMap.containsKey(urn)) {
            return itemMap.get(urn);
        } else {
            SoundcloudItem item = new SoundcloudItem(urn);
            itemMap.put(urn, item);
            resolveQueue.add(item);
            return item;
        }
    }

    @Override
    public View onCreateView(final LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        final View view = inflater.inflate(R.layout.sc_layout, container, false);
        ListView queueView = (ListView) view.findViewById(R.id.sc_queue);
        queueView.setAdapter(new ArrayAdapter<SoundcloudItem>(getContext(), 0) {

            @Override
            public View getView(int position, View convertView, ViewGroup parent) {
                View view;
                if (convertView == null) {
                    view = inflater.inflate(R.layout.sc_queue_item, parent, false);
                } else {
                    view = convertView;
                }
                SoundcloudItem item = getItem(position);
                TextView textView = (TextView) view.findViewById(R.id.sc_queue_item_text);
                textView.setText(item.getDisplayText());
                return view;
            }
        });
        view.findViewById(R.id.sc_launch_url_go).setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View v) {
                EditText urlText = (EditText) view.findViewById(R.id.sc_launch_url);
                String urlStr = urlText.getText().toString().trim();
                if (!urlStr.isEmpty()) {
                    try {
                        final URL url = new URL(urlStr);
                        if (url.getProtocol().equals("http") || url.getProtocol().equals("https")) {
                            if ("soundcloud.com".equals(url.getHost())) {
                                urlText.setEnabled(false);
                                new Thread() {

                                    public void run() {
                                        try {
                                            openSoundcloud(url);
                                        } catch (IOException e) {
                                            // TODO Auto-generated catch block
                                            e.printStackTrace();
                                        }
                                    }
                                }.start();
                            }
                        }
                    } catch (MalformedURLException e) {
                        // TODO Auto-generated catch block
                        e.printStackTrace();
                    }
                }
            }
        });
        return view;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (savedInstanceState == null)
            return;
        clientId = savedInstanceState.getString("clientId", clientId);
        if (clientId != null) {
            if (!resolverThread.isAlive())
                resolverThread.start();
        }
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        outState.putString("clientId", clientId);
    }

    void openSoundcloud(URL url) throws IOException {
        HttpURLConnection con = (HttpURLConnection) url.openConnection();
        List<URL> scripts = new ArrayList<>();
        JsonNode hydrationData = null;
        // Soundcloud needs a desktop UA otherwise it serves a different page
        con.addRequestProperty("User-Agent", DESKTOP_UA);
        try (InputStream stream = con.getInputStream()) {
            // From
            // https://github.com/yt-dlp/yt-dlp/blob/b532556d0a85e7d76f8f0880861232fb706ddbc5/yt_dlp/extractor/soundcloud.py#L80
            Pattern patScript = Pattern.compile("<script[^>]+src=\"([^\"]+)\"");
            BufferedReader reader = new BufferedReader(new InputStreamReader(stream));
            String line;
            while ((line = reader.readLine()) != null) {
                if (hydrationData == null) {
                    int idxHydration = line.indexOf("__sc_hydration = ");
                    if (idxHydration != -1) {
                        int idxEnd = line.lastIndexOf(';');
                        if (idxEnd != -1) {
                            hydrationData = jsonMapper.readTree(line.substring(idxHydration + 17, idxEnd));
                        }
                    }
                }
                if (line.indexOf("<script") != -1) {
                    Matcher m = patScript.matcher(line);
                    while (m.find()) {
                        try {
                            URL scriptUrl = new URL(url, m.group(1));
                            scripts.add(scriptUrl);
                        } catch (MalformedURLException e) {
                        }
                    }
                }
            }
        }
        Pattern patClientId = Pattern.compile("client_id\\s*:\\s*\"([0-9a-zA-Z]{32})\"");
        scriptLoop: for (int i = scripts.size() - 1; i >= 0; i--) {
            URLConnection scriptCon = scripts.get(i).openConnection();
            try (InputStream stream = scriptCon.getInputStream()) {
                BufferedReader reader = new BufferedReader(new InputStreamReader(stream));
                String line;
                while ((line = reader.readLine()) != null) {
                    Matcher m = patClientId.matcher(line);
                    if (m.find()) {
                        clientId = m.group(1);
                        break scriptLoop;
                    }
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        if (hydrationData == null || clientId == null) {
            launchFailed();
            return;
        }
        JsonNode trackData = null;
        for (JsonNode elem : hydrationData) {
            if ("sound".equals(elem.get("hydratable").textValue())) {
                trackData = elem.get("data");
                break;
            }
        }
        if (trackData == null) {
            launchFailed();
            return;
        }
        int trackId = trackData.get("id").asInt();
        sendSoundcloudLoad(trackId);
        // We can start the resolver now that the clientId is known
        if (!resolverThread.isAlive())
            resolverThread.start();
    }

    void sendSoundcloudLoad(int trackId) throws IOException {
        chromeCast.launchApp(CastActivity.APP_SOUNDCLOUD_PLAYER);
        Map<String, Object> customData = new HashMap<>();
        customData.put("current_index", 0);
        List<Map<String, Object>> queue = new ArrayList<>();
        Map<String, Object> queueItem = new HashMap<>();
        queueItem.put("urn", "soundcloud:tracks:" + String.valueOf(trackId));
        queue.add(queueItem);
        customData.put("queue", queue);
        Media media = new Media("soundcloud:tracks:" + String.valueOf(trackId), "audio/mpeg", null, Media.StreamType.BUFFERED);
        chromeCast.load(media, true, 0d, customData);
    }

    private void launchFailed() {
        System.err.println("Launch failed");
        // TODO Auto-generated method stub

    }

}
