# Monilog - 分布式系统监控日志组件

## 概述
MoniLog是一个基于Spring Boot的轻量级日志监控组件，提供统一的日志拦截、分析和上报功能。支持多种中间件和框架的自动埋点，帮助开发者快速实现系统监控。

## 核心功能
- 自动拦截Web请求、Feign调用、gRPC调用等
- 支持Redis、Kafka、RocketMQ等中间件的操作日志
- 提供统一的异常处理和日志格式
- 可扩展的日志输出策略

## 快速开始

### 添加依赖
```xml
<dependency>
    <groupId>com.example.monilog</groupId>
    <artifactId>monilog</artifactId>
    <version>1.2.2</version>
</dependency>
```

### 基础配置
在application.properties中添加：
```properties
spring.application.name=xxx
```

## 详细配置
通过`MoniLogProperties`类可配置以下参数，在idea中也有填写提示，以下是部分示例：

| 参数                    | 默认值   | 说明                       |
|-----------------------|-------|--------------------------|
| monilog.enable        | false | 当出现问题时可以一键关闭所有           |
| monilog.web.enable    | false | 当出现web监控问题时可以一键关闭监控，其他类似 |


## 支持组件
- Web请求拦截
- Feign客户端
- gRPC服务
- Redis操作
- Kafka生产者/消费者
- RocketMQ
- MyBatis SQL执行
- XXL-JOB任务

## 开发注意事项
1. 需要配合Lombok使用
3. 生产环境建议配置适当的详情和摘要日志级别

## 版本历史
- 1.2.2: 优化日志输出格式
- 1.1.0: 新增Kafka支持
- 1.0.0: 初始版本

## 更多文档
详细使用文档请参考：[技术Wiki](https://ncn3hjlyonrl.feishu.cn/wiki/AVSHw5r4liZO1Fktiojc4zVEnqf?from=from_copylink)