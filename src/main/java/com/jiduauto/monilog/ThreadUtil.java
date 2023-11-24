package com.jiduauto.monilog;


import com.google.common.collect.Sets;
import org.apache.commons.lang3.StringUtils;

import java.util.*;
import java.util.stream.Collectors;

/**
 * @author yp
 * @date 2023/07/26
 */
class ThreadUtil {
    //遍历线程栈找业务类时需要排除掉的包(类)前缀
    private final static Set<String> DEFAULT_EXCLUDE_PKGS = Sets.newHashSet(
            "com.sun.proxy.$Proxy",
            "java", "sun.reflect",
            "org.springframework",
            "org.apache",
            "kong.unirest.apache",
            "com.jiduauto.monilog",
            "com.jiduauto.javakit",
            "okhttp3",
            "org.elasticsearch.client",
            "org.redisson");
    /**
     * 从当前线程栈中，按先后顺序找到指定类的下一个类对应的栈帧，返回找到的第一个栈帧
     */
    static StackTraceElement getNextClassFromStack(Class<?> currentCls, String... excludePkgPrefixs) {
        Set<String> excludes = new HashSet<>(DEFAULT_EXCLUDE_PKGS);
        if (excludePkgPrefixs != null) {
            excludes.addAll(Arrays.stream(excludePkgPrefixs).filter(StringUtils::isNotBlank).collect(Collectors.toList()));
        }

        StackTraceElement[] st = Thread.currentThread().getStackTrace();
        List<StackTraceElement> notExclude = new ArrayList<>();
        outer:
        for (StackTraceElement s : st) {
            String name = getDeclaringClassName(s.getClassName());
            for (String excludePkgPrefix : excludes) {
                if (name.startsWith(excludePkgPrefix)) {
                    continue outer;
                }
            }
            notExclude.add(s);
        }
        StackTraceElement target = null;
        boolean hasFoundTargetClass = false;
        if (currentCls != null) {
            String clsName = currentCls.getCanonicalName();
            for (StackTraceElement s : notExclude) {
                String name = getDeclaringClassName(s.getClassName());
                if (clsName.equals(name)) {
                    hasFoundTargetClass = true;
                    continue;
                }
                if (hasFoundTargetClass) {
                    target = s;
                    break;
                }
            }
        }
        if (target == null) {
            if (!notExclude.isEmpty()) {
                target = notExclude.get(0);
            }
        }
        return target == null ? null : new StackTraceElement(target.getClassName(), target.getMethodName(), target.getFileName(), target.getLineNumber());
    }

    private static String getDeclaringClassName(String name) {
        String declaringCls = null;
        if (name.contains("$")) {
            declaringCls = name.split("\\$")[0];
        }
        if (declaringCls != null) {
            return declaringCls.endsWith(".") ? name : declaringCls;
        }
        return name;
    }
}
