package org.ict.kibwa.artmo;

import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import java.io.File;

@SpringBootApplication
public class ArtmoApplication {

    @Value("${storage.dir}")
    String storageDirectory;

    public static void main(String[] args) {
        SpringApplication.run(ArtmoApplication.class, args);
    }

    @PostConstruct
    public void createDirectory() {
        File directory = new File(storageDirectory);
        if (!directory.exists() && !directory.mkdirs()) {
            throw new RuntimeException("Unable to create storage directory");
        }
    }
}
