package com.jiduauto.monilog;

import lombok.Setter;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.math.NumberUtils;

import java.util.Collection;

/**
 * 解析结果
 */
final class ParsedInfo<T> {
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

    T getCompatibleResult() {
        return isExpectCompatible() ? getResult() : null;
    }

    /**
     * 根据当前jsonpath解析到的值，计算实际需要的目标结果值
     */
    @SuppressWarnings("unchecked")
    T getResult() {
        Object o = null;
        //简单类型的默认值
        if (value == null) {
            if (isBool(valueClass)) {
                Boolean fitExpect = null == expect || "null".equalsIgnoreCase(expect);//$.error==null
                return (T) fitExpect;
            }
            if (!valueClass.isPrimitive()) {
                return null;
            }
            if (valueClass == int.class || valueClass == long.class || valueClass == short.class || valueClass == float.class || valueClass == double.class || valueClass == char.class || valueClass == byte.class) {
                o = 0;
            } else if (valueClass == boolean.class) {
                o = false;
            }
            return (T) o;
        }
        //valueClass: boolean/Boolean, String, int/Integer
        if (StringUtils.isNotBlank(expect) && isBool(valueClass)) {
            o = StringUtils.equalsIgnoreCase(expect, value.toString());
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
     * 注意，该方法并不是判断结果是否正确，仅仅只是判断返回的数据是否与期望的结果类型兼容。
     * $.success， value=false， 则此时expect=null，应当是兼容的， 结果为false
     * $.success， value=null， 则此时expect=null，应当是兼容的， 结果为true
     * $.success， value=true， 则此时expect=null，应当是兼容的， 结果为true
     * $.errCode=null， value=null， 则此时expect="null"，应当是兼容的， 结果为true
     * $.errCode=success， value="success"， 则此时expect="success"，应当是兼容的， 结果为true
     * $.status=200， value="0"， 则此时expect="200"，应当是兼容的， 结果为false
     * $.isOk()=true， value="false"， 则此时expect="true"，应当也是兼容的， 结果为false
     * $.isOk()=true， value="3"， 则此时expect="true"，此时不是兼容的， 结果为false
     */
    boolean isExpectCompatible() {
        //非双操作数，认为结果有效
        if (StringUtils.isBlank(expect)) {
            return true;
        }
        //双操作数时，结果类型必须是boolean类型。此时要求表达式运算结果类型兼容boolean类型
        if (isBool(valueClass)) {
            //进入此方法时，expect一定不是空串
            if (value == null) {//例：当表达式是：$.errCode=null， value=null， 则此时expect="null"
                return "null".equalsIgnoreCase(expect);
            }
            if (StringUtils.equalsIgnoreCase(expect, value.toString())) {
                return true;//相等则必兼容
            }
            //判断实际值与期望值是否兼容: String<->String, Number<->Number, Boolean<->Boolean
            if (isNumber(value.getClass())) {
                return NumberUtils.isCreatable(expect);
            } else if (isBool(value.getClass())) {
                return Boolean.TRUE.toString().equalsIgnoreCase(expect) || Boolean.FALSE.toString().equalsIgnoreCase(expect);
            } else {
                return true;
            }
        }
        if (isNumber(valueClass)) {
            return NumberUtils.isCreatable(expect);
        }
        return true;
    }

    static boolean isBool(Class<?> valueClass) {
        return valueClass == Boolean.class || valueClass == boolean.class;
    }

    private static boolean isNumber(Class<?> valueClass) {
        return Number.class.isAssignableFrom(valueClass) || valueClass == int.class || valueClass == long.class || valueClass == short.class || valueClass == double.class || valueClass == float.class;
    }
}
