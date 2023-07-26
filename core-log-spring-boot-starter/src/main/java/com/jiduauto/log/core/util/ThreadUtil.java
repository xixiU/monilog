package com.jiduauto.log.core.util;

/**
 * @author yp
 * @date 2023/07/26
 */
public class ThreadUtil {
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
        String clsName = currentCls.getCanonicalName();
        StackTraceElement[] st = Thread.currentThread().getStackTrace();
        StackTraceElement target = null;
        out:
        for (int i = 0; i < st.length - 1; i++) {
            String name = st[i].getClassName();
            if (name.contains("$")) {
                name = name.split("\\$")[0];
            }
            if (clsName.equals(name)) {
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
        }
        return target == null ? null : new StackTraceElement(target.getClassName(), target.getMethodName(), target.getFileName(), target.getLineNumber());
    }
}
