import com.reviiser.bloom.core.*;
import java.io.*;

/** Writes the planes for a seed, so they can be compared against what the Python painter makes. */
public class SceneDump {
    public static void main(String[] a) throws Exception {
        long seed = Long.parseLong(a[0]);
        Scene s = Scene.paintOnly(480, 270, seed);
        try (OutputStream o = new BufferedOutputStream(new FileOutputStream(a[1]))) {
            for (int[] p : new int[][]{s.base, s.fg, s.ground, s.water}) for (int v : p) o.write(v);
        }
    }
}
