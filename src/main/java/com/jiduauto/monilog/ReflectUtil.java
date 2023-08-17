package com.jiduauto.monilog;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.parser.ParserConfig;
import com.alibaba.fastjson.util.TypeUtils;
import com.google.common.collect.Sets;
import org.apache.commons.collections4.CollectionUtils;

import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * @author yepei
 */
class ReflectUtil {
    private static final ConcurrentHashMap<String, Boolean> CACHE = new ConcurrentHashMap<>();

    public static boolean objectHasProperty(Class<?> cls, String propertyName) {
        String clsName = cls.getName();
        try {
            if (CACHE.containsKey(clsName)) {
                return CACHE.get(clsName);
            }
            Field field = cls.getDeclaredField(propertyName);
            CACHE.put(clsName, true);
            return true;
        } catch (NoSuchFieldException e) {
            CACHE.put(clsName, false);
            return false;
        }
    }
    /**
     * 找到方法上的注解，如果找不到则向上找类上的，如果还找不到，则再向上找接口上的
     */
    static <T extends Annotation> T getAnnotation(Class<T> annotationClass, Class<?> methodOwnedClass, Method... methods) {
        assert methods != null && methods.length > 0;
        T annotation = null;
        //方法上的
        for (Method method : methods) {
            annotation = method == null ? null : method.getAnnotation(annotationClass);
            if (annotation != null) {
                break;
            }
        }
        if (annotation != null) {
            return annotation;
        }
        //类上的
        for (Method method : methods) {
            annotation = method == null ? null : method.getDeclaringClass().getAnnotation(annotationClass);
            if (annotation != null) {
                break;
            }
        }
        if (annotation != null) {
            return annotation;
        }
        //接口上的
        for (Method method : methods) {
            annotation = method == null ? null : methodOwnedClass.getAnnotation(annotationClass);
            if (annotation != null) {
                break;
            }
        }
        return annotation;
    }


    static Method getMethodWithoutException(Object service, String methodName, Object[] args) {
        Class<?> ownerCls = service instanceof Class ? (Class<?>) service : service.getClass();
        try {
            List<Method> list = getClsMethods(ownerCls, methodName, args);
            return CollectionUtils.isEmpty(list) ? null : list.get(0);
        } catch (Throwable ignore) {
            return null;
        }
    }

    /**
     * 从当前类以及该类的父类、接口上寻找符合签名的方法(含非public方法)，找到一个就立即返回
     * getDeclaredMethods:获取当前类的所有方法；包括 protected/默认/private 修饰的方法；不包括父类 、接口 public 修饰的方法
     * getMethods：获取当前类或父类或父接口的 public 修饰的字段；包含接口中 default 修饰的方法
     *
     */
    private static List<Method> getClsMethods(Class<?> cls, String methodName, Object[] args) {
        List<Method> results = new ArrayList<>();
        Optional<Method> first = Arrays.stream(cls.getDeclaredMethods()).filter(e -> matchMethod(e, methodName, args)).findFirst();
        if (first.isPresent()) {
            results.add(first.get());
            return results;
        }
        List<Class<?>> superClsList = new ArrayList<>();
        Class<?> superclass = cls.getSuperclass();
        if (superclass != null) {
            superClsList.add(superclass);
        }
        Class<?>[] interfaces = cls.getInterfaces();
        if (interfaces.length > 0) {
            superClsList.addAll(Arrays.asList(interfaces));
        }
        if (CollectionUtils.isEmpty(superClsList)) {
            return null;
        }
        for (Class<?> c : superClsList) {
            List<Method> list = getClsMethods(c, methodName, args);
            if (CollectionUtils.isNotEmpty(list)) {
                results.addAll(list);
                break;
            }
        }
        return results;
    }

    private static boolean matchMethod(Method e, String methodName, Object[] args) {
        if (!e.getName().equals(methodName)) {
            return false;
        }
        int paramCount = args == null ? 0 : args.length;
        if (e.getParameterCount() != paramCount) {
            return false;
        }
        if (paramCount > 0) {
            Type[] parameterTypes = e.getGenericParameterTypes();
            for (int i = 0; i < paramCount; i++) {
                Object arg = args[i];
                Type type = parameterTypes[i];
                if (!compatible(arg, type)) {
                    return false;
                }
            }
        }
        return true;
    }

    // TODO rongjie.yuan  2023/8/17 10:33 可以缓存优化
    @SuppressWarnings("unchecked")
    static <T> T convertType(Object arg, Type requiredType) {
        if (arg == null) {
            return null;
        }
        boolean parameterized = requiredType instanceof ParameterizedType;
        Class<T> paramCls = (Class<T>) (parameterized ? ((ParameterizedType) requiredType).getRawType() : requiredType);
        try {
            //如果是非泛型参数，且类型兼容，则直接返回
            if (!parameterized && paramCls.isAssignableFrom(arg.getClass())) {
                return (T) arg;
            }
            if (requiredType == Class.class && (arg instanceof String)) {
                return (T) Class.forName(arg.toString());
            }
            //类型转换
            return TypeUtils.cast(arg, requiredType, ParserConfig.getGlobalInstance());
        } catch (Exception e) {
            if (arg instanceof Map && !Map.class.isAssignableFrom(paramCls)) {
                //如果入参是Map类型，但目标方法要求的不是Map类型，则按实际要求转换
                return JSON.parseObject(JSON.toJSONString(arg), requiredType);
            }
            if (arg instanceof String) {
                return JSON.parseObject((String) arg, requiredType);
            }
            throw e instanceof RuntimeException ? (RuntimeException) e : new RuntimeException(e);
        }
    }

