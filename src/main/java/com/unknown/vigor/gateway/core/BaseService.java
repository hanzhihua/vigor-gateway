package com.unknown.vigor.gateway.core;

import com.unknown.vigor.gateway.core.conf.AbstractProxyConf;
import com.google.common.base.Preconditions;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j
public abstract class BaseService implements LifecycleAware, Configurable {

    protected AbstractProxyConf conf;
    protected LifecycleState state;
    protected ComponentId id;
    private AtomicBoolean isInit = new AtomicBoolean(false);

    @Override
    public void configure(AbstractProxyConf conf) {
        this.conf = conf;
    }

    @Override
    public AbstractProxyConf getConf() {
        return conf;
    }

    @Override
    public void start() throws Exception {
        Preconditions.checkNotNull(conf);
        if (id == null) {
            id = new ComponentId(this.getClass().getSimpleName());
        }

        if (!isInit.compareAndSet(false, true)) {
            throw new RuntimeException(id.getFullName() + " has already been started!");
        }
        state = LifecycleState.START;
        log.info("start component {}", id.getFullName());
    }

    @Override
    public void stop() {
        state = LifecycleState.STOP;
        isInit.set(false);
        log.info("stop component {}", id.getFullName());
    }

    @Override
    public LifecycleState getState() {
        return state;
    }

    @Override
    public void setId(ComponentId id) {
        this.id = id;
    }

    @Override
    public ComponentId getId() {
        return id;
    }
}
