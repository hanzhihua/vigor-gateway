package com.unknown.vigor.gateway.core;

import lombok.extern.slf4j.Slf4j;
import okhttp3.*;

import java.util.Map;

import static java.util.concurrent.TimeUnit.MINUTES;
import static java.util.concurrent.TimeUnit.SECONDS;
@Slf4j
public class HttpClientUtil {

    private static final MediaType TEXT = MediaType.get("text/plain; charset=utf-8");

    private static OkHttpClient client;

    static {
        client = new OkHttpClient().newBuilder()
                .connectTimeout(5, SECONDS)
                .readTimeout(5, SECONDS)
                .writeTimeout(5, SECONDS)
                .connectionPool(new ConnectionPool(100, 5, MINUTES))
                .hostnameVerifier((s, sslSession) -> true)
                .retryOnConnectionFailure(true)
                .build();
    }

    public static Response sendPost(String uri, byte[] content, Map<String, String> headers) {
        Response response = null;

        if (content != null) {
            RequestBody body = RequestBody.create(content, TEXT);
            Request.Builder builder = new Request.Builder()
                    .url(uri)
                    .post(body);

            for (Map.Entry<String, String> entry : headers.entrySet()) {
                builder.addHeader(entry.getKey(), entry.getValue());
            }

            try {
                response = client.newCall(builder.build()).execute();
                log.info("send http post finished, response={}", response.code());
                return response;
            } catch (Exception e) {
                log.error("send http post error", e);
            }
        }

        return response;
    }
}
