import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.List;

/**
 * A simple telnet command server.
 */
class Server implements Runnable {

    private final int port;

    Server(int port) {
        this.port = port;
    }

    static void start(int port) {
         new Thread(new Server(port)).start();
    }

    @Override
    public void run() {
        try (ServerSocket ssocket = new ServerSocket(port)) {
            while (true) {
                Socket socket = ssocket.accept();
                new Thread(new Handler(socket)).start();
            }
        } catch (IOException e) {
            throw new RuntimeException("Something went wrong", e);
        }
    }

    private static class Handler implements Runnable {

        private final Socket socket;

        Handler(Socket socket) {
            this.socket = socket;
        }

        @Override
        public void run() {
            // retrieve a command, run it, and send results back
            try (
                    BufferedReader in = new BufferedReader(
                        new InputStreamReader(socket.getInputStream()));
                    BufferedWriter out = new BufferedWriter(
                            new OutputStreamWriter(socket.getOutputStream()))
            ) {
                String cmd;
                while ((cmd = in.readLine()) != null) {
                    List<String> output = new Command(cmd).getOutput();
                    for (String line : output) {
                        out.write(line);
                        out.newLine();
                    }
                    out.flush();
                }
            } catch (IOException e) {
                throw new RuntimeException("Something went wrong", e);
            }
        }
    }

}
