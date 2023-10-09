package com.simon816.castremote;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.Map;
import java.util.Map.Entry;
import java.util.function.Function;
import java.util.stream.Collectors;

public class Utils {

    public static String urlEncodeParams(Map<String, String> params) {
        return params.entrySet().stream().map(new Function<Map.Entry<String, String>, String>() {

            @Override
            public String apply(Entry<String, String> e) {
                try {
                    return URLEncoder.encode(e.getKey(), "UTF-8") + "=" + URLEncoder.encode(e.getValue(), "UTF-8");
                } catch (UnsupportedEncodingException ex) {
                    throw new RuntimeException(ex);
                }
            }
        }).collect(Collectors.joining("&"));
    }

}
