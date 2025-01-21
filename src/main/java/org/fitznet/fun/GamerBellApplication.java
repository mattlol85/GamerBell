package org.fitznet.fun;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = "org.fitznet.fun")
public class GamerBellApplication {

    public static void main(String[] args) {
        SpringApplication.run(GamerBellApplication.class, args);
    }

}
