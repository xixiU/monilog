package com.jiduauto.monitor.log.util;


import com.jiduauto.monitor.log.annotation.MonitorLogTags;

/**
 * @author yp
 * @date 2023/07/26
 */
public class ThreadUtil {
    public static StackTraceElement getNextClassFromStack(Class<?> currentCls) {
        return getNextClassFromStack(currentCls, null);
    }

    /**
     * 从当前线程栈中，按先后顺序找到MonitorLogTag出现的第一个栈帧，找不到则返回null
     *
     * @param excludePkgPrefixs
     * @return
     */
    public static StackTraceElement getFirstAnnotationStMonitorTag(String... excludePkgPrefixs) {
        StackTraceElement[] st = Thread.currentThread().getStackTrace();
        Class<?> serviceCls = null;

        for (int i = 0; i < st.length - 1; i++) {
            StackTraceElement currentSte = st[i];
            String name = currentSte.getClassName();
            if (name.contains("$")) {
                name = name.split("\\$")[0];
            }
            if (excludePkgPrefixs != null) {
                for (String excludePkgPrefix : excludePkgPrefixs) {
                    if (name.startsWith(excludePkgPrefix)) {
                        break;
                    }
                }
            }
            try {
                serviceCls = Class.forName(currentSte.getClassName());
                if (serviceCls != null) {
                    MonitorLogTags monitorLogTags = ReflectUtil.getAnnotation(MonitorLogTags.class, serviceCls, serviceCls.getMethods());
                    if (monitorLogTags != null) {
                        new StackTraceElement(currentSte.getClassName(), currentSte.getMethodName(), currentSte.getFileName(), currentSte.getLineNumber());
                    }
                }
            } catch (Exception e) {

            }

        }
        return null;
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
        String clsName = currentCls.getCanonicalName();
        StackTraceElement[] st = Thread.currentThread().getStackTrace();
        StackTraceElement target = null;
        boolean hasFoundTargetClass = false;
        out:
        for (int i = 0; i < st.length - 1; i++) {
            String name = st[i].getClassName();
            if (name.contains("$")) {
                name = name.split("\\$")[0];
            }
            if (clsName.equals(name)) {
                hasFoundTargetClass = true;
                if (excludePkgPrefixs != null) {
                    for (String excludePkgPrefix : excludePkgPrefixs) {
                        if (clsName.startsWith(excludePkgPrefix)) {
                            continue out;
                        }
                    }
                }
                target = st[i + 1];
                break;
            }
            if (hasFoundTargetClass) {
                target = st[i];
                break;
            }
        }
        return target == null ? null : new StackTraceElement(target.getClassName(), target.getMethodName(), target.getFileName(), target.getLineNumber());
    }
}
