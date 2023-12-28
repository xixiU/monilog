package com.jiduauto.monilog;

import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerInterceptor;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.OffsetAndMetadata;
import org.apache.kafka.clients.producer.ProducerInterceptor;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.apache.kafka.common.TopicPartition;

import java.util.Map;

/**
 * kafka monilog处理器
 *
 * @author yp
 * @date 2023/12/28
 */
@Slf4j
public final class KafkaMonilogInterceptor {
    public static <K, V> ConsumerInterceptor<K, V> getConsumerInterceptor() {
        return new KafkaMonilogInterceptor.MonilogConsumerInterceptor<>();
    }

    public static <K, V> ProducerInterceptor<K, V> getProducerInterceptor() {
        return new KafkaMonilogInterceptor.MonilogProducerInterceptor<>();
    }

    private static class MonilogConsumerInterceptor<K, V> implements ConsumerInterceptor<K, V> {


        @Override
        public ConsumerRecords<K, V> onConsume(ConsumerRecords<K, V> records) {
            // 判断开关
            if (!ComponentEnum.kafka_consumer.isEnable()) {
                return records;
            }
            log.warn("monilog kafka onConsume...");
            return records;
        }

        @Override
        public void onCommit(Map<TopicPartition, OffsetAndMetadata> offsets) {
        }

        @Override
        public void close() {
        }

        @Override
        public void configure(Map<String, ?> configs) {
        }
    }

    private static class MonilogProducerInterceptor<K, V> implements ProducerInterceptor<K, V> {

        @Override
        public ProducerRecord<K, V> onSend(ProducerRecord<K, V> record) {
            // 判断开关
            if (!ComponentEnum.kafka_producer.isEnable()) {
                return record;
            }
            log.warn("monilog kafka onSend...");
            return record;
        }

        @Override
        public void onAcknowledgement(RecordMetadata metadata, Exception exception) {
        }

        @Override
        public void close() {
        }

        @Override
        public void configure(Map<String, ?> configs) {
        }
    }
}
