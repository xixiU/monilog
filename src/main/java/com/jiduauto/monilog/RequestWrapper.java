package com.jiduauto.monilog;

import lombok.extern.slf4j.Slf4j;

import javax.servlet.ReadListener;
import javax.servlet.ServletInputStream;
import javax.servlet.ServletRequest;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletRequestWrapper;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;


@Slf4j
class RequestWrapper extends HttpServletRequestWrapper {
    private byte[] body;
    private Map<String, String[]> modifiableParameters;

    public RequestWrapper(HttpServletRequest request) {
        super(request);
        String sessionStream = getBodyString(request);
        body = sessionStream.getBytes(StandardCharsets.UTF_8);
        modifiableParameters = new HashMap<>(request.getParameterMap());
    }

    public String getBodyString() {
        return new String(body, StandardCharsets.UTF_8);
    }

    /**
     * 获取请求Body
     */
    public String getBodyString(ServletRequest request) {
        StringBuilder sb = new StringBuilder();
        try (InputStream inputStream = cloneInputStream(request.getInputStream());
             BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line);
            }
        } catch (IOException e) {
            MoniLogUtil.innerDebug("RequestWrapper.getBodyString error", e);
        }
        return sb.toString();
    }

    /**
     * 复制输入流
     */
    // TODO rongjie.yuan  2023/9/22 15:55 这里读取流可能会有会有问题
    public InputStream cloneInputStream(ServletInputStream inputStream) {
        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        byte[] buffer = new byte[1024];
        int len;
        try {
            while ((len = inputStream.read(buffer)) > -1) {
                byteArrayOutputStream.write(buffer, 0, len);
            }
            byteArrayOutputStream.flush();
        } catch (IOException e) {
            // 大参数太多会出现Unexpected EOF，直接吞掉
            // org.apache.catalina.connector.ClientAbortException: java.io.EOFException:
            // Unexpected EOF read on the socket at org.apache.catalina.connector.InputBuffer.realReadBytes(InputBuffer.java:340)
            MoniLogUtil.innerDebug("RequestWrapper.cloneInputStream error:{}", e);
        }
        return new ByteArrayInputStream(byteArrayOutputStream.toByteArray());
    }

    @Override
    public BufferedReader getReader() {
        return new BufferedReader(new InputStreamReader(getInputStream()));
    }

    @Override
    public ServletInputStream getInputStream() {

        ByteArrayInputStream bis = new ByteArrayInputStream(body);
        return new ServletInputStream() {
            @Override
            public int read() {
                return bis.read();
            }
            @Override
            public boolean isFinished() {
                return false;
            }

            @Override
            public boolean isReady() {
                return false;
            }
            @Override
            public void setReadListener(ReadListener readListener) {
            }
        };
    }

    /**
     * 赋值给body字段
     *
     * @param body 赋值信息
     */
    public void setBody(String body) {
        this.body = body.getBytes(StandardCharsets.UTF_8);
    }

    public void setParameter(String key, Object value) {
        Map<String, String[]> additionalParams = new TreeMap<>(modifiableParameters);
        additionalParams.put(key, new String[]{value.toString()});
        this.modifiableParameters = additionalParams;
    }

    @Override
    public String getParameter(final String name) {
        String[] strings = getParameterMap().get(name);
        if (strings != null) {
            return strings[0];
        }
        return super.getParameter(name);
    }

    @Override
    public Map<String, String[]> getParameterMap() {
        if (modifiableParameters == null) {
            modifiableParameters = new TreeMap<>();
        }
        return Collections.unmodifiableMap(modifiableParameters);
    }

    @Override
    public Enumeration<String> getParameterNames() {
        return Collections.enumeration(getParameterMap().keySet());
    }

    @Override
    public String[] getParameterValues(final String name) {
        return getParameterMap().get(name);
    }

}
