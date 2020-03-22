package io.blackpine.eebulk;

import org.json.JSONArray;
import org.json.JSONObject;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.net.InetSocketAddress;
import java.net.URL;
import java.nio.file.CopyOption;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Callable;
import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLException;
import javax.net.ssl.SSLSession;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;

import bulkdownloadapplication.Encryption;

public class Main implements Callable<Integer> {
    private static final Logger logger =
        LoggerFactory.getLogger(Main.class);

    public static final int REQ_LOGIN = 2;
    public static final int REQ_FILE_LIST = 3;
    public static final int REQ_FILE = 4;
    public static final int REQ_FILE_STATUS = 5;
    public static final int REQ_ORDER_LIST = 7;

    public static final int STATUS_DOWNLOADING = 0;
    public static final int STATUS_COMPLETE = 2;
    public static final int STATUS_QUEUED = 5;

    @Parameters(index = "0", description = "EROS username")
	private String username;

    @Parameters(index = "1", description = "EROS password")
    private String password;

    @Option(names = {"-d", "--directory"},
        description = "Storage directory [default: 'data']")
    private File directory = new File("data/");

    @Option(names = {"-h", "--host"},
        description = "API host [default: 'eebulk.cr.usgs.gov']")
    private String host = "eebulk.cr.usgs.gov";

    @Option(names = {"-o", "--order"},
        description = "Order number (-1 for all orders) [default: -1]")
    private int order = -1;

    @Option(names = {"-p", "--port"},
        description = "API port [default: 4448]")
    private int port = 4448;

    @Option(names = {"-t", "--threads"},
        description = "Download thread count [default: 4]")
    private short threadCount = 4;

    public static void main(String[] args) {
        int exitCode = new CommandLine(new Main()).execute(args);
        System.exit(exitCode);
    }

    @Override
	public Integer call() throws Exception {
        logger.info("starting application");

        // create storage directory if it does not exist
        logger.debug("-Ddirectory: " + this.directory);
        if (!this.directory.exists()) {
            logger.info("creating directory '" + this.directory + "'");
            this.directory.mkdirs();
        }

        // set trust store information
        try {
            File copyFile = new File("/tmp/bda.jks");
            Date modifiedDate = new Date(copyFile.lastModified());
            Date originalDate = new Date(Main.class.getResource(
                "/bda.jks").openConnection().getLastModified());

            if (!copyFile.exists() || 
                    originalDate.compareTo(modifiedDate) > 0) {
                InputStream in = Main.class
                    .getResourceAsStream("/bda.jks");

                Files.copy(in, Paths.get("/tmp/bda.jks"),
                    new CopyOption[]{StandardCopyOption.REPLACE_EXISTING});
            }

            System.setProperty("javax.net.ssl.trustStore", "/tmp/bda.jks");
            System.setProperty("javax.net.ssl.trustStorePassword", "bda173");
        } catch (IOException e) {
            logger.error("unknown key store failure: " + e);
            return 1;
        }

        // connect to eebulk ssl server
        logger.info("connecting to eebulk ssl server");
        logger.debug("-Dhost: " + this.host);
        logger.debug("-Dport: " + this.port);

        SSLSocket socket = null;
        BufferedReader in = null;
        PrintWriter out = null;
        try {
            SSLSocketFactory sslSocketFactory = 
                (SSLSocketFactory) SSLSocketFactory.getDefault();

            socket = (SSLSocket) sslSocketFactory.createSocket();
            socket.connect(new InetSocketAddress(this.host, this.port), 30000);

            in = new BufferedReader(new InputStreamReader(
                socket.getInputStream()));
            out = new PrintWriter(socket.getOutputStream());

            in.readLine();
        } catch (IOException e) {
            logger.error("unknown ssl socket connection failure: " + e);
            return 1;
        }

        // authenticate using EROS username and password
        logger.info("authenicating to EROS system");
        logger.debug("-Dusername: " + this.username);
        logger.debug("-Dpassword: " + this.password);
        try {
            // initialize request
            JSONObject request = new JSONObject();
            request.put("requestType", REQ_LOGIN);
            request.put("message", ""); 

            request.put("username",
                Encryption.getInstance().encrypt(this.username));
            request.put("password",
                Encryption.getInstance().encrypt(this.password));
            request.put("requester", "bda");
            request.put("version", "1.4.1");

            // add client details
            JSONObject clientDetails = new JSONObject();
            clientDetails.put("os", System.getProperty("os.name")
                + " (build " + System.getProperty("os.version") + ", "
                + System.getProperty("os.arch") + ")");
            clientDetails.put("javaVersion",
                System.getProperty("java.version"));
            request.put("clientDetails", clientDetails);

            // send request
            JSONObject reply = send(request, out, in);

            // process reply
            if (reply.has("authenticated")
                    && Boolean.valueOf(reply.getBoolean("authenticated"))) {
                logger.info("authentication succces");
            } else {
                logger.error("authentication failure");
                return 1;
            }
        } catch (IOException e) {
            logger.error("authentication failure: " + e);
            return 1;
        }

        // retrieve bulk order file lists
        Map<Integer, JSONArray> orders = new HashMap();
        try {
            // initialize request
            JSONObject request = new JSONObject();
            request.put("requestType", REQ_ORDER_LIST);
            request.put("message", ""); 

            // send request
            JSONObject reply = send(request, out, in);

            // process orders
            JSONArray array = reply.getJSONArray("orders");
            for (Object object : array) {
                JSONObject order = (JSONObject) object;
                int orderId = order.getInt("orderId");

                if (this.order == -1 || this.order == orderId) {
                    // initialize request
                    JSONObject filesRequest = new JSONObject();
                    filesRequest.put("requestType", REQ_FILE_LIST);
                    filesRequest.put("message", ""); 
                    filesRequest.put("orderId", orderId); 

                    // send request
                    JSONObject filesReply = send(filesRequest, out, in);
                    
                    // process files
                    JSONArray filesArray =
                        filesReply.getJSONArray("filelist");
                    orders.put(orderId, filesArray);
                }
            }
        } catch (IOException e) {
            logger.error("bulk orders list failure: " + e);
            return 1;
        }

        // process orders
        for (Map.Entry<Integer, JSONArray> order : orders.entrySet()) {
            logger.info("processing order '" + order.getKey() + "'");

            // initialize and start a download manager instance
            DownloadManager downloadManager =
                new DownloadManager(this.directory, this.threadCount, in, out);
            downloadManager.start();

            // add files to download manager
            for (Object object : order.getValue()) {
                JSONObject fileJSON = (JSONObject) object;

                downloadManager.add(fileJSON);
            }

            // stop and wait for downloads to finish
            downloadManager.stop();
        }

        // disconnect ssl socket
        try {
            out.close();
            in.close();
            socket.close();
        } catch (IOException e) {
            logger.error("ssl socket close failure: " + e);
            return 1;
        } 

        // return success
		return 0;
	}

    protected static JSONObject send(JSONObject request,
            PrintWriter out, BufferedReader in) throws IOException {
        // send request
        logger.trace("request: " + request);
        out.println(request.toString());

        if (out.checkError()) {
            throw new SSLException("server was shut down");
        }

        // read reply
        String jsonReply = in.readLine();
        logger.trace("authentication reply: " + jsonReply);

        if (jsonReply.equals("Bye")) {
            throw new IOException("SSL socket shudown");
        }

        JSONObject reply = new JSONObject(jsonReply);
        // TODO - handle errorCode != 0
        /*if (reply.has("errorCode")) {
            logger.error("reply error code: " + reply.get("errorCode"));
            return 1;
        }*/

        return reply;
    }
}
