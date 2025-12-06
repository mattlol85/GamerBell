package org.fitznet.fun;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = "org.fitznet.fun")
@Slf4j
public class GamerBellApplication {

    public static void main(String[] args) {
        SpringApplication.run(GamerBellApplication.class, args);
    }

}
