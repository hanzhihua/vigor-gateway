package com.unknown.vigor.gateway.core;

public interface LifecycleAware {

    /**
     * start component
     * 
     * @throws Exception
     */
    void start() throws Exception;

    /**
     * stop component
     * 
     * @throws Exception
     */
    void stop();

    /**
     * getState
     * 
     * @return
     */
    LifecycleState getState();
}
