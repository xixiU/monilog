package com.jiduauto.log.core.util;

import lombok.Getter;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * @author yp
 * @date 2023/07/26
 */
@Getter
public class TagBuilder {
    private final List<String> tags;

    private TagBuilder() {
        this.tags = new ArrayList<>();
    }

    public static TagBuilder of(List<String> tags) {
        return new TagBuilder().add(tags);
    }

    public static TagBuilder of(String[] tags) {
        return of(Arrays.asList(tags));
    }

    public static TagBuilder of(String key, String value) {
        return new TagBuilder().add(key, value);
    }

    public static TagBuilder of(String k1, String v1, String k2, String v2) {
        return new TagBuilder().add(k1, v1).add(k2, v2);
    }

    public static TagBuilder of(String k1, String v1, String k2, String v2, String k3, String v3) {
        return new TagBuilder().add(k1, v1).add(k2, v2).add(k3, v3);
    }

    public TagBuilder add(List<String> tags) {
        if (CollectionUtils.isEmpty(tags)) {
            return this;
        }
        for (int i = 0; i < tags.size() - 1; i++) {
            String key = tags.get(i);
            String value = tags.get(i + 1);
            this.add(key, value);
            i++;
        }
        return this;
    }

    public TagBuilder add(String key, String value) {
        if (StringUtils.isBlank(key) || StringUtils.isBlank(value)) {
            return this;
        }
        this.tags.add(key);
        this.tags.add(value);
        return this;
    }

    public String[] toArray() {
        return tags.toArray(new String[0]);
    }
}
