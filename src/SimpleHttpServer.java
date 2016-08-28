import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpExchange;
import java.net.InetSocketAddress;
import java.net.URI;
import java.io.OutputStream;
import java.io.IOException;
import java.io.File;
import java.nio.file.Files;

/*
 * The server returns requested files from ${used.dir}.
 * Directory browsing is not supported (403 error appears in this case).
 */
public class SimpleHttpServer implements HttpHandler {

    private static final int DEFAULT_PORT = 80;

    private HttpServer httpServer;

    /**
     * This is the maximum number of queued incoming connections
     * to allow on the listening socket.
     */
    static private final int MAX_CONNECTIONS = Integer.getInteger(
            "maxConnections", 100);

    /**
     * Entry point.
     *
     * @param args[0] Server port number.
     * @throws java.io.IOException if something went wrong
     */
    public static void main(String[] args) throws IOException {
        int port = DEFAULT_PORT;
        if (args.length > 0) {
            port = Integer.parseInt(args[0]);
        }
        create(port).start();
    }

    static SimpleHttpServer create(int port) throws IOException {
        SimpleHttpServer instance = new SimpleHttpServer();
        HttpServer httpServer = HttpServer.create(
                new InetSocketAddress(port), MAX_CONNECTIONS);
        httpServer.createContext("/", instance);
        httpServer.setExecutor(null);
        instance.httpServer = httpServer;

        return instance;
    }

    void start() {
        httpServer.start();
    }

    void stop() {
        httpServer.stop(0);
    }

    /**
     * A handler which is invoked to process HTTP exchanges.
     */
    @Override
    public void handle(HttpExchange t) throws IOException {
        URI requestUri = t.getRequestURI();
        System.out.println("SimpleHttpServer: requested URI: " + requestUri);

        String path = System.getProperty("user.dir") + requestUri.toString();
        File file = new File(path);
        if (!file.exists()) {
            System.out.println("SimpleHttpServer: requested path '" + path
                    + "' does not exist -> 404 error");
            t.sendResponseHeaders(404, 0);
        } else if (file.isDirectory()) {
            System.out.println("SimpleHttpServer: requested path '" + path
                    + "' is directory -> 403 error");
            t.sendResponseHeaders(403, 0);
        } else {
            System.out.println("SimpleHttpServer: requested path '" + path
                    + "' is file -> return requested file");
            t.sendResponseHeaders(200, 0);
            try (OutputStream os = t.getResponseBody()) {
                os.write(Files.readAllBytes(file.toPath()));
            }
        }
    }
}
