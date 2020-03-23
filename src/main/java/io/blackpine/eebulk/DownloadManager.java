package io.blackpine.eebulk;

import org.json.JSONArray;
import org.json.JSONObject;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.lang.InterruptedException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.Semaphore;
import javax.net.ssl.HttpsURLConnection;

public class DownloadManager {
    private static final Logger logger =
        LoggerFactory.getLogger(DownloadManager.class);

    protected File directory;
    protected short threadCount;
    protected BufferedReader in;
    protected PrintWriter out;

    protected Semaphore semaphore;
    protected ArrayBlockingQueue<JSONObject> queue;
    protected List<Thread> threads;

    public DownloadManager(File directory, short threadCount,
            BufferedReader in, PrintWriter out) {
        this.directory = directory;
        this.threadCount = threadCount;
        this.in = in;
        this.out = out;

        this.semaphore = new Semaphore(1);
        this.queue = new ArrayBlockingQueue(16);
        this.threads = new ArrayList();
    }

    public void add(JSONObject json) throws InterruptedException, IOException {
        while (!this.queue.offer(json)) {
            Thread.sleep(50);
        }

        // set file status to 'QUEUED'
        semaphore.acquire();
        try {
            // initialize file request
            JSONObject request = new JSONObject();
            request.put("requestType", Main.REQ_FILE_STATUS);
            request.put("message", ""); 
            request.put("type", "set"); 
            request.put("status", Main.STATUS_QUEUED); 
            request.put("fileId", json.get("fileId")); 

            // send request
            Main.send(request, out, in);
        } finally {
            semaphore.release();
        }
    }

    public void start() {
        // start 'threadCount' worker threads
        for (int i=0; i<this.threadCount; i++) {
            Thread thread = new Worker();
            thread.start();

            this.threads.add(thread);
        }
    }

    public void stop() {
        // ensure this is running
        if (this.threads.size() == 0) {
            return;
        }

        // write empty objects to queue
        for (int i=0; i<this.threads.size(); i++) {
            while (!this.queue.offer(new JSONObject())) {
                try {
                    Thread.sleep(50);
                } catch (InterruptedException e) {
                    logger.warn("failed to add poison pill: " + e);
                    break;
                }
            }
        }

        // join worker threads
        for (Thread thread : this.threads) {
            try {
                thread.join();
            } catch (InterruptedException e) {
                logger.warn("failed to join download thread: " + e);
                break;
            }
        }

        // clearn threads list
        this.threads.clear();
    }

    protected class Worker extends Thread {
        @Override
        public void run() {
            while (true) {
                try {
                    // retrieve next item
                    JSONObject fileJSON = queue.take();

                    // if json is empty -> break from loop
                    if (fileJSON.length() == 0) {
                        break;
                    }

                    // retrieve file
                    JSONObject reply = null;
                    semaphore.acquire();
                    try {
                        // initialize file request
                        JSONObject request = new JSONObject();
                        request.put("requestType", Main.REQ_FILE);
                        request.put("message", ""); 
                        request.put("fileId", fileJSON.get("fileId")); 

                        // send request
                        reply = Main.send(request, out, in);
                    } finally {
                        semaphore.release();
                    }

                    // open file url connection
                    logger.debug("connecting to url '"
                        + reply.getString("url") + "'");

                    URL url = new URL(reply.getString("url"));
                    HttpsURLConnection connection =
                        (HttpsURLConnection) url.openConnection();
                    connection.setReadTimeout(300000);

                    connection.connect();

                    // validate connection response
                    if (connection.getResponseCode() != 200) {
                        switch (connection.getResponseCode()) {
                            case 403:
                                logger.error("forbidden url");
                                break;
                            case 404:
                                logger.error("url not found");
                                break;
                            default:
                                logger.error("url unavailable");
                                break;
                        }

                        continue;
                    }

                    long remainingLength = connection.getContentLength();

                    if (fileJSON.getString("eula_code").length() > 0) {
                        // TODO - download EULA.txt
                    }

                    // open output file
                    File file = new File(directory + "/"
                        + fileJSON.getString("entityId"));
                    FileOutputStream fileOut = new FileOutputStream(file);

                    // set file status to 'DOWNLOADING'
                    semaphore.acquire();
                    try {
                        // initialize file request
                        JSONObject request = new JSONObject();
                        request.put("requestType", Main.REQ_FILE_STATUS);
                        request.put("message", ""); 
                        request.put("type", "set"); 
                        request.put("status", Main.STATUS_DOWNLOADING); 
                        request.put("fileId", fileJSON.get("fileId")); 

                        // send request
                        Main.send(request, out, in);
                    } finally {
                        semaphore.release();
                    }

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

                    logger.debug("downloaded url '"
                        + reply.getString("url") + "'");

                    // set file status to 'COMPLETE'
                    semaphore.acquire();
                    try {
                        // initialize file request
                        JSONObject request = new JSONObject();
                        request.put("requestType", Main.REQ_FILE_STATUS);
                        request.put("message", ""); 
                        request.put("type", "set"); 
                        request.put("status", Main.STATUS_COMPLETE); 
                        request.put("fileId", fileJSON.get("fileId")); 

                        // send request
                        Main.send(request, out, in);
                    } finally {
                        semaphore.release();
                    }
                } catch (InterruptedException e) {
                    logger.error("interrupted: " + e);
                    break;
                } catch (IOException e) {
                    logger.error("io: " + e);
                    continue;
                }
            }
        }
    }
}
