package com.unknown.vigor.gateway.common.event;

import com.google.common.base.Preconditions;
import org.apache.commons.lang3.StringUtils;

public class LogIdEvent {
    private final static int BRIEF_CONTENT_LENGTH = 1_000;

    private String logId;
    private EventMeta meta;
    private byte[] body;

    private int size;

    private Callback callback;

    private long sendTime;

    public LogIdEvent(String logId, EventMeta meta, byte[] body) {
        Preconditions.checkArgument(StringUtils.isNotEmpty(logId), "event logId is empty");
        Preconditions.checkNotNull(meta, "event header is null");
        Preconditions.checkNotNull(body, "event body is null");

        this.logId = logId;
        this.meta = meta;
        this.body = body;
        this.size = computeSize();
    }

    public long getSendTime() { return sendTime; }

    public void setSendTime(long sendTime) { this.sendTime = sendTime; }

    public String getLogId() {
        return logId;
    }

    public EventMeta getMeta() {
        return meta;
    }

    public byte[] getBody() {
        return body;
    }

    public Callback getCallback() {
        return callback;
    }

    public void setCallback(Callback callback) {
        this.callback = callback;
    }

    private int computeSize() {
        int t = 0;

        // get header size
        if (meta != null) {
            t += meta.getSize();
        }

        // get body size
        if (body != null) {
            t += body.length;
        }

        return t;
    }

    public int size() {
        return size;
    }

    @Override
    public String toString() {
        String briefContent;
        if (body.length > BRIEF_CONTENT_LENGTH) {
            briefContent = new String(body, 0, BRIEF_CONTENT_LENGTH);
        } else {
            briefContent = new String(body);
        }

        return "LogIdEvent[logId = " + logId + ", meta = " + meta + ", body = " + briefContent + "]";
    }

}