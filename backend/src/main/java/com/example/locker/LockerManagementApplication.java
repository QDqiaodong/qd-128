package com.example.locker;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;

import java.util.TimeZone;

@SpringBootApplication
@EnableCaching
public class LockerManagementApplication {

    /**
     * 应用统一使用的业务时区（北京时间）。页面时间选择器按浏览器本地时间生成
     * yyyy-MM-ddTHH:mm:ss，后端以 LocalDateTime 直接解析并用 LocalDateTime.now() 校验，
     * 因此 JVM 默认时区必须与页面口径一致。
     */
    public static final String BUSINESS_ZONE_ID = "Asia/Shanghai";

    /**
     * 统一 JVM 默认时区为北京时间。容器（eclipse-temurin 基础镜像）默认 UTC，
     * 不设置会导致页面提交的「当前时刻」晚于服务端 now()，登记停收等单据被
     * 「不能晚于当前时间」误判拦截。
     */
    public static void initBusinessTimeZone() {
        TimeZone.setDefault(TimeZone.getTimeZone(BUSINESS_ZONE_ID));
    }

    public static void main(String[] args) {
        initBusinessTimeZone();
        SpringApplication.run(LockerManagementApplication.class, args);
    }
}
