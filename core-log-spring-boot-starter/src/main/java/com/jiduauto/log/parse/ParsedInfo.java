package com.jiduauto.log.parse;

import com.jiduauto.log.util.ReflectUtil;
import lombok.Setter;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.math.NumberUtils;

import java.util.Collection;

/**
 * 解析结果
 */
public class ParsedInfo<T> {
    private final Class<T> valueClass;
    /**
     * 通过jsonpath解析到的值
     */
    @Setter
    private Object value;
    /**
     * 期望值
     */
    @Setter
    private String expect;

    public ParsedInfo(Class<T> valueClass) {
        this.valueClass = valueClass;
    }

    /**
     * 拿到目标结果
     *
     * @param targetCls
     * @param <T>
     * @return
     */
    @SuppressWarnings("all")
    public T getResult() {
        Object o = null;
        //简单类型的默认值
        if (value == null) {
            if (!valueClass.isPrimitive()) {
                return null;
            }
            if (valueClass == int.class || valueClass == long.class || valueClass == short.class || valueClass == float.class || valueClass == double.class || valueClass == char.class || valueClass == byte.class) {
                o = 0;
            } else if (valueClass == boolean.class) {
                o = false;
            }
        } else {
            boolean hasExpect = StringUtils.isNotBlank(expect) && (valueClass == Boolean.class || valueClass == boolean.class);
            if (hasExpect) {
                o = value != null && StringUtils.equalsIgnoreCase(expect, value.toString());
            } else {
                if (typeNotMatchForArray(value, valueClass)) {
                    return null;
                }
                try {
                    //防止JSONPath.eval(root, path)返回了荒唐的值。例如 Object cls = JSONPath.eval(new ArrayList<>(), "$.success"), 仍然能返回结果
                    o = ReflectUtil.convertType(value, valueClass);
                } catch (Exception ignore) {
                    return null;
                }
            }
        }
        return (T) o;
    }

    //对于value是数组或list类型，JSONPath存在eval后的值仍然是集合的bug，这里额外处理一下
    private boolean typeNotMatchForArray(Object value, Class<T> targetCls) {
        if (value == null) {
            return false;
        }
        Class<?> valueCls = value.getClass();
        if (valueCls.isArray() || Collection.class.isAssignableFrom(valueCls)) {
            return !targetCls.isArray() && !Collection.class.isAssignableFrom(targetCls);
        }
        return false;
    }

    /**
     * 判断解析后的值是否有效。例如目标结果需要返回boolean类型，但获得的结果不空且不可映射为任何boolean类型
     */
    public boolean isExpectValid() {
        //非双操作数，认为结果有效
        if (StringUtils.isBlank(expect)) {
            return true;
        }
        //双操作数时，结果类型必须是boolean类型。此时要求要么表达式运算结果类型兼容boolean类型
        if (valueClass == Boolean.class || valueClass == boolean.class) {
            return StringUtils.equalsIgnoreCase(expect, Boolean.TRUE.toString())
                    || StringUtils.equalsIgnoreCase(expect, Boolean.FALSE.toString())
                    || (value != null && StringUtils.equalsIgnoreCase(expect, value.toString()));
        }
        if (Number.class.isAssignableFrom(valueClass) || valueClass == int.class || valueClass == long.class || valueClass == short.class || valueClass == double.class || valueClass == float.class) {
            return NumberUtils.isCreatable(expect);
        }
        return true;
    }
}
