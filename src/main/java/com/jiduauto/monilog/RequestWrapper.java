package com.jiduauto.monilog;

import lombok.extern.slf4j.Slf4j;

import javax.servlet.ReadListener;
import javax.servlet.ServletInputStream;
import javax.servlet.ServletRequest;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletRequestWrapper;
import java.io.*;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.*;


@Slf4j
class RequestWrapper extends HttpServletRequestWrapper {
    private static final Charset CHARSET = StandardCharsets.UTF_8;
    private byte[] body;

    public RequestWrapper(HttpServletRequest request) throws IOException {
        super(request);
        String sessionStream = getBodyString(request);
        body = sessionStream.getBytes(CHARSET);
    }

    public String getBodyString() {
        return StringUtil.encodeByteArray(body, CHARSET, "Binary data");
    }

    /**
     * 获取请求Body
     */
    public String getBodyString(ServletRequest request) throws IOException {
        StringBuilder sb = new StringBuilder();
        try (InputStream inputStream = cloneInputStream(request.getInputStream());
             BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, CHARSET))) {
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line);
            }
        }
        return sb.toString();
    }

    /**
     * 复制输入流
     */
    public InputStream cloneInputStream(ServletInputStream inputStream) throws IOException {
        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        byte[] buffer = new byte[2048];
        int len;
        while ((len = inputStream.read(buffer)) > -1) {
            byteArrayOutputStream.write(buffer, 0, len);
        }
        byteArrayOutputStream.flush();
        return new ByteArrayInputStream(byteArrayOutputStream.toByteArray());
    }

    @Override
    public BufferedReader getReader() {
        return new BufferedReader(new InputStreamReader(getInputStream(), CHARSET));
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

}
