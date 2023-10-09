package com.simon816.castremote;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.CharBuffer;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.UUID;

// based on https://github.com/ur1katz/casttube/blob/master/casttube/YouTubeSession.py
// Some functions from https://github.com/henriquekano/youtube-lounge-api/wiki/Annex#remote-commands
// Also useful: https://github.com/henriquekano/youtube-lounge-api/wiki/Finally-some-api!
public class YouTubeSession {

    public static interface YTEventListener {

        void onEvent(YouTubeSession session, EventType eventType);
    }

    public enum EventType {
        VOLUME, PLAYLIST, NOW_PLAYING, ADVERT

    }

    public static class YTDevice {

        public final String app;
        public final String name;
        public final String id;
        public final String type;

        public YTDevice(@JsonProperty("app") String app, @JsonProperty("name") String name, @JsonProperty("id") String id,
                @JsonProperty("type") String type) {
            this.app = app;
            this.name = name;
            this.id = id;
            this.type = type;
        }

    }

    public static class YTVideo {

        public final String videoId;
        public final String title;
        public final String length;
        public final String thumbUrl;

        public YTVideo(String videoId, String title, String length, String thumbUrl) {
            this.videoId = videoId;
            this.title = title;
            this.length = length;
            this.thumbUrl = thumbUrl;
        }

    }

    public static class YTEvent {

        public final int eventId;
        public final String eventName;
        public final List<JsonNode> args;

        public YTEvent(int eventId, String eventName, List<JsonNode> args) {
            this.eventId = eventId;
            this.eventName = eventName;
            this.args = args;
        }

        JsonNode firstArg(String key) {
            return this.args.get(0).get(key);
        }

        @Override
        public String toString() {
            return "YTEvent[" + eventId + "](" + eventName + ") " + args;
        }
    }

    public static class YTState {

        public static final int STATE_IDLE = 0;
        public static final int STATE_PLAYING_2 = 1;
        public static final int STATE_PAUSED = 2; // confirmed certain,
                                                  // nowPlaying
        public static final int STATE_PLAYING = 3;

        public static final int AD_STATE_AD_PLAYING = 1;
        public static final int AD_STATE_AD_PLAYING_2 = -1;
        public static final int AD_STATE_NO_ADD = 0;

        String playlistId;
        YTDevice screenDevice;
        boolean muted;
        int volume;
        boolean hasPrevious;
        boolean hasNext;
        boolean shuffleEnabled;
        boolean loopEnabled;
        int playState;
        String currentVideoId;
        int currentPlaylistIndex = -1;
        float seekEndTime = -1;
        float currentTime = -1;
        int adState = 0;
        boolean canSkipAd;

        public int getPlayState() {
            return playState;
        }

        public int getCurrentPlaylistIndex() {
            return currentPlaylistIndex;
        }

        public float getCurrentTime() {
            return currentTime;
        }

        public float getSeekEndTime() {
            return seekEndTime;
        }

        public int getVolume() {
            return volume;
        }

        public String getCurrentVideoId() {
            return currentVideoId;
        }

        void setDevices(List<YTDevice> devices) {
            for (YTDevice device : devices) {
                if ("LOUNGE_SCREEN".equals(device.type)) {
                    screenDevice = device;
                }
            }
        }

        void clearCurrentPlaying() {
            this.currentVideoId = null;
            this.currentPlaylistIndex = -1;
            this.seekEndTime = -1;
            this.currentTime = -1;
        }

    }

    static final ObjectMapper mapper = new ObjectMapper();

    public static interface OutputParser<T> {

        public static final OutputParser<JsonNode> JSON = new OutputParser<JsonNode>() {

            @Override
            public JsonNode read(InputStream stream) throws IOException {
                return mapper.readTree(stream);
            }
        };

        public static final OutputParser<JsonNode> JSON_LEN_PREFIX = new OutputParser<JsonNode>() {

            @Override
            public JsonNode read(InputStream stream) throws IOException {
                BufferedReader reader = new BufferedReader(new InputStreamReader(stream));
                Integer.parseInt(reader.readLine());
                return mapper.readTree(reader);
            }
        };

