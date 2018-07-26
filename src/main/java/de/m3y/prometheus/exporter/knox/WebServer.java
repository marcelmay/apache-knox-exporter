package de.m3y.prometheus.exporter.knox;

import java.io.FileReader;
import java.net.InetSocketAddress;

import io.prometheus.client.exporter.MetricsServlet;
import io.prometheus.client.hotspot.MemoryPoolsExports;
import org.apache.log4j.Level;
import org.apache.log4j.spi.RootLogger;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.yaml.snakeyaml.Yaml;

public class WebServer {

    private Server server;
    private KnoxCollector knoxCollector;

    WebServer configure(Config config, String address, int port) {
        // Metrics
        knoxCollector = new KnoxCollector(config);
        knoxCollector.register();

        new MemoryPoolsExports().register();

        final BuildInfoExporter buildInfo = new BuildInfoExporter("knox_exporter_",
                "knox_exporter").register();

        // Jetty
        InetSocketAddress inetAddress = new InetSocketAddress(address, port);
        server = new Server(inetAddress);
        ServletContextHandler context = new ServletContextHandler();
        context.setContextPath("/");
        server.setHandler(context);
        context.addServlet(new ServletHolder(new MetricsServlet()), "/metrics");
        context.addServlet(new ServletHolder(new HomePageServlet(config, buildInfo)), "/");

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
        if (args.length < 4) {
            System.out.println("Usage: WebServer [-Dlog.level=[WARN|INFO|DEBUG]] <hostname> <port> <yml configuration file>"); // NOSONAR
            System.out.println();
            System.exit(1);
        }

        RootLogger.getRootLogger().setLevel(Level.toLevel(System.getProperty("log.level"), Level.INFO));

        Config config;
        try (FileReader reader = new FileReader(args[2])) {
            config = new Yaml().loadAs(reader, Config.class);
        }

        new WebServer().configure(config, args[0], Integer.parseInt(args[1])).start().join();
    }
}
