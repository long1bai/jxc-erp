package com.jxc.erp;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@MapperScan("com.jxc.erp")
public class JxcErpApplication {

    public static void main(String[] args) {
        SpringApplication.run(JxcErpApplication.class, args);
    }
}
