package com.example.retinavision;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.transaction.annotation.EnableTransactionManagement;

@EnableTransactionManagement   // 开启事务管理
@SpringBootApplication                // SpringBoot启动类
@MapperScan("com.example.retinavision.mapper")  //
public class RetinaVisionApplication {

	public static void main(String[] args) {
		SpringApplication.run(RetinaVisionApplication.class, args);
	}

}
