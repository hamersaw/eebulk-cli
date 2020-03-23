package io.blackpine.eebulk;

import org.json.JSONObject;

import java.io.File;
import java.io.IOException;
import java.lang.InterruptedException;

public interface Downloader {
    public void process(JSONObject listJSON, JSONObject fileJSON,
            File directory) throws InterruptedException, IOException;
}
