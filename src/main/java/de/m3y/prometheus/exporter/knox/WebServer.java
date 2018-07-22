package de.m3y.prometheus.exporter.knox;

import java.io.FileReader;
import java.net.InetSocketAddress;

import io.prometheus.client.exporter.MetricsServlet;
import io.prometheus.client.hotspot.MemoryPoolsExports;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.yaml.snakeyaml.Yaml;

public class WebServer {

    private Server server;

    WebServer configure(Config config, String address, int port, String knoxGatewayUrl) {
        // Metrics
        final KnoxCollector knoxCollector = new KnoxCollector(knoxGatewayUrl, config);
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
        context.addServlet(new ServletHolder(new HomePageServlet(knoxGatewayUrl, config, buildInfo)), "/");

        return this;
    }

    Server start() throws Exception {
        server.start();
        return server;
    }

    public static void main(String[] args) throws Exception {
        if (args.length < 4) {
            System.err.println("Usage: WebServer <hostname> <port> <knox gateway url> <yml configuration file>"); // NOSONAR
            System.exit(1);
        }

        Config config;
        try (FileReader reader = new FileReader(args[3])) {
            config = new Yaml().loadAs(reader, Config.class);
        }

        new WebServer().configure(config, args[0], Integer.parseInt(args[1]), args[2]).start().join();
    }
}
