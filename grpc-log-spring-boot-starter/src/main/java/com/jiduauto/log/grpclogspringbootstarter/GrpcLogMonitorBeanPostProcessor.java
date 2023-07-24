///*
// * Copyright (c) 2016-2020 Michael Zhang <yidongnan@gmail.com>
// *
// * Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated
// * documentation files (the "Software"), to deal in the Software without restriction, including without limitation the
// * rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to
// * permit persons to whom the Software is furnished to do so, subject to the following conditions:
// *
// * The above copyright notice and this permission notice shall be included in all copies or substantial portions of the
// * Software.
// *
// * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE
// * WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR
// * COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR
// * OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
// */
//
//package com.jiduauto.log.grpclogspringbootstarter;
//
//import com.google.common.collect.Lists;
//import com.jiduauto.javakit.config.exporter.JnsMetricsExporter;
//import io.grpc.Channel;
//import io.grpc.ClientInterceptor;
//import io.grpc.stub.AbstractStub;
//import net.devh.boot.grpc.client.channelfactory.GrpcChannelFactory;
//import net.devh.boot.grpc.client.inject.GrpcClient;
//import net.devh.boot.grpc.client.inject.StubTransformer;
//import net.devh.boot.grpc.client.nameresolver.NameResolverRegistration;
//import net.devh.boot.grpc.client.stubfactory.StubFactory;
//import org.slf4j.Logger;
//import org.slf4j.LoggerFactory;
//import org.springframework.beans.BeanInstantiationException;
//import org.springframework.beans.BeansException;
//import org.springframework.beans.InvalidPropertyException;
//import org.springframework.beans.factory.BeanCreationException;
//import org.springframework.beans.factory.BeanDefinitionStoreException;
//import org.springframework.beans.factory.config.BeanPostProcessor;
//import org.springframework.context.ApplicationContext;
//import org.springframework.core.annotation.AnnotationUtils;
//import org.springframework.util.ReflectionUtils;
//import org.springframework.util.StringUtils;
//
//import java.lang.reflect.Field;
//import java.lang.reflect.Member;
//import java.lang.reflect.Method;
//import java.util.*;
//
//import static java.util.Objects.requireNonNull;
//
///**
// * This {@link BeanPostProcessor} searches for fields and methods in beans that are annotated with {@link GrpcClient}
// * and sets them.
// *
// * @author Michael (yidongnan@gmail.com)
// * @author Daniel Theuke (daniel.theuke@heuboe.de)
// */
//public class GrpcLogMonitorBeanPostProcessor implements BeanPostProcessor {
//
//    @Override
//    public Object postProcessBeforeInitialization(final Object bean, final String beanName) throws BeansException {
//        Class<?> clazz = bean.getClass();
//        do {
//            for (final Field field : clazz.getDeclaredFields()) {
//                final GrpcClient annotation = AnnotationUtils.findAnnotation(field, GrpcClient.class);
//                if (annotation != null) {
//                    Class<?> grpcServiceClass = field.getType().getDeclaringClass();
//                    try {
//                        Field serviceNameField = grpcServiceClass.getField("SERVICE_NAME");
//                        currentServiceName = Arrays.asList((String) serviceNameField.get(null));
//                    } catch (Exception e) {
//                        LOGGER.error("获取服务名称失败, beanName:{}", beanName, e);
//                    }
//                    final String name = resolve(annotation.value());
//                    jnsMetricsExporter.exportDepGrpc(Arrays.asList(name));
//                    ReflectionUtils.makeAccessible(field);
//                    ReflectionUtils.setField(field, bean, processInjectionPoint(field, field.getType(), annotation));
//                }
//            }
//            for (final Method method : clazz.getDeclaredMethods()) {
//                final GrpcClient annotation = AnnotationUtils.findAnnotation(method, GrpcClient.class);
//                if (annotation != null) {
//                    final Class<?>[] paramTypes = method.getParameterTypes();
//                    if (paramTypes.length != 1) {
//                        throw new BeanDefinitionStoreException(
//                                "Method " + method + " doesn't have exactly one parameter.");
//                    }
//                    ReflectionUtils.makeAccessible(method);
//                    ReflectionUtils.invokeMethod(method, bean,
//                            processInjectionPoint(method, paramTypes[0], annotation));
//                }
//            }
//            clazz = clazz.getSuperclass();
//        } while (clazz != null);
//        return bean;
//    }
//
//}