        public static final OutputParser<List<YTEvent>> EVENT_STREAM = new OutputParser<List<YTEvent>>() {

            @Override
            public List<YTEvent> read(InputStream stream) throws IOException {
                BufferedReader reader = new BufferedReader(new InputStreamReader(stream));
                List<YTEvent> events = new ArrayList<>();
                Integer.parseInt(reader.readLine());
                JsonNode tree = mapper.readTree(reader);
                for (JsonNode event : tree) {
                    int eventId = event.get(0).asInt();
                    JsonNode eventData = event.get(1);
                    Iterator<JsonNode> iter = eventData.elements();
                    String eventName = iter.next().asText();
                    final List<JsonNode> args = new ArrayList<JsonNode>();
                    while (iter.hasNext()) {
                        args.add(iter.next());
                    }
                    events.add(new YTEvent(eventId, eventName, args));
                }
                return events;
            }
        };

        public static final OutputParser<String> TEXT = new OutputParser<String>() {

            @Override
            public String read(InputStream stream) throws IOException {
                InputStreamReader reader = new InputStreamReader(stream);
                CharBuffer buffer = CharBuffer.allocate(10000);
                while (reader.read(buffer) > 0) {
                }
                buffer.position(0);
                return buffer.toString();
            }
        };

        T read(InputStream stream) throws IOException;
    }

    private static final String INNERTUBE_API_KEY = "AIzaSyAO_FJ2SlqU8Q4STEHLGCilw_Y9_11qcW8";
    private static final URL LOUNGE_TOKEN_URL;
    private static final URL BIND_URL;
    private static final URL INNERTUBE_URL;
    private static final URL CREATE_PAIR_URL;

    static {
        try {
            LOUNGE_TOKEN_URL = new URL("https://www.youtube.com/api/lounge/pairing/get_lounge_token_batch");
            BIND_URL = new URL("https://www.youtube.com/api/lounge/bc/bind");
            INNERTUBE_URL = new URL("https://www.youtube.com/youtubei/v1/next");
            CREATE_PAIR_URL = new URL("https://www.youtube.com/api/lounge/pairing/get_pairing_code?ctx=pair");
        } catch (MalformedURLException e) {
            throw new RuntimeException(e);
        }
    }

    private final String screenId;
    private boolean connected;
    private String loungeToken;
    private int requestId;
    private int offset;
    private String sid;
    private String gSessionId;
    private String googEomVisitorId;
    private int lastEventId;
    public final YTState state = new YTState();
    private final String deviceName;
    private final String deviceId;
    private final Set<YTEventListener> listeners = new HashSet<>();

    public YouTubeSession(String screenId, String deviceName) {
        this(screenId, deviceName, UUID.randomUUID().toString());
    }

    public YouTubeSession(String screenId, String deviceName, String deviceId) {
        this.screenId = screenId;
        this.deviceName = deviceName;
        this.deviceId = deviceId;
    }

    public void addEventListener(YTEventListener listener) {
        listeners.add(listener);
    }

    public void removeEventListener(YTEventListener listener) {
        listeners.remove(listener);
    }

    public void addToQueue(String videoId) throws IOException {
        this.connect();
        // If nothing is playing, need to start a new queue
        if (this.state.currentPlaylistIndex == -1) {
            playNow(videoId);
        } else {
            doAction("addVideo", videoId);
        }
    }

    public void playNext(String videoId) throws IOException {
        this.connect();
        doAction("insertVideo", videoId);
    }

    public void removeVideo(String videoId) throws IOException {
        this.connect();
        doAction("removeVideo", videoId);
    }

    public void stopVideo() throws IOException {
        this.connect();
        // TODO: test
        doActions(new YTCommand("stopVideo"));
    }

    public void nextVideo() throws IOException {
        this.connect();
        doActions(new YTCommand("next"));
    }

    public void pauseVideo() throws IOException {
        this.connect();
        doActions(new YTCommand("pause"));
    }

    public void resumeVideo() throws IOException {
        this.connect();
        doActions(new YTCommand("play"));
    }

    public void previousVideo() throws IOException {
        this.connect();
        doActions(new YTCommand("previous"));
    }

    public void clearPlaylist() throws IOException {
        this.connect();
        doActions(new YTCommand("clearPlaylist"));
    }

