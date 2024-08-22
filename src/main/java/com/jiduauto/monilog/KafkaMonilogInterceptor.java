package com.jiduauto.monilog;

import com.alibaba.fastjson.JSONObject;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.Callback;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.springframework.messaging.Message;

/**
 * kafka monilog处理器
 *
 * @author yp
 * @date 2023/12/28
 */
@Slf4j
public final class KafkaMonilogInterceptor {
    public static class ConsumerInterceptor {
        /**
         * 请勿修改本方法的方法名及可见性
         */
        public static MoniLogParams beforeInvoke(Message<?> message, Object... providedArgs) {
            try {
                // 判断开关
                if (!ComponentEnum.kafka_consumer.isEnable()) {
                    return null;
                }
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
                ConsumerRecord<?, ?> cr = findConsumerRecord(providedArgs);
                p.setLogPoint(LogPoint.kafka_consumer);
                p.setAction(action);
                p.setServiceCls(Class.forName(clsName));
                p.setService(ReflectUtil.getSimpleClassName(p.getServiceCls()));
                p.setCost(System.currentTimeMillis());
                p.setSuccess(true);
                p.setMsgCode(ErrorEnum.SUCCESS.name());
                p.setMsgInfo(ErrorEnum.SUCCESS.getMsg());
                p.setInput(new Object[]{formatInput(cr)});
                if (cr != null) {
                    p.setTags(TagBuilder.of("topic", cr.topic()).toArray());
                }
                return p;
            } catch (Throwable t) {
                MoniLogUtil.innerDebug("kafka beforeConsume error", t);
                return null;
            }
        }

        /**
         * 请勿修改本方法的方法名及可见性
         */
        public static void afterInvoke(MoniLogParams p, long start, Throwable ex, Object result) {
            if (p == null) {
                return;
            }
            try {
                p.setCost(System.currentTimeMillis() - start);
                p.setOutput(result);
                p.setException(ex);
                p.setSuccess(ex == null);
                if (ex != null) {
                    ErrorInfo errorInfo = ExceptionUtil.parseException(ex);
                    p.setMsgCode(errorInfo.getErrorCode());
                    p.setMsgInfo(errorInfo.getErrorMsg());
                }
                MoniLogUtil.log(p);
            } catch (Throwable t) {
                MoniLogUtil.innerDebug("kafka onConsume afterInvoke error", t);
            }
        }

        private static ConsumerRecord<?, ?> findConsumerRecord(Object... providedArgs) {
            if (providedArgs == null) {
                return null;
            }
            for (Object o : providedArgs) {
                if (o instanceof ConsumerRecord) {
                    return (ConsumerRecord<?, ?>) o;
                }
            }
            return null;
        }
    }

    public static class ProducerInterceptor {
        /**
         * 请勿修改本方法的方法名及可见性
         */
        public static <K, V> MoniLogParams beforeSend(ProducerRecord<K, V> record) {
            if (!ComponentEnum.kafka_producer.isEnable()) {
                return null;
            }
            try {
                //该方法可能会被调用多次(框架重试)
                StackTraceElement st = ThreadUtil.getNextClassFromStack(ProducerInterceptor.class);
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
                p.setServiceCls(Class.forName(clsName));
                p.setService(ReflectUtil.getSimpleClassName(p.getServiceCls()));
                p.setCost(System.currentTimeMillis());
                p.setSuccess(true);
                p.setMsgCode(ErrorEnum.SUCCESS.name());
                p.setMsgInfo(ErrorEnum.SUCCESS.getMsg());
                p.setInput(new Object[]{formatInput(record)});
                p.setTags(TagBuilder.of("topic", record.topic()).toArray());
                return p;
            } catch (Exception e) {
                MoniLogUtil.innerDebug("kafka beforeSend error", e);
            }
            return null;
        }

        public static class KfkSendCallback implements Callback {
            private final Callback delegate;
            private final long timestamp;
            private final MoniLogParams mp;

            public KfkSendCallback(Callback delegate, long timestamp, MoniLogParams mp) {
                this.delegate = delegate;
                this.timestamp = timestamp;
                this.mp = mp;
            }

            @Override
            public void onCompletion(RecordMetadata metadata, Exception e) {
                if (mp == null) {
                    doRealCompletion(metadata, e);
                    return;
                }
                try {
                    mp.setCost(timestamp > 0 ? System.currentTimeMillis() - timestamp : 0);
                    mp.setException(e);
                    mp.setSuccess(e == null);
                    if (e != null) {
                        ErrorInfo errorInfo = ExceptionUtil.parseException(e);
                        mp.setMsgCode(errorInfo.getErrorCode());
                        mp.setMsgInfo(errorInfo.getErrorMsg());
                    }
                    MoniLogUtil.log(mp);
                } catch (Throwable t) {
                    MoniLogUtil.innerDebug("kafka onCompletion error", t);
                }
                doRealCompletion(metadata, e);
            }

            private void doRealCompletion(RecordMetadata metadata, Exception e) {
                if (delegate == null) {
                    return;
                }
                delegate.onCompletion(metadata, e);
            }
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
