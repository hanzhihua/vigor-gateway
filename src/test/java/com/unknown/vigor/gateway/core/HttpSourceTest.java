package com.unknown.vigor.gateway.core;

import com.unknown.vigor.gateway.common.protobuf.StreamEvent;
import io.netty.handler.codec.http.HttpResponseStatus;
import okhttp3.Response;
import org.junit.Assert;
import org.junit.Test;

import java.util.HashMap;
import java.util.Map;

public class HttpSourceTest extends BaseTest{


    @Test
    public void testNoLogId() {
        Map<String, String> headers = new HashMap<>();
        Response response1 = HttpClientUtil.sendPost(URL,
                mockPbEvent("", 1024).toByteArray(),
                headers);
        Assert.assertEquals(StreamEvent.StatusCode.BAD_REQUEST_VALUE, response1.code());
    }

    @Test
    public void testIllegalLogId() {
        Map<String, String> headers = new HashMap<>();
        headers.put("item-count", "200");
        headers.put("ctime", Long.toString(System.currentTimeMillis()));
        String logId1 = "abcdef";
        headers.put("logId", logId1);
        Response response1 = HttpClientUtil.sendPost(URL,
                mockPbEvent(logId1, 1024).toByteArray(),
                headers);
        Assert.assertEquals(StreamEvent.StatusCode.BAD_REQUEST_VALUE, response1.code());

        String logId2 = "000000";
        headers.put("logId", logId1);
        Response response2 = HttpClientUtil.sendPost(URL,
                mockPbEvent(logId2, 1024).toByteArray(),
                headers);
        Assert.assertEquals(HttpResponseStatus.BAD_REQUEST.BAD_REQUEST.code(), response2.code());
    }

    @Test
    public void testTooLargeEvent() {
        Map<String, String> headers = new HashMap<>();
        headers.put("item-count", "200");
        headers.put("ctime", Long.toString(System.currentTimeMillis()));

        // 测试字母
        String logId = "900000";
        headers.put("logId", logId);
        Response response = HttpClientUtil.sendPost(URL,
                mockPbEvent(logId, 1024 * 1024).toByteArray(),
                headers);
        Assert.assertEquals(HttpResponseStatus.TOO_MANY_REQUESTS.code(), response.code());
    }

    @Test
    public void testWrongProtobufEvent() {
        Map<String, String> headers = new HashMap<>();
        headers.put("item-count", "200");
        headers.put("ctime", Long.toString(System.currentTimeMillis()));

        // 测试字母
        String logId = "900000";
        headers.put("logId", logId);
        byte[] data = new byte[1024];
        random.nextBytes(data);
        Response response = HttpClientUtil.sendPost(URL,
                data,
                headers);
        Assert.assertEquals(HttpResponseStatus.BAD_REQUEST.code(), response.code());
    }
}
