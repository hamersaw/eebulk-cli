package io.blackpine.eebulk;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import java.io.File;
import java.util.concurrent.Callable;

public class Main implements Callable<Integer> {
    private static final Logger logger =
        LoggerFactory.getLogger(Main.class);

    @Parameters(index = "0", description = "EROS username")
	private String username;

    @Parameters(index = "1", description = "EROS password")
    private String password;

    @Option(names = {"-d", "--directory"},
        description = "Storage directory [default: 'data']")
    private File directory = new File("data/");

    @Option(names = {"-h", "--host"},
        description = "Storage directory [default: 'eebulk.cr.usgs.gov']")
    private String host = "eebulk.cr.usgs.gov";

    @Option(names = {"-o", "--order"},
        description = "Order number (-1 for all orders) [default: -1]")
    private int order = -1;

    public static void main(String[] args) {
        int exitCode = new CommandLine(new Main()).execute(args);
        System.exit(exitCode);
    }

    @Override
	public Integer call() throws Exception {
        logger.info("starting application");
        logger.debug("-Dusername: " + this.username);
        logger.debug("-Dpassword: " + this.password);

        // create storage directory if it does not exist
        logger.debug("-Ddirectory: " + this.directory);
        if (!this.directory.exists()) {
            logger.info("creating directory '" + this.directory + "'");
            this.directory.mkdirs();
        }

        // TODO - connect to eebulk server

        // return success
		return 0;
	}
}
