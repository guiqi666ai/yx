package com.yxlogin.config.objectMapper;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.fasterxml.jackson.datatype.jsr310.deser.LocalDateTimeDeserializer;
import com.fasterxml.jackson.datatype.jsr310.ser.LocalDateTimeSerializer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Configuration
public class ObjectMapperConfig {
    private static final String DATETIME_PATTERN = "yyyy-MM-dd HH:mm:ss";
    @Bean
    @Primary
    public ObjectMapper objectMapper(){
        ObjectMapper mapper = new ObjectMapper();
        // json有多余未知字段，不抛异常
        mapper.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
        // Date不要输出时间戳，输出日期字符串
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        // ==========支持 LocalDateTime / LocalDate ==========
        JavaTimeModule javaTimeModule=new JavaTimeModule();
        javaTimeModule.addSerializer(LocalDateTime.class,new LocalDateTimeSerializer(DateTimeFormatter.ofPattern(DATETIME_PATTERN)));
        javaTimeModule.addDeserializer(LocalDateTime.class,
                new LocalDateTimeDeserializer(DateTimeFormatter.ofPattern(DATETIME_PATTERN)));
        mapper.registerModule(javaTimeModule);
        return mapper;
    }
}
