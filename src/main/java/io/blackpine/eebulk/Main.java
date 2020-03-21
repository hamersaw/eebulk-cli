package io.blackpine.eebulk;

import org.json.JSONObject;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.net.InetSocketAddress;
import java.nio.file.CopyOption;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Date;
import java.util.concurrent.Callable;
import javax.net.ssl.SSLException;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;

import bulkdownloadapplication.Encryption;

public class Main implements Callable<Integer> {
    private static final Logger logger =
        LoggerFactory.getLogger(Main.class);

    public static final int REQ_LOGIN = 2;
    public static final int REQ_ORDER_LIST = 7;

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
            logger.debug("authentication request: " + request);
            out.println(request.toString());
            if (out.checkError()) {
                throw new SSLException("Server was shut down");
            }

            // read reply
            String jsonReply = in.readLine();
            logger.debug("authentication reply: " + jsonReply);

            if (jsonReply.equals("Bye")) {
                logger.info("server dissconnected");
                return 0;
            }

            JSONObject reply = new JSONObject(jsonReply);
            // TODO - handle errorCode != 0
            /*if (reply.has("errorCode")) {
                logger.error("reply error code: " + reply.get("errorCode"));
                return 1;
            }*/

            // process reply
            if (reply.has("authenticated")
                    && Boolean.valueOf(reply.getBoolean("authenticated"))) {
                logger.info("authentication succces");
            } else {
                logger.error("authentication failure");
                return 1;
            }
        } catch (IOException e) {
            logger.error("unknown authentication failure: " + e);
            return 1;
        }

        // list bulk orders
        try {
            // initialize request
            JSONObject request = new JSONObject();
            request.put("requestType", REQ_ORDER_LIST);
            request.put("message", ""); 

            // send request
            logger.debug("bulk orders list request: " + request);
            out.println(request.toString());
            if (out.checkError()) {
                throw new SSLException("Server was shut down");
            }

            // read reply
            String jsonReply = in.readLine();
            logger.debug("bulk orders list reply: " + jsonReply);

            if (jsonReply.equals("Bye")) {
                logger.info("server dissconnected");
                return 0;
            }

            JSONObject reply = new JSONObject(jsonReply);
            // TODO - handle errorCode != 0
            /*if (reply.has("errorCode")) {
                logger.error("reply error code: " + reply.get("errorCode"));
                return 1;
            }*/

            logger.trace("authentication response: " + reply);

            // TODO - process orders
            System.out.println(jsonReply);
        } catch (IOException e) {
            logger.error("unknown authentication failure: " + e);
            return 1;
        }

        // disconnect ssl socket
        try {
            out.close();
            in.close();
            socket.close();
        } catch (IOException e) {
            logger.error("unknown ssl socket close failure: " + e);
            return 1;
        } 

        // return success
		return 0;
	}
}
