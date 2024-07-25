package controllers;

import com.google.inject.Inject;
import com.typesafe.config.Config;

public class Config1 {
    @Inject private Config config;

    public boolean hasPath(String path) {
        return config.hasPath(path);
    }

    public String getString(String path) {
        return config.getString(path);
    }
}
