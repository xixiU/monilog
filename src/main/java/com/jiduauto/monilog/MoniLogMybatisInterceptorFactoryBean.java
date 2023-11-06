package com.jiduauto.monilog;

import org.springframework.beans.factory.FactoryBean;

/**
 * moniLog的对象工厂
 * 
 * @author rongjie.yuan
 * @date 2023/11/6 14:34
 */
public class MoniLogMybatisInterceptorFactoryBean implements FactoryBean<MybatisInterceptor> {
    @Override
    public MybatisInterceptor getObject() throws Exception {
        return new MybatisInterceptor();
    }

    @Override
    public Class<?> getObjectType() {
        return MybatisInterceptor.class;
    }
}