    public void setVolume(int volume, boolean mute) throws IOException {
        this.connect();
        Map<String, String> cmdArgs = new HashMap<>();
        cmdArgs.put("volume", String.valueOf(volume));
        cmdArgs.put("muted", String.valueOf(mute));
        doActions(new YTCommand("setVolume", cmdArgs));
    }

    public void seek(float seconds) throws IOException {
        this.connect();
        Map<String, String> cmdArgs = new HashMap<>();
        cmdArgs.put("newTime", String.valueOf(seconds));
        doActions(new YTCommand("seekTo", cmdArgs));
    }

    public void skipAd() throws IOException {
        this.connect();
        doActions(new YTCommand("skipAd", null));
    }

    public void setAutoplay(boolean enabled) throws IOException {
        this.connect();
        Map<String, String> cmdArgs = new HashMap<>();
        cmdArgs.put("autoplayMode", enabled ? "ENABLED" : "DISABLED");
        doActions(new YTCommand("setAutoplayMode", cmdArgs));
    }

    public void nowPlayingPlaylist() throws IOException {
        this.connect();
        doActions(new YTCommand("nowPlayingPlaylist"));
    }

    public void getNowPlaying() throws IOException {
        this.connect();
        doActions(new YTCommand("getNowPlaying"));
    }

    public void playNow(String videoId) throws IOException {
        setPlaylist("", videoId, -1);
    }

    // you can skip to arbitrary videos within the playlist with this
    public void jumpToVideoFromPlaylist(String videoId) throws IOException {
        if (this.state.playlistId == null)
            return;
        setPlaylist(this.state.playlistId, videoId, -1);
    }

    public void setPlaylist(String listId, String videoId, int index) throws IOException {
        this.connect();
        Map<String, String> cmdArgs = new HashMap<>();
        cmdArgs.put("listId", listId);
        cmdArgs.put("currentTime", "0");
        cmdArgs.put("currentIndex", String.valueOf(index));
        cmdArgs.put("audioOnly", "false");
        cmdArgs.put("videoId", videoId);
        doActions(new YTCommand("setPlaylist", cmdArgs));
    }

    public void terminate() throws IOException {
        this.connect();
        Map<String, String> params = new HashMap<>();
        params.put("RID", String.valueOf(this.requestId));
        params.put("VER", "8");
        params.put("CVER", "1");
        params.put("SID", this.sid);
        params.put("gsessionid", this.gSessionId);

        Map<String, String> postParams = new HashMap<>();
        postParams.put("TYPE", "terminate");
        postParams.put("clientDisconnectReason", "MDX_SESSION_DISCONNECT_REASON_DISCONNECTED_BY_USER");
        byte[] data = Utils.urlEncodeParams(postParams).getBytes(Charset.forName("UTF-8"));
        doPost(BIND_URL, data, params, OutputParser.TEXT);
        this.disconnect();
    }

    public List<YTVideo> getQueue() throws IOException {
        this.connect();
        List<YTVideo> queue = new ArrayList<>();
        if (this.state.playlistId == null) {
            return queue;
        }
        this.innertubeInit();
        ObjectNode payload = mapper.createObjectNode();
        payload.put("playlistId", this.state.playlistId);
        JsonNode node = innertubeAPI(payload);
        JsonNode playlistContainer = node.get("contents").get("twoColumnWatchNextResults").get("playlist");
        if (playlistContainer == null) {
            return queue;
        }
        JsonNode playlist = playlistContainer.get("playlist").get("contents");
        if (!playlist.isArray()) {
            throw new IOException("Unknown JSON content");
        }
        for (JsonNode jsonNode : playlist) {
            JsonNode itemNode = jsonNode.get("playlistPanelVideoRenderer");
            String title = itemNode.get("title").get("simpleText").asText();
            String thumbUrl = itemNode.get("thumbnail").get("thumbnails").get(0).asText();
            String length = itemNode.get("lengthText").get("simpleText").asText();
            String videoId = itemNode.get("videoId").asText();
            queue.add(new YTVideo(videoId, title, length, thumbUrl));
        }
        return queue;
    }

