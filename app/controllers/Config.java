package controllers;

import java.io.IOException;
import java.io.InputStream;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.horstmann.codecheck.ResourceLoader;

@Singleton public class Config implements ResourceLoader {
    @Inject private com.typesafe.config.Config config;
    @Inject private play.api.Environment playEnv;

    public InputStream loadResource(String path) throws IOException {
        return playEnv.classLoader().getResourceAsStream("public/resources/" + path);
    }
    public String getProperty(String key) {
        return config.hasPath(key) ? config.getString(key) : null;
    }
    public String getString(String key) {
        return getProperty(key);
    }
    public boolean hasPath(String key) {
        return getProperty(key) != null;
    }
}