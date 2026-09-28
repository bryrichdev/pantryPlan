package edu.wgu.pantryprep;

import org.springframework.boot.SpringApplication;

public class TestPantryPrepApplication {

    public static void main(String[] args) {
        SpringApplication.from(PantryPrepApplication::main).with(TestcontainersConfiguration.class).run(args);
    }

}
