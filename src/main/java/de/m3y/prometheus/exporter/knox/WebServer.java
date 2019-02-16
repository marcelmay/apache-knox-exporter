package de.m3y.prometheus.exporter.knox;

import java.io.File;
import java.net.InetSocketAddress;

import io.prometheus.client.exporter.MetricsServlet;
import io.prometheus.client.hotspot.DefaultExports;
import org.apache.log4j.Level;
import org.apache.log4j.spi.RootLogger;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;

public class WebServer {

    private Server server;
    private KnoxCollector knoxCollector;

    WebServer configure(ConfigLoader configLoader, String address, int port) {
        // Metrics
        knoxCollector = new KnoxCollector(configLoader);
        knoxCollector.register();

        DefaultExports.initialize();

        final BuildInfoExporter buildInfo = new BuildInfoExporter("knox_exporter_",
                "knox_exporter").register();

        // Jetty
        InetSocketAddress inetAddress = new InetSocketAddress(address, port);
        server = new Server(inetAddress);
        ServletContextHandler context = new ServletContextHandler();
        context.setContextPath("/");
        server.setHandler(context);
        context.addServlet(new ServletHolder(new MetricsServlet()), "/metrics");
        context.addServlet(new ServletHolder(new HomePageServlet(configLoader, buildInfo)), "/");

        return this;
    }

    Server start() throws Exception {
        server.start();
        return server;
    }

    public void shutdown() throws Exception {
        server.stop();
        knoxCollector.shutdown();
    }

    public static void main(String[] args) throws Exception {
        if (args.length < 3) {
            System.out.println("Usage: WebServer [-Dlog.level=[WARN|INFO|DEBUG]] <hostname> <port> <yml configuration file>"); // NOSONAR
            System.out.println(); // NOSONAR
            System.exit(1);
        }

        RootLogger.getRootLogger().setLevel(Level.toLevel(System.getProperty("log.level"), Level.INFO));

        final String configFile = args[2];
        final int port = Integer.parseInt(args[1]);
        ConfigLoader configLoader = ConfigLoader.forFile(new File(configFile));
        new WebServer().configure(configLoader, args[0], port).start().join();
    }
}
