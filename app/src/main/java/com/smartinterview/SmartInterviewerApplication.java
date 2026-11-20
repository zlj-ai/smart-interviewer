package com.smartinterview;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class SmartInterviewerApplication {

  public static void main(String[] args) {
    SpringApplication.run(SmartInterviewerApplication.class, args);
  }
}