    public String createPairCode() throws IOException {
        this.connect();
        Map<String, String> params = new HashMap<>();
        params.put("access_type", "permanent");
        params.put("app", this.state.screenDevice.app);
        params.put("lounge_token", this.loungeToken);
        params.put("screen_id", this.screenId);
        params.put("screen_name", this.state.screenDevice.name);
        params.put("device_id", this.state.screenDevice.id);

        byte[] data = Utils.urlEncodeParams(params).getBytes(Charset.forName("UTF-8"));

        HttpURLConnection connection = (HttpURLConnection) CREATE_PAIR_URL.openConnection();
        connection.addRequestProperty("Origin", "https://www.youtube.com/");
        connection.addRequestProperty("Content-Type", "application/x-www-form-urlencoded");
        if (this.loungeToken != null) {
            connection.addRequestProperty("X-YouTube-LoungeId-Token", loungeToken);
        }
        connection.setRequestMethod("POST");
        connection.setDoOutput(true);
        try (OutputStream os = connection.getOutputStream()) {
            os.write(data);
        }
        try (InputStream stream = connection.getInputStream()) {
            return OutputParser.TEXT.read(stream);
        } catch (IOException e) {
            try (InputStream stream = connection.getErrorStream()) {
                System.err.println(OutputParser.TEXT.read(stream));
            }
            throw e;
        }
    }

    // Returns whether we have caught up with the event stream (i.e. no new
    // events)
    public boolean pollEvents() throws IOException {
        this.connect();
        Map<String, String> params = new HashMap<>();
        params.put("mdxVersion", "3");
        params.put("RID", "rpc");
        params.put("VER", "8");
        params.put("TYPE", "xmlhttp");
        params.put("SID", this.sid);
        params.put("gsessionid", this.gSessionId);
        params.put("loungeIdToken", this.loungeToken);
        params.put("v", "2");
        params.put("t", "1");
        params.put("AID", String.valueOf(this.lastEventId));
        params.put("CI", "1");
        URL url = this.withParams(BIND_URL, params);
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestMethod("GET");
        try (InputStream stream = connection.getInputStream()) {
            List<YTEvent> events = OutputParser.EVENT_STREAM.read(stream);
            updateFromEvents(events);
            return events.size() == 0 || events.get(events.size() - 1).eventName.equals("noop");
        } catch (IOException e) {
            try (InputStream stream = connection.getErrorStream()) {
                System.err.println(OutputParser.TEXT.read(stream));
            }
            throw e;
        }
    }

    public void pollAllEvents() throws IOException {
        boolean finished = false;
        while (!finished)
            finished = pollEvents();
    }

    private void innertubeInit() throws IOException {
        if (this.googEomVisitorId == null) {
            JsonNode node = innertubeAPI(null);
            googEomVisitorId = node.get("responseContext").get("visitorData").asText();
        }
    }

    // Useful reference: https://github.com/SuspiciousLookingOwl/youtubei
    private JsonNode innertubeAPI(ObjectNode inputData) throws IOException {
        Map<String, String> params = new HashMap<>();
        params.put("key", INNERTUBE_API_KEY);
        params.put("prettyPrint", "false");
        URL url = this.withParams(INNERTUBE_URL, params);
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        if (this.googEomVisitorId != null) {
            connection.addRequestProperty("x-goog-eom-visitor-id", this.googEomVisitorId);
        }
        connection.addRequestProperty("Content-Type", "application/json");
        connection.setRequestMethod("POST");
        connection.setDoOutput(true);
        ObjectNode payload = mapper.createObjectNode();
        if (inputData != null)
            payload.setAll(inputData);
        ObjectNode contextNode = mapper.createObjectNode();
        ObjectNode clientNode = mapper.createObjectNode();
        clientNode.put("clientName", "WEB");
        clientNode.put("clientVersion", "2.20230810.05.00");
        clientNode.put("platform", "DESKTOP");
        contextNode.set("client", clientNode);
        payload.set("context", contextNode);
        byte[] data = mapper.writeValueAsBytes(payload);
        try (OutputStream os = connection.getOutputStream()) {
            os.write(data);
        }
        try (InputStream stream = connection.getInputStream()) {
            return OutputParser.JSON.read(stream);
        } catch (IOException e) {
            try (InputStream stream = connection.getErrorStream()) {
                System.err.println(OutputParser.TEXT.read(stream));
            }
            throw e;
        }
    }

