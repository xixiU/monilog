package com.jiduauto.monilog;

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
            //该方法可能会被调用多次(框架重试)
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
                    MoniLogUtil.log(p);
                } catch (Exception e) {
                    MoniLogUtil.innerDebug("onSend error", e);
                }

            }
            log.warn("monilog kafka onConsume...");
            return records;
        }

        @Override
        public void onCommit(Map<TopicPartition, OffsetAndMetadata> offsets) {
            //消费(失败重试)完成时回调
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
            //该方法可能会被调用多次(框架重试)
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
                MoniLogUtil.log(p);
            } catch (Exception e) {
                MoniLogUtil.innerDebug("onSend error", e);
            }
            return record;
        }

        @Override
        public void onAcknowledgement(RecordMetadata metadata, Exception exception) {
            String topic = metadata.topic();
            log.warn("onAcknowledgement...");
            //发送或异常时回调
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
