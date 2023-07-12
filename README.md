该包提供了一个方便的流量拦截工具，用于在流量的出口和入口收集方法执行参数、结果及耗时信息，并提供统一的打日志接口(供实现)

使用方法：
1. 引入maven依赖：

    <dependency>
        <groupId>com.jiduauto.log</groupId>
        <artifactId>monitor-log-spring-boot-starter</artifactId>
        <version>1.0.0-SNAPSHOT</version>
    </dependency>

2. 实现MonitorLogPrinter接口，并为实现类打上@Component注解交由Spring容器即可
