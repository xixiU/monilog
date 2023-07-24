package com.jiduauto.log.feign;

import feign.Response;
import org.apache.commons.lang3.StringUtils;
import org.springframework.http.HttpHeaders;
import org.springframework.util.StreamUtils;

import java.io.*;
import java.util.Collection;
import java.util.Map;

/**
 * @author yp
 * @date 2023/07/24
 */
class BufferingFeignClientResponse implements Closeable {
    private Response response;
    private byte[] body;

    BufferingFeignClientResponse(Response response) {
        this.response = response;
    }

    Response getResponse() {
        return this.response;
    }

    int status() {
        return this.response.status();
    }

    private Map<String, Collection<String>> headers() {
        return this.response.headers();
    }


    boolean isDownstream() {
        String header = getFirstHeader(HttpHeaders.CONTENT_DISPOSITION);
        return StringUtils.containsIgnoreCase(header, "attachment")
                || StringUtils.containsIgnoreCase(header, "filename");
    }

    boolean isJson() {
        if (isDownstream()) {
            return false;
        }
        String header = getFirstHeader(HttpHeaders.CONTENT_TYPE);
        return StringUtils.containsIgnoreCase(header, "application/json");
    }

    String getFirstHeader(String name) {
        if (headers() == null || StringUtils.isBlank(name)) {
            return null;
        }
        for (Map.Entry<String, Collection<String>> me : headers().entrySet()) {
            if (me.getKey().equalsIgnoreCase(name)) {
                Collection<String> headers = me.getValue();
                if (headers == null || headers.isEmpty()) {
                    return null;
                }
                return headers.iterator().next();
            }
        }
        return null;
    }

    String body() throws IOException {
        StringBuilder sb = new StringBuilder();
        try (InputStreamReader reader = new InputStreamReader(getBody())) {
            char[] tmp = new char[1024];
            int len;
            while ((len = reader.read(tmp, 0, tmp.length)) != -1) {
                sb.append(new String(tmp, 0, len));
            }
        }
        return sb.toString();
    }

    private InputStream getBody() throws IOException {
        if (this.body == null) {
            this.body = StreamUtils.copyToByteArray(this.response.body().asInputStream());
        }
        return new ByteArrayInputStream(this.body);
    }

    @Override
    public void close() {
        this.response.close();
    }
}