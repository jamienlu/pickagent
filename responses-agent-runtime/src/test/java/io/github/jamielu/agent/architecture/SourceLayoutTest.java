package io.github.jamielu.agent.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SourceLayoutTest {
    // 场景：扫描 src 下的全部 Java 文件；行为：检查标准 Maven 根目录；预期：生产代码只在 main/java，测试代码只在 test/java。
    @Test
    void javaSourcesUseStandardMavenDirectories() throws IOException {
        Path sourceRoot = Path.of("src").toAbsolutePath().normalize();
        Path mainRoot = sourceRoot.resolve("main/java");
        Path testRoot = sourceRoot.resolve("test/java");

        List<String> misplaced;
        try (var paths = Files.walk(sourceRoot)) {
            misplaced = paths
                    .filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().endsWith(".java"))
                    .filter(path -> !path.startsWith(mainRoot) && !path.startsWith(testRoot))
                    .map(sourceRoot::relativize)
                    .map(Path::toString)
                    .sorted()
                    .toList();
        }

        assertEquals(List.of(), misplaced, "Java 源文件必须位于标准 Maven 目录");
    }
}
