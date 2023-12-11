package com.jiduauto.monilog;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;

import java.util.List;
import java.util.stream.Collectors;

/**
 * @author yp
 * @date 2023/12/11
 */
@Slf4j
@AllArgsConstructor
class ApolloChangeListenerRegistry {
    static void register(Runnable callback) {
        try {
            // 使用ApolloConfigChangeListener方法不生效，手动注入一个监听器
            com.ctrip.framework.apollo.ConfigService.getAppConfig().addChangeListener(changeEvent -> {
                List<String> changedKeysList = changeEvent.changedKeys().stream().filter(item -> item.startsWith("monilog")).collect(Collectors.toList());
                if (CollectionUtils.isEmpty(changedKeysList)) {
                    return;
                }
                // 日志记录
                for (String key : changedKeysList) {
                    com.ctrip.framework.apollo.model.ConfigChange change = changeEvent.getChange(key);
                    String oldValue = change.getOldValue();
                    String newValue = change.getNewValue();
                    log.info("monilog properties changed #configChange key:{} new value:{} old value:{}", key, newValue, oldValue);
                }
                // 只需要bindValue 一次就行，不要放在for循环里面
                callback.run();
            });
        } catch (Throwable e) {
            log.warn("addApolloListener failed, apollo sdk maybe missing, monilog's config property cannot changed by apollo");
        }
    }
}
