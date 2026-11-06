/*
 * Copyright 2011 LMAX Ltd.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.lmax.disruptor;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.tools.JavaCompiler;
import javax.tools.ToolProvider;
import java.io.ByteArrayOutputStream;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

class PackageDocumentationTest
{
    @TempDir
    Path directory;

    @Test
    void shouldCompileAndRunThePublishedPackageExample() throws Exception
    {
        final String documentation = Files.readString(
            Paths.get("src/main/java/com/lmax/disruptor/package-info.java"), StandardCharsets.UTF_8);
        final int heading = documentation.indexOf("<h2>Code Example</h2>");
        final int start = documentation.indexOf("<pre>{@code", heading) + "<pre>{@code".length();
        final int end = documentation.indexOf("}</pre>", start);
        final String example = documentation.substring(start, end).replaceAll("(?m)^ \\* ?", "");
        final Path source = directory.resolve("DisruptorExample.java");
        Files.writeString(source, example, StandardCharsets.UTF_8);

        final JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        assertNotNull(compiler, "The documentation example requires a JDK to compile");
        final String classPath = Paths.get(RingBuffer.class.getProtectionDomain()
            .getCodeSource().getLocation().toURI()).toString();
        final ByteArrayOutputStream diagnostics = new ByteArrayOutputStream();
        final int result = compiler.run(null, diagnostics, diagnostics,
            "--release", "11", "-classpath", classPath, "-d", directory.toString(), source.toString());
        assertEquals(0, result, () -> diagnostics.toString(StandardCharsets.UTF_8));

        try (URLClassLoader loader = new URLClassLoader(new URL[]{directory.toUri().toURL()}, getClass().getClassLoader()))
        {
            final Class<?> exampleClass = loader.loadClass("DisruptorExample");
            assertTimeoutPreemptively(Duration.ofSeconds(5),
                () -> exampleClass.getMethod("main", String[].class).invoke(null, (Object) new String[0]));
        }
    }
}
