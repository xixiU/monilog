package com.jiduauto.monilog;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.ListableBeanFactory;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;

/**
 * Spring(Spring boot)工具封装，包括：
 *
 * <ol>
 *     <li>Spring IOC容器中的bean对象获取</li>
 *     <li>注册和注销Bean</li>
 * </ol>
 *
 * @author loolly
 * @since 5.1.0
 */
@Slf4j
class SpringUtils implements BeanFactoryPostProcessor, ApplicationContextAware {

    /**
     * "@PostConstruct"注解标记的类中，由于ApplicationContext还未加载，导致空指针<br>
     * 因此实现BeanFactoryPostProcessor注入ConfigurableListableBeanFactory实现bean的操作
     */
    private static ConfigurableListableBeanFactory beanFactory;
    /**
     * Spring应用上下文环境
     */
    private static ApplicationContext applicationContext;

    static boolean IS_READY = false;
    @SuppressWarnings("NullableProblems")
    @Override
    public void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory) throws BeansException {
        SpringUtils.beanFactory = beanFactory;
        IS_READY |= applicationContext != null;
    }

    @SuppressWarnings("NullableProblems")
    @Override
    public void setApplicationContext(ApplicationContext applicationContext) {
        SpringUtils.applicationContext = applicationContext;
        IS_READY |= beanFactory != null;
    }

    /**
     * 获取{@link ApplicationContext}
     *
     * @return {@link ApplicationContext}
     */
    static ApplicationContext getApplicationContext() {
        return applicationContext;
    }

    /**
     * 获取{@link ListableBeanFactory}，可能为{@link ConfigurableListableBeanFactory} 或 {@link ApplicationContextAware}
     *
     * @return {@link ListableBeanFactory}
     * @since 5.7.0
     */
    static ListableBeanFactory getBeanFactory() {
        return null == beanFactory ? applicationContext : beanFactory;
    }

    /**
     * 通过class获取Bean
     *
     * @param <T>   Bean类型
     * @param clazz Bean类
     * @return Bean对象
     */
    static <T> T getBean(Class<T> clazz) {
        return getBeanFactory().getBean(clazz);
    }

    static <T> T getBeanWithoutException(Class<T> clazz) {
        try {
            return getBeanFactory() == null ? null : getBeanFactory().getBean(clazz);
        } catch (Exception e) {
            //注意，这里不能再调用innerDebug方法，容易出现死循环
            log.warn(MoniLogUtil.INNER_DEBUG_PREFIX + "SpringUtils.getBean failed", e);
            return null;
        }
    }

    /**
     * 获取配置文件配置项的值
     *
     * @param key 配置项key
     * @return 属性值
     * @since 5.3.3
     */
    public static String getProperty(String key) {
        if (null == applicationContext || StringUtils.isBlank(key)) {
            return null;
        }
        return applicationContext.getEnvironment().getProperty(key);
    }


    static String parseSpELValue(String spel) {
        return applicationContext.getEnvironment().resolvePlaceholders(spel);
    }

    /**
     * 获取应用程序名称
     *
     * @return 应用程序名称
     * @since 5.7.12
     */
    static String getApplicationName() {
        String appName = getProperty("monilog.app-name");
        if (StringUtils.isNotBlank(appName)) {
            return appName;
        }
        String property = getProperty("spring.application.name");
        if (StringUtils.isNotBlank(property)) {
            return property;
        }
        throw new RuntimeException("appName is not set");
    }

    /**
     * 获取当前的环境配置，无配置返回null
     *
     * @return 当前的环境配置
     * @since 5.3.3
     */
    static String[] getActiveProfiles() {
        if (null == applicationContext) {
            return null;
        }
        return applicationContext.getEnvironment().getActiveProfiles();
    }

    /**
     * 获取当前的环境配置，当有多个环境配置时，只获取第一个
     *
     * @return 当前的环境配置
     * @since 5.3.3
     */
    static String getActiveProfile() {
        String[] activeProfiles = getActiveProfiles();
        return (activeProfiles == null || activeProfiles.length == 0) ? null : activeProfiles[0];
    }

    static boolean isTargetEnv(String... envs) {
        String[] activeProfiles = getActiveProfiles();
        if (activeProfiles == null || envs == null || envs.length == 0) {
            return false;
        }
        for (String s : activeProfiles) {
            for (String env : envs) {
                if (StringUtils.containsIgnoreCase(s, env)) {
                    return true;
                }
            }
        }
        return false;
    }
}