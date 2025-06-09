package com.example.monilog;

import org.springframework.boot.ansi.AnsiOutput;
import org.springframework.boot.ansi.AnsiPropertySource;
import org.springframework.core.env.MutablePropertySources;
import org.springframework.core.env.PropertySourcesPropertyResolver;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.util.StringUtils;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * MoniLog logo输出
 *
 * @author rongjie.yuan
 * @date 2023/8/17 10:58
 */
class MoniLogBanner {
    private static final String BANNER_LOCATION = "classpath:monilogbanner.txt";
    private final ResourceLoader resourceLoader = new DefaultResourceLoader(null);
    private final Map<String, String> placeholders;

    private final PrintStream out = System.out;

    MoniLogBanner(Map<String, String> placeholders) {
        this.placeholders = placeholders;
    }

    void printBanner() {
        Resource resource = resourceLoader.getResource(BANNER_LOCATION);
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                line = replacePlaceholders(line);
                line = AnsiOutput.toString(line); // 处理颜色设置
                out.println(line);
            }
        } catch (IOException e) {
            // 处理异常
        }
    }

    private String replacePlaceholders(String line) {
        // 处理颜色,见org.springframework.boot.ResourceBanner.getAnsiResolver
        MutablePropertySources sources = new MutablePropertySources();
        sources.addFirst(new AnsiPropertySource("ansi", true));
        line = new PropertySourcesPropertyResolver(sources).resolvePlaceholders(line);
        // 适配{xx.xx}与${xx.xx}写法
        for (Map.Entry<String, String> entry : placeholders.entrySet()) {
            String dollarPlaceholder = "${" + entry.getKey() + "}";
            String replacement = entry.getValue();
            line = StringUtils.replace(line, dollarPlaceholder, replacement);
            String placeholder = "{" + entry.getKey() + "}";
            line = StringUtils.replace(line, placeholder, replacement);
        }
        return line;
    }

}
