package com.simon816.castremote;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import su.litvak.chromecast.api.v2.Message;
import su.litvak.chromecast.api.v2.Request;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "type")
@JsonSubTypes({
        @JsonSubTypes.Type(name = "UPDATE_QUEUE", value = SoundcloudMessage.UpdateQueue.class),
})
public abstract class SoundcloudMessage implements Message {

    abstract static class SoundcloudRequest extends SoundcloudMessage implements Request {

        Long requestId;

        @Override
        public final void setRequestId(Long requestId) {
            this.requestId = requestId;
        }

        @Override
        public final Long getRequestId() {
            return requestId;
        }

    }

    public static class UpdateQueue extends SoundcloudRequest {

        @JsonProperty
        private final Map<String, Object> payload;

        public UpdateQueue(int currentIndex, List<String> queueItems) {
            payload = new HashMap<>();
            payload.put("current_index", currentIndex);
            List<Map<String, String>> queue = new ArrayList<>();
            int id = 0;
            for (String trackId : queueItems) {
                Map<String, String> item = new HashMap<>();
                item.put("id", String.valueOf(++id));
                item.put("urn", "soundcloud:tracks:" + trackId);
                queue.add(item);
            }
            payload.put("queue", queue);
        }
    }

}