    @SuppressWarnings("all")
    static <T> T getPropValue(Object obj, String name, boolean ignoreException) {
        try {
            Class cls = obj instanceof Class ? (Class) obj : obj.getClass();
            Field f = getField(cls, name, null);
            if (f == null) {
                throw new NoSuchFieldException("No such field[" + name + "] in " + cls.getCanonicalName());
            }
            f.setAccessible(true);
            return (T) (f.get(obj));
        } catch (Throwable e) {
            if (ignoreException) {
                return null;
            } else {
                throw new RuntimeException(e);
            }
        }
    }

    static <T> T getPropValue(Object obj, String name){
        return getPropValue(obj, name, true);
    }

    @SuppressWarnings("unchecked")
    static <T> T getPropValue(Object obj, String name, T defaultValue){
        Object propValue = getPropValue(obj, name, true);
        if (propValue != null) {
            return (T)propValue;
        }
        return defaultValue;
    }

    @SuppressWarnings("all")
    static void setPropValue(Object obj, String name, Object value, boolean ignoreException) {
        try {
            Class cls = obj instanceof Class ? (Class) obj : obj.getClass();
            Field f = getField(cls, name, null);
            if (f == null) {
                throw new NoSuchFieldException("No such field[" + name + "] in " + cls.getCanonicalName());
            }
            f.setAccessible(true);
            f.set(obj, value);
        } catch (Throwable e) {
            if (ignoreException) {
                //...ignore
            } else {
                throw new RuntimeException(e);
            }
        }
    }


    private static Field getField(Class<?> cls, String name, Class<?> fieldCls) {
        for (Class<?> superClass = cls; superClass != Object.class; superClass = superClass.getSuperclass()) {
            Field[] fields = superClass.getDeclaredFields();
            for (Field f : fields) {
                if (fieldCls != null && !fieldCls.isAssignableFrom(f.getType())) {
                    continue;
                }
                if (f.getName().equals(name)) {
                    return f;
                }
            }
        }
        return null;
    }

    /**
     * 从运行时类型中提取指定类型所声明的泛型参数的实际类型
     */
    private static Type[] getGenericParamType(Class<?> declaredClass, Class<?> thisCls) {
        if (thisCls == null || thisCls == Object.class) {
            return null;
        }
        Type[] interfaces = thisCls.getGenericInterfaces();
        Set<Type> types = Sets.newHashSet(interfaces);
        if (thisCls.getGenericSuperclass() != null && thisCls.getGenericSuperclass() != Object.class) {
            types.add(thisCls.getGenericSuperclass());
        }
        if (types.isEmpty()) {
            Set<Type> list = Sets.newHashSet(thisCls.getInterfaces());
            if (thisCls.getSuperclass() != null && thisCls.getSuperclass() != Object.class) {
                list.add(thisCls.getSuperclass());
            }
            for (Type superType : list) {
                if (superType == Object.class) {
                    continue;
                }
                Type[] genericParamType = getGenericParamType(declaredClass, (Class<?>) superType);
                if (genericParamType != null) {
                    return genericParamType;
                }
            }
            return null;
        }
        for (Type type : types) {
            if (type instanceof ParameterizedType) {
                Type rawType = ((ParameterizedType) type).getRawType();
                if (rawType instanceof Class && declaredClass.isAssignableFrom((Class<?>) rawType)) {
                    return ((ParameterizedType) type).getActualTypeArguments();
                }
            } else {
                Type[] genericParamType = getGenericParamType(declaredClass, (Class<?>) type);
                if (genericParamType != null) {
                    return genericParamType;
                }
            }
        }
        return null;
    }

    /**
     * 找到定义指定方法的原始接口
     */
    static Class<?> getInterfaceByGivenMethod(Method m) {
        Class<?> declaringClass = m.getDeclaringClass();
        if (declaringClass.isInterface()) {
            return declaringClass;
        }
        List<Class<?>> allInterfaces = getAllInterfaces(declaringClass);
        if (CollectionUtils.isEmpty(allInterfaces)) {
            return null;
        }
        for (Class<?> c : allInterfaces) {
            try {
                Method method = c.getMethod(m.getName(), m.getParameterTypes());
                return method.getDeclaringClass();
            } catch (NoSuchMethodException e) {
                //处理泛型实现的情况
                Method[] methods = c.getMethods();
                Set<String> nameList = Arrays.stream(methods).map(Method::getName).collect(Collectors.toSet());
                //匹配方法名
                if (nameList.contains(m.getName())) {
                    //匹配方法参数类型
                    Type[] genericParamType = getGenericParamType(c, declaringClass);
                    if (genericParamType != null) {
                        Set<Type> typeSets = Sets.newHashSet(genericParamType);
                        if (typeSets.containsAll(Arrays.asList(m.getParameterTypes()))) {
                            return c;
                        }
                    }
                }
            }
        }
        return null;
    }

    private static List<Class<?>> getAllInterfaces(Class<?> cls) {
        List<Class<?>> list = new ArrayList<>();
        if (cls.isInterface()) {
            list.add(cls);
        }
        Class<?>[] interfaces = cls.getInterfaces();
        if (interfaces.length > 0) {
            list.addAll(Arrays.asList(interfaces));
        }
        Class<?> superCls = cls.getSuperclass();
        if (superCls == null) {
            return list;
        }
        List<Class<?>> allInterfaces = getAllInterfaces(superCls);
        if (!allInterfaces.isEmpty()) {
            for (Class<?> c : allInterfaces) {
                if (list.contains(c)) {
                    continue;
                }
                list.add(c);
            }
        }
        return list;
    }

    private static boolean compatible(Object arg, Type type) {
        try {
            Object val = convertType(arg, type);
            if (arg != null) {
                return val != null;
            }
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}
