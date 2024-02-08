package com.unknown.vigor.gateway.core;

import com.unknown.vigor.gateway.core.conf.AbstractProxyConf;

public interface Configurable {

    void configure(AbstractProxyConf conf);

    AbstractProxyConf getConf();

    void setId(ComponentId componentId);

    ComponentId getId();
}
