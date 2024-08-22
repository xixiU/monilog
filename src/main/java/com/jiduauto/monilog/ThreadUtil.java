package com.jiduauto.monilog;


import com.google.common.collect.Sets;
import org.apache.commons.lang3.StringUtils;

import java.lang.reflect.Proxy;
import java.util.*;
import java.util.stream.Collectors;

/**
 * @author yp
 * @date 2023/07/26
 */
class ThreadUtil {
    private static final String PROXY_CLS = "com.sun.proxy.$Proxy";
    //遍历线程栈找业务类时需要排除掉的包(类)前缀
    private final static Set<String> DEFAULT_EXCLUDE_PKGS = Sets.newHashSet("java.lang.Thread", "java", "sun.reflect", PROXY_CLS, "org.springframework", "org.apache", "kong.unirest.apache", "com.jiduauto.monilog", "com.jiduauto.javakit", "okhttp3", "org.elasticsearch.client","retrofit2");

    /**
     * 从当前线程栈中，按先后顺序找到指定类的下一个类对应的栈帧，返回找到的第一个栈帧
     */
    static StackTraceElement getNextClassFromStack(Class<?> currentCls, String... excludePkgPrefixs) {
        Set<String> excludes = new HashSet<>(DEFAULT_EXCLUDE_PKGS);
        if (excludePkgPrefixs != null) {
            excludes.addAll(Arrays.stream(excludePkgPrefixs).filter(StringUtils::isNotBlank).collect(Collectors.toList()));
        }
        List<StackTraceElement> filtered = filterStackTrace(excludes);

        StackTraceElement target = null;
        boolean hasFoundTargetClass = false;
        if (currentCls != null) {
            String clsName = currentCls.getCanonicalName();
            for (StackTraceElement s : filtered) {
                if (clsName.equals(s.getClassName())) {
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
            if (!filtered.isEmpty()) {
                target = filtered.get(0);
            }
        }
        return target;
    }

    private static List<StackTraceElement> filterStackTrace(Set<String> excludes) {
        List<StackTraceElement> result = new ArrayList<>();
        StackTraceElement[] origin = Thread.currentThread().getStackTrace();
        for (StackTraceElement s : origin) {
            String name = getDeclaringClassName(s, excludes);
            if (name != null) {
                if (name.equals(s.getClassName())) {
                    result.add(s);
                } else {
                    result.add(new StackTraceElement(name, s.getMethodName(), s.getFileName(), s.getLineNumber()));
                }
            }
        }
        return result;
    }

    private static String getDeclaringClassName(StackTraceElement st, Set<String> excludes) {
        String name = st.getClassName();
        if (name.startsWith(PROXY_CLS)) {
            String targetCls = tryGetProxyTargetCls(name, st.getMethodName(), excludes);
            if (targetCls != null) {
                return targetCls;
            }
        }
        String result = name;
        String declaringCls = null;
        if (name.contains("$")) {
            declaringCls = name.split("\\$")[0];
        }
        if (declaringCls != null && !declaringCls.endsWith(".")) {
            result = declaringCls;
        }
        return isExcluded(excludes, result) ? null : result;
    }

    private static String tryGetProxyTargetCls(String name, String methodName, Set<String> excludes) {
        try {
            Class<?> c = Class.forName(name);
            if (Proxy.isProxyClass(c)) {
                for (Class<?> i : c.getInterfaces()) {
                    String interfaceName = i.getCanonicalName();
                    if (isExcluded(excludes, interfaceName)) {
                        continue;
                    }
                    if (Arrays.stream(i.getDeclaredMethods()).anyMatch(e -> e.getName().equals(methodName))) {
                        return interfaceName;
                    }
                }
            }
            return null;
        } catch (Throwable e) {
            return null;
        }
    }

    private static boolean isExcluded(Set<String> excludes, String target) {
        for (String excludePkgPrefix : excludes) {
            if (target.startsWith(excludePkgPrefix)) {
                return true;
            }
        }
        return false;
    }
}
