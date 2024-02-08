package com.unknown.vigor.gateway.common.event;

public interface Callback {

    void onSuccess(long size);

    void onException(String msg);


    void onLoadFull(String msg);
}
