package com.jiduauto.monilog;

import cn.hutool.core.util.SerializeUtil;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerInterceptor;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.OffsetAndMetadata;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerInterceptor;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.header.Headers;
import org.apache.kafka.common.record.TimestampType;

import java.util.Iterator;
import java.util.Map;

/**
 * kafka monilog处理器
 *
 * @author yp
 * @date 2023/12/28
 */
@Slf4j
public final class KafkaMonilogInterceptor {
    private static final String MONILOG_PARAMS_KEY = "__MoniLogParams";

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
            Iterator<ConsumerRecord<K, V>> it = records.iterator();
            while (it.hasNext()) {
                try {
                    ConsumerRecord<K, V> record = it.next();
                    String topic = record.topic();
                    V value = record.value();
                    long timestamp = record.timestampType() == TimestampType.CREATE_TIME ? record.timestamp() : System.currentTimeMillis();
                    if (timestamp <= 0) {
                        timestamp = System.currentTimeMillis();
                    }
                    MoniLogParams p = new MoniLogParams();
                    p.setLogPoint(LogPoint.kafka_consumer);
                    p.setAction("onConsume");
                    p.setService("kafkaConsumer"); //TODO
                    p.setServiceCls(this.getClass());
                    p.setCost(timestamp);
                    p.setSuccess(true);
                    p.setMsgCode(ErrorEnum.SUCCESS.name());
                    p.setMsgInfo(ErrorEnum.SUCCESS.getMsg());
                    p.setInput(new Object[]{value});
                    p.setTags(TagBuilder.of("topic", topic).toArray());
                    //...
                } catch (Exception e) {
                    MoniLogUtil.innerDebug("onSend error", e);
                }

            }
            log.warn("monilog kafka onConsume...");
            return records;
        }

        @Override
        public void onCommit(Map<TopicPartition, OffsetAndMetadata> offsets) {
            log.warn("onCommit...");
        }

        @Override
        public void close() {
            log.warn("close...");
        }

        @Override
        public void configure(Map<String, ?> configs) {
            log.warn("configure...");
        }
    }

    private static class MonilogProducerInterceptor<K, V> implements ProducerInterceptor<K, V> {
        @Override
        public ProducerRecord<K, V> onSend(ProducerRecord<K, V> record) {
            // 判断开关
            if (!ComponentEnum.kafka_producer.isEnable()) {
                return record;
            }
            Headers headers = record.headers();
            // 在发送完成后拦截，计算耗时并打印监控信息
            StackTraceElement st = ThreadUtil.getNextClassFromStack(KafkaProducer.class);
            String clsName;
            String action;
            if (st == null) {
                clsName = KafkaProducer.class.getCanonicalName();
                action = "send";
            } else {
                clsName = st.getClassName();
                action = st.getMethodName();
            }
            MoniLogParams p = new MoniLogParams();
            p.setLogPoint(LogPoint.kafka_producer);
            p.setAction(action);
            try {
                p.setServiceCls(Class.forName(clsName));
                p.setService(ReflectUtil.getSimpleClassName(p.getServiceCls()));
                p.setCost(record.timestamp() == null ? System.currentTimeMillis() : record.timestamp());
                p.setSuccess(true);
                p.setMsgCode(ErrorEnum.SUCCESS.name());
                p.setMsgInfo(ErrorEnum.SUCCESS.getMsg());
                p.setInput(new Object[]{record.value()});
                p.setTags(TagBuilder.of("topic", record.topic()).toArray());
                byte[] bytes = SerializeUtil.serialize(p);
                headers.add(MONILOG_PARAMS_KEY, bytes);
            } catch (Exception e) {
                MoniLogUtil.innerDebug("onSend error", e);
            }
            return record;
        }

        @Override
        public void onAcknowledgement(RecordMetadata metadata, Exception exception) {
            log.warn("onAcknowledgement...");
        }

        @Override
        public void close() {
            log.warn("close...");
        }

        @Override
        public void configure(Map<String, ?> configs) {
            log.warn("configure...");
        }
    }
}
