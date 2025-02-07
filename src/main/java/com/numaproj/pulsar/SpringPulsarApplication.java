package com.numaproj.pulsar;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.pulsar.annotation.EnablePulsar;

@SpringBootApplication
@EnablePulsar
public class SpringPulsarApplication {

    public static void main(String[] args) {
        SpringApplication.run(SpringPulsarApplication.class, args);
    }

}
