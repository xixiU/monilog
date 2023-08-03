package com.jiduauto.monitor.log;


import com.google.common.collect.Sets;

import java.util.Arrays;
import java.util.Set;

/**
 * @author yp
 * @date 2023/07/26
 */
class ThreadUtil {
    public static StackTraceElement getNextClassFromStack(Class<?> currentCls) {
        return getNextClassFromStack(currentCls, null);
    }

    /**
     * 从当前线程栈中，按先后顺序找到指定类的下一个类对应的栈帧，返回找到的第一个栈帧
     *
     * @param currentCls
     * @param excludePkgPrefixs
     * @return
     */
    public static StackTraceElement getNextClassFromStack(Class<?> currentCls, String... excludePkgPrefixs) {
        if (currentCls == null) {
            return null;
        }
        Set<String> excludes = Sets.newHashSet("com.sun.proxy.$Proxy", "java.lang.reflect", "sun.reflect");
        if (excludePkgPrefixs != null) {
            excludes.addAll(Arrays.asList(excludePkgPrefixs));
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