    private void doAction(String action, String videoId) throws IOException {
        Map<String, String> cmdArgs = new HashMap<>();
        cmdArgs.put("videoId", videoId);
        doActions(new YTCommand(action, cmdArgs));
    }

    private void doActions(YTCommand... commands) throws IOException {
        Map<String, String> params = new HashMap<>();
        params.put("RID", String.valueOf(this.requestId));
        params.put("VER", "8");
        params.put("CVER", "1");
        params.put("SID", this.sid);
        params.put("gsessionid", this.gSessionId);
        byte[] data = encodeCommands(commands).getBytes(Charset.forName("UTF-8"));
        doPost(BIND_URL, data, params, OutputParser.JSON_LEN_PREFIX);
    }

    private static class YTCommand {

        public final String action;
        public final Map<String, String> params;

        public YTCommand(String action) {
            this.action = action;
            this.params = null;
        }

        public YTCommand(String action, Map<String, String> args) {
            this.action = action;
            this.params = args;
        }

    }

    private String encodeCommands(YTCommand... commands) {
        final Map<String, String> params = new HashMap<>();
        params.put("count", String.valueOf(commands.length));
        params.put("ofs", String.valueOf(this.offset));
        for (int i = 0; i < commands.length; i++) {
            YTCommand cmd = commands[i];
            params.put("req" + i + "__sc", cmd.action);
            if (cmd.params != null)
                for (Entry<String, String> entry : cmd.params.entrySet()) {
                    params.put("req" + i + "_" + entry.getKey(), entry.getValue());
                }
        }
        return Utils.urlEncodeParams(params);
    }

    private void connect() throws IOException {
        if (this.connected) {
            return;
        }
        JsonNode tree = doPost(LOUNGE_TOKEN_URL, ("screen_ids=" + URLEncoder.encode(this.screenId, "UTF-8")).getBytes(Charset.forName("UTF-8")),
                OutputParser.JSON);
        this.loungeToken = tree.get("screens").get(0).get("loungeToken").asText();
        Map<String, String> params = new HashMap<>();
        params.put("RID", String.valueOf(this.requestId = 0));
        params.put("VER", "8");
        params.put("CVER", "1");
        this.lastEventId = 0;
        Map<String, String> postData = new HashMap<>();
        postData.put("device", "REMOTE_CONTROL");
        postData.put("id", this.deviceId);
        postData.put("name", this.deviceName);
        postData.put("mdx-version", "3");
        postData.put("pairing_type", "cast");
        postData.put("app", "android-phone-13.14.55");
        byte[] data = Utils.urlEncodeParams(postData).getBytes(Charset.forName("UTF-8"));
        List<YTEvent> events = doPost(BIND_URL, data, params, OutputParser.EVENT_STREAM);
        this.connected = true;
        updateFromEvents(events);
    }

