package com.jiduauto.monilog;

import com.alibaba.fastjson.JSONObject;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.*;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerInterceptor;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.apache.kafka.common.TopicPartition;

import java.util.*;

/**
 * kafka monilog处理器
 *
 * @author yp
 * @date 2023/12/28
 */
@Slf4j
public final class KafkaMonilogInterceptor {
    private static final ThreadLocal<Map<String, Object>> KAFKA_CTX = ThreadLocal.withInitial(() -> new HashMap<>(4));
    public static <K, V> ConsumerInterceptor<K, V> getConsumerInterceptor() {
        return new KafkaMonilogInterceptor.MonilogConsumerInterceptor<>();
    }

    public static <K, V> ProducerInterceptor<K, V> getProducerInterceptor() {
        return new KafkaMonilogInterceptor.MonilogProducerInterceptor<>();
    }

    @Getter
    @Setter
    private static class ConsumeCtx{
        private long timestamp;
        private MoniLogParams params;
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
                    int partition = record.partition();
                    MoniLogParams p = new MoniLogParams();
                    StackTraceElement st = ThreadUtil.getNextClassFromStack(KafkaMonilogInterceptor.class);
                    String clsName;
                    String action;
                    if (st == null) {
                        clsName = KafkaConsumer.class.getCanonicalName();
                        action = "onConsume";
                    } else {
                        clsName = st.getClassName();
                        action = st.getMethodName();
                    }
                    p.setLogPoint(LogPoint.kafka_consumer);
                    p.setAction(action);
                    p.setServiceCls(Class.forName(clsName));
                    p.setService(ReflectUtil.getSimpleClassName(p.getServiceCls()));
                    p.setCost(0);
                    p.setSuccess(true);
                    p.setMsgCode(ErrorEnum.SUCCESS.name());
                    p.setMsgInfo(ErrorEnum.SUCCESS.getMsg());
                    p.setInput(new Object[]{formatInput(record)});
                    p.setTags(TagBuilder.of("topic", topic).toArray());
                    ConsumeCtx consumeCtx = new ConsumeCtx();
                    consumeCtx.setTimestamp(System.currentTimeMillis());
                    consumeCtx.setParams(p);
                    KAFKA_CTX.get().put(topic + "_" + partition, consumeCtx);
                } catch (Throwable e) {
                    MoniLogUtil.innerDebug("kafka onConsume error", e);
                }
            }
            return records;
        }

        @Override
        public void onCommit(Map<TopicPartition, OffsetAndMetadata> offsets) {
            //消费(失败重试)完成时回调
            try {
                for (Map.Entry<TopicPartition, OffsetAndMetadata> topicPartitionOffsetAndMetadataEntry : offsets.entrySet()) {
                    TopicPartition tp = topicPartitionOffsetAndMetadataEntry.getKey();
                    String topic = tp.topic();
                    int partition = tp.partition();
                    String key = topic + "_" + partition;
                    Object ctx = KAFKA_CTX.get().get(key);
                    if (ctx == null) {
                        continue;
                    }
                    ConsumeCtx consumeCtx = (ConsumeCtx) ctx;
                    MoniLogParams p = consumeCtx.getParams();
                    p.setCost(System.currentTimeMillis() - consumeCtx.getTimestamp());
                    MoniLogUtil.log(p);
                    KAFKA_CTX.get().remove(key);
                }
            } catch (Throwable e) {
                MoniLogUtil.innerDebug("kafka onCommit error", e);
            }
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
            //该方法可能会被调用多次(框架重试)
            StackTraceElement st = ThreadUtil.getNextClassFromStack(MonilogProducerInterceptor.class);
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
                p.setCost(0);
                p.setSuccess(true);
                p.setMsgCode(ErrorEnum.SUCCESS.name());
                p.setMsgInfo(ErrorEnum.SUCCESS.getMsg());
                p.setInput(new Object[]{formatInput(record)});
                p.setTags(TagBuilder.of("topic", record.topic()).toArray());
                MoniLogUtil.log(p);
            } catch (Exception e) {
                MoniLogUtil.innerDebug("kafka onSend error", e);
            }
            return record;
        }

        @Override
        public void onAcknowledgement(RecordMetadata metadata, Exception e) {
            String topic = metadata.topic();
            if (e != null) {
                log.error("kafkaMsg[{}] send error:{}", topic, e.getMessage());
            }
        }

        @Override
        public void close() {
        }

        @Override
        public void configure(Map<String, ?> configs) {
        }
    }

    private static <K, V> Object formatInput(ProducerRecord<K, V> record) {
        if (record == null) {
            return null;
        }
        JSONObject obj = new JSONObject();
        obj.put("topic", record.topic());
        obj.put("partition", record.partition());
        obj.put("timestamp", record.timestamp());
        obj.put("key", record.key());
        obj.put("value", record.value());
        return obj;
    }

    private static <K, V> Object formatInput(ConsumerRecord<K, V> record) {
        if (record == null) {
            return null;
        }
        JSONObject obj = new JSONObject();
        obj.put("topic", record.topic());
        obj.put("partition", record.partition());
        obj.put("offset", record.offset());
        obj.put("timestamp", record.timestamp());
        obj.put("timestampType", record.timestampType());
        obj.put("key", record.key());
        obj.put("value", record.value());
        return obj;
    }
}
