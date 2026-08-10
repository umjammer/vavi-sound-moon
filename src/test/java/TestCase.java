/*
 * Copyright (c) 2025 by Naohide Sano, All rights reserved.
 *
 * Programmed by Naohide Sano
 */

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;
import java.util.List;
import java.util.function.Predicate;

import vavi.util.Debug;
import vavi.util.properties.annotation.Property;
import vavi.util.properties.annotation.PropsEntity;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;


/**
 * TestCase.
 *
 * @author <a href="mailto:umjammer@gmail.com">Naohide Sano</a> (nsano)
 * @version 0.00 2025-12-10 nsano initial version <br>
 */
@EnabledIf("localPropertiesExists")
@PropsEntity(url = "file:local.properties")
class TestCase {

    static boolean localPropertiesExists() {
        return Files.exists(Paths.get("local.properties"));
    }

    @Property(name = "mdr")
    String file = "src/test/resources/test.mdr";

    @Property(name = "mdrX")
    String mdrX;

    @Property(name = "mdl")
    String mml = "src/test/resources/test.mdl";

    @Property(name = "vavi.test.volume")
    double volume = 0.2;

    @Property
    String moonDotNet;

    @Property
    String dir;

    @Property
    String ext;

    @BeforeAll
    static void setupAll() throws Exception {
        Path tmp = Path.of("tmp");
        if (!Files.exists(tmp)) Files.createDirectory(tmp);
    }

    @BeforeEach
    void setup() throws Exception {
        if (localPropertiesExists()) {
            PropsEntity.Util.bind(this);
        }

        System.setProperty("moon.volume", "%4.2f".formatted(volume));

Debug.println("volume: " + System.getProperty("moon.volume"));
    }

    @Test
    @Disabled("To make Claude Sonnet 4.5 silence")
    void test1() throws Exception {
        for (byte a = 0; a < (byte) 0xff; a++) {
            byte r1 = (byte) (((a & 0xff) << 1) + ((a & 0xff) >>> 7));
            byte r2 = (byte) (((a & 0xff) << 1) + (((a & 0x80) != 0) ? 1 : 0));
            assertEquals(r1, r2);
        }
    }

    /**
     * @param dir separated by ';'
     * @param ext separated by ','
     */
    static List<Path> listFilesUnderDirFilteredByExt(String dir, String ext) {
Debug.println("dir: " + dir);
Debug.println("ext: " + ext);
        Predicate<Path> x = p -> Arrays.stream(ext.split(",")).anyMatch(e -> p.getFileName().toString().toUpperCase().endsWith(e));
        return Arrays.stream(dir.split(File.pathSeparator)).flatMap(d -> {
            try {
                return Files.walk(Paths.get(d)).filter(x);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }).toList();
    }

    @Test
    @DisplayName("compile dir")
    @EnabledIfSystemProperty(named = "vavi.test", matches = "ide")
    void test2() throws Exception {
        Path testMDL = Path.of("tmp/test_java.mdl");
        Path testMDR = Path.of("tmp/test_java.mdr");
        Path testMDL2 = Path.of("tmp/test_dotnet.mdl");
        Path testMDR2 = Path.of("tmp/test_dotnet.mdr");

        Files.deleteIfExists(testMDL);
        Files.deleteIfExists(testMDL2);

        listFilesUnderDirFilteredByExt(dir, ext).forEach(path -> {
            try {
Debug.println(path);
                Files.copy(path, testMDL, StandardCopyOption.REPLACE_EXISTING);
                Files.copy(path, testMDL2, StandardCopyOption.REPLACE_EXISTING);
                Files.deleteIfExists(testMDR);
                Files.deleteIfExists(testMDR2);

                // compile c#
Debug.println("compile c# --------");
                ProcessBuilder pb = new ProcessBuilder();
                pb.inheritIO();
                Process p = pb.command(moonDotNet, testMDL2.toString()).start();
                int r = p.waitFor();
                assertEquals(0, r);
                assertTrue(Files.exists(testMDR2), "c# compile failed");

                // compile java
Debug.println("compile java --------");

                moonDriver.console.Program.main(new String[] {testMDL.toString()});
                assertTrue(Files.exists(testMDR), "java compile failed");

                // compare
Debug.println("compare --------");
Debug.println("c#  : " + Files.size(testMDR2));
Debug.println("java: " + Files.size(testMDR));
                assertEquals(Files.size(testMDR2), Files.size(testMDR), "java output is different from the original");
            } catch (Exception e) {
Debug.println(e);
            }
        });
    }

    @Test
    @DisplayName("compile & compare c# & play")
    void test6() throws Exception {
Debug.println(mml);
        Path testMDL = Path.of("tmp/test_java.mdl");
        Path testMDR = Path.of("tmp/test_java.mdr");
        Path testMDL2 = Path.of("tmp/test_dotnet.mdl");
        Path testMDR2 = Path.of("tmp/test_dotnet.mdr");

        Files.copy(Path.of(mml), testMDL, StandardCopyOption.REPLACE_EXISTING);
        Files.copy(Path.of(mml), testMDL2, StandardCopyOption.REPLACE_EXISTING);
        Files.deleteIfExists(testMDR);
        Files.deleteIfExists(testMDR2);

        // compile c#
Debug.println("compile c# --------");
        ProcessBuilder pb = new ProcessBuilder();
        pb.inheritIO();
        // "-i" is what the original make_mdr.sh passes to mmckc; without it the song data is not included
        Process p = pb.command(moonDotNet, "-i", testMDL2.toString()).start();
        int r = p.waitFor();
        assertEquals(0, r);
        assertTrue(Files.exists(testMDR2), "c# compile failed");

        // compile java
Debug.println("compile java --------");
        moonDriver.console.Program.main(new String[] {"-i", testMDL.toString()});
        assertTrue(Files.exists(testMDR), "java compile failed");

        // compare
Debug.println("compare --------");
Debug.println("c#  : " + Files.size(testMDR2));
Debug.println("java: " + Files.size(testMDR));
        assertEquals(Files.size(testMDR2), Files.size(testMDR), "java output is different from the original");

        // play
Debug.println("play --------");
        if ("ide".equals(System.getProperty("vavi.test")))
            moonDriver.player.Program.main(new String[] {testMDR.toString()});
    }

    @Test
    @DisplayName("compare to original")
    void test7() throws Exception {
Debug.println(mml);
        Path testMDL = Path.of("tmp/test_java.mdl");
        Path testMDR = Path.of("tmp/test_java.mdr");

        Files.copy(Path.of(mml), testMDL, StandardCopyOption.REPLACE_EXISTING);
        Files.deleteIfExists(testMDR);

        assertTrue(Files.exists(Path.of(mdrX)), "target not exists");

        // compile java
Debug.println("compile java --------");
        // "-i" is what the original make_mdr.sh passes to mmckc; without it the song data is not included
        moonDriver.console.Program.main(new String[] {"-i", testMDL.toString()});
        assertTrue(Files.exists(testMDR), "java compile failed");

        // compare
Debug.println("compare --------");
Debug.println("original: " + Files.size(Path.of(mdrX)));
Debug.println("java:     " + Files.size(testMDR));
        assertEquals(Files.size(Path.of(mdrX)), Files.size(testMDR), "java output is different from the original");
    }

    @Test
    @DisplayName("play .mdr")
    @EnabledIfSystemProperty(named = "vavi.test", matches = "ide")
    void test3() throws Exception {
Debug.println(file);
        moonDriver.player.Program.main(new String[] {file});
    }
}
