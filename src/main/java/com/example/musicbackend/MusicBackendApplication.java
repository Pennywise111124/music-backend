package com.example.musicbackend;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import java.util.TimeZone;

@SpringBootApplication
public class MusicBackendApplication {

    public static void main(String[] args) {
        configureTimeZone();
        SpringApplication.run(MusicBackendApplication.class, args);
    }

    static void configureTimeZone() {
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
    }
}