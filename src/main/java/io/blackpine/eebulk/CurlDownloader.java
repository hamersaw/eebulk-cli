package io.blackpine.eebulk;

import org.json.JSONObject;

import java.io.File;
import java.io.IOException;
import java.lang.InterruptedException;
import java.lang.ProcessBuilder;

public class CurlDownloader implements Downloader {
    protected static final String USER_AGENT =
        "Mozilla/5.0 (X11; Linux i686; rv:64.0) Gecko/20100101 Firefox/64.0";

    @Override
    public void process(JSONObject listJSON, JSONObject fileJSON,
            File directory) throws InterruptedException, IOException {
        // build curl process
        ProcessBuilder processBuilder = new ProcessBuilder(
            "curl",
            "-A", USER_AGENT,
            "-o", directory + "/" + listJSON.getString("entityId"),
            fileJSON.getString("url"));

        // execute process
        Process process = processBuilder.start();
        process.waitFor();

        // check exit code
        if (process.exitValue() != 0) {
            throw new IOException("curl failed with exit code: "
                + process.exitValue());
        }
    }
}
