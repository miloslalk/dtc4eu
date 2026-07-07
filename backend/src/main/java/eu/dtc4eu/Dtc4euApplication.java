package eu.dtc4eu;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class Dtc4euApplication {

    public static void main(String[] args) {
        SpringApplication.run(Dtc4euApplication.class, args);
    }
}