    private void updateFromEvents(List<YTEvent> events) {
        for (YTEvent event : events) {
            System.out.println(event);
            this.lastEventId = event.eventId;
            switch (event.eventName) {
                case "c":
                    this.sid = event.args.get(0).textValue();
                    this.offset = 0;
                    break;
                case "S":
                    this.gSessionId = event.args.get(0).textValue();
                    break;
                case "loungeStatus": {
                    try {
                        JsonNode devices = mapper.readTree(event.firstArg("devices").textValue());
                        List<YTDevice> deviceList = new ArrayList<>();
                        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
                        for (JsonNode device : devices) {
                            deviceList.add(mapper.treeToValue(device, YTDevice.class));
                        }
                        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, true);
                        state.setDevices(deviceList);
                    } catch (JsonProcessingException e) {
                        System.err.println("Cannot parse devices string:");
                        e.printStackTrace();
                    }
                    break;
                }
                case "playlistModified":
                    this.state.playlistId = event.firstArg("listId").textValue();
                    fireChangeEvent(EventType.PLAYLIST);
                    break;
                case "nowPlaying":
                    // maybe useful - mdxExpandedReceiverVideoIdList is a csv of
                    // video ids in the queue
                    this.state.playlistId = event.firstArg("listId").textValue();
                    if (event.firstArg("state") != null) {
                        this.state.playState = Integer.parseInt(event.firstArg("state").textValue());
                        if (this.state.playState != YTState.STATE_IDLE) {
                            this.state.currentVideoId = event.firstArg("videoId").textValue();
                            this.state.currentPlaylistIndex = Integer.parseInt(event.firstArg("currentIndex").textValue());
                            this.state.seekEndTime = Float.parseFloat(event.firstArg("seekableEndTime").textValue());
                            this.state.currentTime = Float.parseFloat(event.firstArg("currentTime").textValue());
                            fireChangeEvent(EventType.NOW_PLAYING);
                            break;
                        }
                    }
                    this.state.clearCurrentPlaying();
                    fireChangeEvent(EventType.NOW_PLAYING);
                    break;
                case "onVolumeChanged":
                    this.state.muted = Boolean.parseBoolean(event.firstArg("muted").textValue());
                    this.state.volume = Integer.parseInt(event.firstArg("volume").textValue());
                    this.fireChangeEvent(EventType.VOLUME);
                    break;
                case "onHasPreviousNextChanged":
                    this.state.hasPrevious = Boolean.parseBoolean(event.firstArg("hasPrevious").textValue());
                    this.state.hasNext = Boolean.parseBoolean(event.firstArg("hasNext").textValue());
                    fireChangeEvent(EventType.NOW_PLAYING);
                    break;
                case "onPlaylistModeChanged":
                    this.state.shuffleEnabled = Boolean.parseBoolean(event.firstArg("shuffleEnabled").textValue());
                    this.state.loopEnabled = Boolean.parseBoolean(event.firstArg("loopEnabled").textValue());
                    fireChangeEvent(EventType.PLAYLIST);
                    break;
                case "onStateChange":
                    // TODO: This state may be different to the nowPlaying one
                    this.state.playState = Integer.parseInt(event.firstArg("state").textValue());
                    if (this.state.playState == YTState.STATE_IDLE) {
                        this.state.clearCurrentPlaying();
                    }
                    fireChangeEvent(EventType.NOW_PLAYING);
                    break;
                case "loungeScreenDisconnected":
                    // TODO
                    break;
                case "onAdStateChange":
                    this.state.adState = Integer.parseInt(event.firstArg("adState").textValue());
                case "adPlaying":
                    this.state.canSkipAd = Boolean.parseBoolean(event.firstArg("isSkipEnabled").textValue());
                    fireChangeEvent(EventType.ADVERT);
                    break;
            }
        }
    }

    private void fireChangeEvent(EventType eventType) {
        for (YTEventListener listener : listeners) {
            listener.onEvent(this, eventType);
        }
    }

    private void disconnect() {
        connected = false;
        loungeToken = null;
        requestId = 0;
    }

    private <T> T doPost(URL url, byte[] data, OutputParser<T> outParser) throws IOException {
        return doPost(url, data, null, outParser);
    }

    private URL withParams(URL url, Map<String, String> params) throws MalformedURLException {
        if (params != null) {
            url = new URL(url.toExternalForm() + "?" + Utils.urlEncodeParams(params));
        }
        return url;
    }

    private <T> T doPost(URL url, byte[] data, Map<String, String> params, OutputParser<T> outParser) throws IOException {
        url = this.withParams(url, params);
        System.out.println(url);
        System.out.println(new String(data, "UTF-8"));
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.addRequestProperty("Origin", "https://www.youtube.com/");
        connection.addRequestProperty("Content-Type", "application/x-www-form-urlencoded");
        if (this.loungeToken != null) {
            connection.addRequestProperty("X-YouTube-LoungeId-Token", loungeToken);
        }
        connection.setRequestMethod("POST");
        connection.setDoOutput(true);
        try (OutputStream os = connection.getOutputStream()) {
            os.write(data);
        }
        try (InputStream stream = connection.getInputStream()) {
            requestId++;
            this.offset++;
            return outParser.read(stream);
        } catch (IOException e) {
            try (InputStream stream = connection.getErrorStream()) {
                if (stream != null)
                    System.err.println(OutputParser.TEXT.read(stream));
            }
            throw e;
        }
    }

}
