package edu.wgu.pantryplan;

import org.springframework.boot.SpringApplication;

public class TestPantryplanApplication {

    public static void main(String[] args) {
        SpringApplication.from(PantryplanApplication::main).with(TestcontainersConfiguration.class).run(args);
    }

}
