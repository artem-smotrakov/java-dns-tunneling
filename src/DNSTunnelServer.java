import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;

// TODO: multiple sessions
public class DNSTunnelServer implements Runnable, DNSTunnelConstants {

    private static final boolean DEBUG = true;
    private static final int DEFAULT_PORT = 53;
    private static final int BUFFER_SIZE = 512;

    private final Queue<String> commands = new LinkedList<>();
    private final int port;

    DNSTunnelServer(int port) {
        this.port = port;
    }

    static DNSTunnelServer start(int port) {
        DNSTunnelServer server = new DNSTunnelServer(port);
        new Thread(server).start();
        return server;
    }

    public static void main(String[] args) {
        // start a server instance
        DNSTunnelServer server = DNSTunnelServer.start(DEFAULT_PORT);

        // read commands
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(System.in))) {

            while(true) {
                String command = reader.readLine();
                server.addCommand(command);
            }
        } catch(IOException e) {
            error("Unexpected exception: " + e);
        }
    }

    void addCommand(String command) {
        synchronized (commands) {
            commands.add(command);
        }
    }

    @Override
    public void run() {
        try (DatagramSocket socket = new DatagramSocket(port)) {
            socket.setSoTimeout(0);
            byte[] buf = new byte[BUFFER_SIZE];
            debug("started");
            while (true) {
                DatagramPacket packet = new DatagramPacket(buf, BUFFER_SIZE);
                socket.receive(packet);
                InetAddress remoteAddress = packet.getAddress();
                int remotePort = packet.getPort();
                try {
                    packet = handle(packet, remoteAddress, remotePort);
                } catch (IOException e) {
                    // TODO: send RCODE=2
                    debug("error: " + e.getMessage());
                    continue;
                }
                debug("send a response to " + remoteAddress + ":" + remotePort);
                socket.send(packet);
            }
        } catch (IOException e) {
            // TODO: don't quit
            error("Unexpected exception: " + e);
        }
        debug("finished");
    }

    // Refer to http://tools.ietf.org/html/rfc1035 for details
    private DatagramPacket handle(DatagramPacket packet, InetAddress address, int port)
            throws IOException {

        debug("received a request");
        try (
                DataInputStream in = new DataInputStream(
                    new ByteArrayInputStream(packet.getData()));
                ByteArrayOutputStream bos = new ByteArrayOutputStream();
                DataOutputStream out = new DataOutputStream(bos)) {

            // parse a DNS request

            // read ID
            short id = in.readShort();

            // skip flags
            in.skipBytes(2);

            // read questions count (QDCOUNT)
            short qdcount = in.readShort();
            if (qdcount < 1) {
                throw new IOException("Wrong QDCOUNT: " + qdcount);
            }

            // skip ANCOUNT, NSCOUNT, ARCOUNT
            in.skipBytes(6);

            // read first question
            int len;
            List<byte[]> components = new ArrayList<>();
            while ((len = in.readByte()) > 0) {
                byte[] component = new byte[len];
                in.read(component, 0, len);
                components.add(component);
            }

            if (components.size() < 2) {
                throw new IOException("Wrong QNAME");
            }

            // TODO: check domain name

            short qtype = in.readShort();
            short qclass = in.readShort();

            processData(components.get(0));

            // build DNS response
            // write ID
            out.writeShort(id);

            // write flags
            // set QR = 1 which means this message is a response
            // set AA = 1 which means that the responding name server is an
            // authority for the domain name
            short flags = 0;
            flags = (short) (flags | QR_RESPONSE | AA_BIT);

            out.writeShort(flags);

            // write QDCOUNT = 1
            out.writeShort(1);

            // TODO: if an error occured while sending a command,
            //       the command should be returned to the queue
            String command;
            synchronized (commands) {
                command = commands.poll();
            }

            short ancount = (short) (command == null ? 1 : 2);

            // write ANCOUNT
            out.writeShort(ancount);

            // write NSCOUNT
            out.writeShort(0);

            // write ARCOUNT
            out.writeShort(0);

            // write the question (Java DNS client rejects responses without it)
            for (byte[] component : components) {
                out.write(component.length);
                out.write(component, 0, component.length);
            }
            out.write(0);
            out.writeShort(qtype);
            out.writeShort(qclass);

            // write an answer, A IN 1.2.3.4
            out.write(buildAnswerRecord(components, new byte[] {1, 2, 3, 4}));

            // send a command if available
            if (command != null) {
                debug("send a command: " + command);
                out.write(buildTxtRecord(components, command));
            }

            out.flush();
            byte[] buf = bos.toByteArray();
            return new DatagramPacket(buf, buf.length, address, port);
        }
    }

    private byte[] buildAnswerRecord(List<byte[]> name, byte[] data)
            throws IOException {

        try (ByteArrayOutputStream bos = new ByteArrayOutputStream();
                DataOutputStream out = new DataOutputStream(bos)) {

            // write NAME
            for (byte[] component : name) {
                out.write(component.length);
                out.write(component, 0, component.length);
            }
            out.write(0);

            // write QTYPE and QCLASS
            out.writeShort(QType.A.getValue());
            out.writeShort(Class.IN.getValue());

            // write TTL=0
            out.write(new byte[] {0, 0, 0, 0});

            // write an IP address (RDLENGTH and RDATA)
            out.writeShort(data.length);
            out.write(data, 0, data.length);

            out.flush();
            return bos.toByteArray();
        }
    }

    private byte[] buildTxtRecord(List<byte[]> name, String text)
            throws IOException {

        try (ByteArrayOutputStream bos = new ByteArrayOutputStream();
                DataOutputStream out = new DataOutputStream(bos)) {

            // write NAME
            for (byte[] component : name) {
                out.write(component.length);
                out.write(component, 0, component.length);
            }
            out.write(0);

            // Write QTYPE and QCLASS
            out.writeShort(QType.TXT.getValue());
            out.writeShort(Class.IN.getValue());

            // write TTL=0
            out.write(new byte[] {0, 0, 0, 0});

            // write TXT data (RDLENGTH and RDATA)
            byte[] data = text.getBytes("US-ASCII");

            // RDLENGTH
            out.writeShort(data.length + 1);

            // TXT length and text
            out.write(data.length);
            out.write(data, 0, data.length);

            out.flush();
            return bos.toByteArray();
        }
    }

    private void processData(byte[] bytes) throws IOException {
        String data = BASE32.decodeToString(bytes);
        debug("processData(): decoded data = " + data);
        int pos = data.indexOf(SEPARATOR);
        if (pos < 0) {
            throw new IOException("Wrong request, couldn't find a separator");
        }
        String output = data.substring(pos + 1);
        if (!output.isEmpty()) {
            print(output);
        } else {
            debug("processData(): no output");
        }
    }

    private static void debug(String message) {
        if (DEBUG) {
            System.out.println("debug: " + message);
        }
    }

    private static void print(String text) {
        System.out.print(text);
    }

    private static void error(String text) {
        System.out.println("error: " + text);
    }

}
