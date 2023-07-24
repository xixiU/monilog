package com.jiduauto.log.feign;

import feign.Response;
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

    private Response getResponse() {
        return this.response;
    }

    private int status() {
        return this.response.status();
    }

    private Map<String, Collection<String>> headers() {
        return this.response.headers();
    }

    private String body() throws IOException {
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