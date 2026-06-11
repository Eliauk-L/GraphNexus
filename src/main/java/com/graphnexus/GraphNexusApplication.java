package com.graphnexus;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.amqp.RabbitAutoConfiguration;
import org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration;
import org.springframework.ai.autoconfigure.openai.OpenAiAutoConfiguration;

@SpringBootApplication(exclude = {
        OpenAiAutoConfiguration.class
})
public class GraphNexusApplication {

    public static void main(String[] args) {
        SpringApplication.run(GraphNexusApplication.class, args);
    }
}