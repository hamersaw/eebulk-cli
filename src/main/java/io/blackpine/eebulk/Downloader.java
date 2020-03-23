package io.blackpine.eebulk;

import org.json.JSONObject;

import java.io.File;
import java.io.IOException;

public interface Downloader {
    public void process(JSONObject listJSON,
            JSONObject fileJSON, File directory) throws IOException;
}
