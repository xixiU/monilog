package com.jiduauto.monilog;


import com.google.common.collect.Sets;
import org.apache.commons.lang3.StringUtils;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * @author yp
 * @date 2023/07/26
 */
class ThreadUtil {
    //遍历线程栈找业务类时需要排除掉的包(类)前缀
    private final static Set<String> DEFAULT_EXCLUDE_PKGS = Sets.newHashSet("com.sun.proxy.$Proxy", "java.lang.reflect", "sun.reflect", "org.springframework", "org.apache", "com.jiduauto.monilog");
    /**
     * 从当前线程栈中，按先后顺序找到指定类的下一个类对应的栈帧，返回找到的第一个栈帧
     */
    static StackTraceElement getNextClassFromStack(Class<?> currentCls, String... excludePkgPrefixs) {
        if (currentCls == null) {
            return null;
        }
        Set<String> excludes = new HashSet<>(DEFAULT_EXCLUDE_PKGS);
        if (excludePkgPrefixs != null) {
            excludes.addAll(Arrays.stream(excludePkgPrefixs).filter(StringUtils::isNotBlank).collect(Collectors.toList()));
        }
        String clsName = currentCls.getCanonicalName();
        StackTraceElement[] st = Thread.currentThread().getStackTrace();
        StackTraceElement target = null;
        boolean hasFoundTargetClass = false;
        out:
        for (int i = 0; i < st.length - 1; i++) {
            String name = getDeclaringClassName(st[i].getClassName());
            if (clsName.equals(name)) {
                hasFoundTargetClass = true;
                continue;
            } else if (hasFoundTargetClass) {
                for (String excludePkgPrefix : excludes) {
                    if (name.startsWith(excludePkgPrefix)) {
                        continue out;
                    }
                }
            }
            if (hasFoundTargetClass) {
                target = st[i];
                break;
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
