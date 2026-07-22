package com.bprojects.courses.claude.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Bean;

@org.springframework.context.annotation.Configuration
public class Configuration {

    @Bean
    ObjectMapper objectMapper(){
        return new ObjectMapper();
    }

}
