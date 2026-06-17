package com.example.retinavision;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = "spring.rabbitmq.listener.simple.auto-startup=false")
class RetinaVisionApplicationTests {

	@Test
	void contextLoads() {
	}

}
