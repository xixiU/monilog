该包提供了一个方便的流量拦截工具，用于在流量的出口和入口收集方法执行参数、结果及耗时信息，并提供统一的打日志接口(供实现)

使用方法：
1. 引入maven依赖：
```xml
    <dependency>
        <groupId>com.jiduauto.log</groupId>
        <artifactId>monitor-log-spring-boot-starter</artifactId>
        <version>1.0.0-SNAPSHOT</version>
    </dependency>
```

2. 在应用中流量出入口的类上打上@MonitorLog注解， 则该类的方法在执行时就会自动聚合日志所需的字段信息

3. 通常情况下，解析器会自动按常规方式解析方法的响应结果，也可以在目标方法上打上@LogParser注解来提供自定义响应结果解析器

4. 实现 MonitorLogPrinter 接口，并为实现类打上 @Component 注解交由Spring容器即可
