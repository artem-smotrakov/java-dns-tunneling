import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.StringTokenizer;

class Command {

    private final List<String> output;

    // run a command
    Command(String cmd) {
        List<String> cmds = new ArrayList<>();
        StringTokenizer st = new StringTokenizer(cmd);
        while (st.hasMoreTokens()) {
            cmds.add(st.nextToken());
        }

        ProcessBuilder pb = new ProcessBuilder(cmds);
        pb.redirectErrorStream(true);
        Process p;
        output = new ArrayList<>();
        try {
            p = pb.start();
            // TODO: add a timeout if possible
            p.waitFor();
            try (BufferedReader out = new BufferedReader(
                    new InputStreamReader(p.getInputStream()))) {

                String line;
                while ((line = out.readLine()) != null) {
                    output.add(line);
                }
            }
        } catch (IOException | InterruptedException e) {
            output.add("Error: " + e.getMessage());
        }
    }

    List<String> getOutput() {
        return output;
    }
}
