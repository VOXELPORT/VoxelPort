package org.localm.service;

import java.io.*;
import java.nio.file.*;
import java.util.*;

public class ConfigService {
    public Properties loadProperties(Path serverDir) throws IOException {
        Path propsFile = serverDir.resolve("server.properties");
        Properties props = new Properties();
        if (Files.exists(propsFile)) {
            try (InputStream in = Files.newInputStream(propsFile)) {
                props.load(in);
            }
        }
        return props;
    }

    public void saveProperties(Path serverDir, Properties props) throws IOException {
        Path propsFile = serverDir.resolve("server.properties");
        try (OutputStream out = Files.newOutputStream(propsFile)) {
            props.store(out, "Modified by LocalM Java Edition");
        }
    }
}
