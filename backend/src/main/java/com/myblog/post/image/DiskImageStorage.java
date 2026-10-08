package com.myblog.post.image;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class DiskImageStorage implements ImageStorage {

    private final Path root;

    public DiskImageStorage(@Value("${blog.image.dir}") String dir) throws IOException {
        this.root = Path.of(dir).toAbsolutePath().normalize();
        Files.createDirectories(root);
    }

    @Override
    public void save(String key, byte[] bytes) {
        try {
            Files.write(resolve(key), bytes);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    public byte[] load(String key) {
        try {
            return Files.readAllBytes(resolve(key));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    public void delete(String key) {
        try {
            Files.deleteIfExists(resolve(key));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    public Stream<String> keys() {
        try (Stream<Path> files = Files.list(root)) {
            return files.map(p -> p.getFileName().toString()).toList().stream();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** 서버가 만든 이름만 받으므로 폴더 밖으로 나갈 수 없지만 한 번 더 막는다 */
    private Path resolve(String key) {
        Path path = root.resolve(key).normalize();
        if (!path.getParent().equals(root)) {
            throw new IllegalArgumentException("잘못된 이미지 이름");
        }
        return path;
    }
}
