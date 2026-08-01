package com.yawei.erp;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@MapperScan("com.yawei.erp")
public class YaweiErpApplication {

    public static void main(String[] args) {
        SpringApplication.run(YaweiErpApplication.class, args);
    }
}
