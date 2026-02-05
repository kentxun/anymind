package com.anymind.promptrecorder.storage;

import java.nio.file.Path;
import java.nio.file.Paths;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class StoragePaths {
    private final Path root;

    public StoragePaths(@Value("${storage.root:data}") String rootDir) {
        this.root = Paths.get(rootDir).toAbsolutePath();
    }

    public Path root() {
        return root;
    }

    public Path registryDb() {
        return root.resolve("registry.sqlite");
    }

    public Path spaceDir(String spaceId) {
        return root.resolve("spaces").resolve(spaceId);
    }

    public Path spaceDb(String spaceId) {
        return spaceDir(spaceId).resolve("space.sqlite");
    }
}
