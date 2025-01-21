package org.fitznet.fun;

import org.springframework.boot.SpringApplication;

public class TestGamerBellApplication {

    public static void main(String[] args) {
        SpringApplication.from(GamerBellApplication::main).with(TestcontainersConfiguration.class).run(args);
    }

}
