package com.simon816.castremote;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import su.litvak.chromecast.api.v2.Message;
import su.litvak.chromecast.api.v2.Request;

import java.util.Map;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "type")
@JsonSubTypes({
        @JsonSubTypes.Type(name = "getMdxSessionStatus", value = YoutubeMessage.GetMdxSessionStatus.class),
        @JsonSubTypes.Type(name = "mdxSessionStatus", value = YoutubeMessage.MdxSessionStatus.class)
})
public abstract class YoutubeMessage implements Message {

    abstract static class YoutubeRequest extends YoutubeMessage implements Request {

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

    public static class YoutubeResponse extends YoutubeMessage {

        public Map<String, Object> data;
    }

    public static class GetMdxSessionStatus extends YoutubeRequest {
    }

    public static class MdxSessionStatus extends YoutubeResponse {

    }

}
