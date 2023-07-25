package com.jiduauto.log.web.service;

import com.jiduauto.log.core.enums.LogPoint;

import javax.servlet.http.HttpServletRequestWrapper;

/**
 * @description: 校验http请求来源
 * @author rongjie.yuan
 * @date 2023/7/25 18:52
 */
public interface HttpRequestValidator {
    LogPoint validateRequest(HttpServletRequestWrapper wrapperRequest);
}