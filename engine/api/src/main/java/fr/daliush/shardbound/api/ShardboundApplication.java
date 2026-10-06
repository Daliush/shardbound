package fr.daliush.shardbound.api;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class ShardboundApplication {

    public static void main(String[] args) {
        SpringApplication.run(ShardboundApplication.class, args);
    }
}
