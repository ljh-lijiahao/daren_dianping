package com.example.drdp;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.EnableAspectJAutoProxy;

@MapperScan("com.example.drdp.mapper")
@EnableAspectJAutoProxy(exposeProxy = true)
@SpringBootApplication
public class DaRenDianPingApplication {

    public static void main(String[] args) {
        SpringApplication.run(DaRenDianPingApplication.class, args);
    }

}
