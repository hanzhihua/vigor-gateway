package com.unknown.vigor.gateway.core;

import com.google.common.base.Preconditions;
import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class ComponentId {

    private String className;
    private String tag;
    private Integer index;

    private final static Set<String> componentFullNames = new HashSet<>();

    public ComponentId(String className, String tag, Integer index) {
        Preconditions.checkArgument(StringUtils.isNotEmpty(tag), "tag is empty");
        this.className = className;
        this.tag = tag;
        this.index = index;

        // 保证componentId不重复
        if (componentFullNames.contains(getFullName())) {
            throw new IllegalArgumentException("componentId has already exists");
        }
    }

    public ComponentId(String className, String tag) {
        this.className = className;
        this.tag = tag;
    }

    public ComponentId(String className) {
        this.className = className;
    }

    public Integer getIndex() {
        return index;
    }

    public void setIndex(Integer index) {
        this.index = index;
    }

    public String getFullName() {
        List<String> nameList = new ArrayList<>(3);
        if (StringUtils.isNotEmpty(className)) {
            nameList.add(className);
        }

        if (StringUtils.isNotEmpty(tag)) {
            nameList.add(tag);
        }

        if (index != null) {
            nameList.add(index.toString());
        }
        return StringUtils.join(nameList, "_");
    }

    public String getClassName() {
        return className;
    }

    public void setClassName(String className) {
        this.className = className;
    }

    public String getTag() {
        return tag;
    }

    public void setTag(String tag) {
        this.tag = tag;
    }
}
