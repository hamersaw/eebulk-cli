package io.blackpine.eebulk;

import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.IOException;
import java.net.URL;
import javax.net.ssl.HttpsURLConnection;

public class JavaSSLDownloader implements Downloader {
    @Override
    public void process(JSONObject listJSON,
            JSONObject fileJSON, File directory) throws IOException {
        // connect to url
        URL url = new URL(fileJSON.getString("url"));
        HttpsURLConnection connection =
            (HttpsURLConnection) url.openConnection();
        connection.setReadTimeout(300000);
        connection.connect();

        // validate connection response
        if (connection.getResponseCode() != 200) {
            throw new IOException("invalid repsonse code '"
                + connection.getResponseCode() + "'");
        }

        long remainingLength = connection.getContentLength();

        if (listJSON.getString("eula_code").length() > 0) {
            // TODO - download EULA.txt
        }

        // open output file
        File file = new File(directory + "/"
            + listJSON.getString("entityId"));
        FileOutputStream fileOut = new FileOutputStream(file);

        // download data from connection
        byte[] buffer = new byte[8096];
        int bytesRead = 0;

        InputStream stream = connection.getInputStream();
        while (remainingLength > 0) {
            bytesRead = stream.read(buffer);
            fileOut.write(buffer, 0, bytesRead);

            remainingLength -= bytesRead;
        }

        // close file
        fileOut.close();
    }
}
