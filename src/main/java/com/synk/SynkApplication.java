package com.synk;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.util.TimeZone;

@EnableJpaAuditing
@EnableScheduling
@SpringBootApplication
public class SynkApplication {
    public static void main(String[] args) {
        // Hibernate JDBC 커넥션 풀 초기화 전에 JVM 기본 타임존을 KST로 고정
        // @PostConstruct 사용 시 Spring 컨텍스트 초기화(커넥션 풀 생성) 이후에 실행되어 UTC가 적용될 수 있음
        TimeZone.setDefault(TimeZone.getTimeZone("Asia/Seoul"));
        SpringApplication.run(SynkApplication.class, args);
    }
}




