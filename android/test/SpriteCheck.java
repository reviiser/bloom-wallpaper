import com.reviiser.bloom.core.*;
import java.nio.file.*;
import java.util.*;

/** Reads sprites drawn by the Python version and checks the Java builders make exactly the same ones. */
public class SpriteCheck {
    @SuppressWarnings("unchecked")
    public static void main(String[] a) throws Exception {
        List<Object> cases = (List<Object>) Json.parse(new String(Files.readAllBytes(Paths.get(a[0])), "UTF-8"));
        Map<String, int[]> tally = new TreeMap<>();
        int shown = 0;
        for (Object o : cases) {
            Map<String, Object> c = (Map<String, Object>) o;
            String kind = (String) c.get("kind");
            List<int[]> got = Sprites.bevel(Sprites.build(kind, new G((Map<String, ?>) c.get("g")), (long) Json.num(c.get("seed")), Json.num(c.get("scale"))), Json.integer(c.get("light")));
            List<Object> want = (List<Object>) c.get("pix");
            boolean same = got.size() == want.size();
            int at = -1;
            for (int i = 0; same && i < got.size(); i++) {
                double[] w = Json.doubles(want.get(i));
                int[] g = got.get(i);
                if (g[0] != (int) w[0] || g[1] != (int) w[1] || g[2] != (int) w[2]) { same = false; at = i; }
            }
            int[] t = tally.get(kind);
            if (t == null) tally.put(kind, t = new int[2]);
            t[same ? 0 : 1]++;
            if (!same && shown++ < 6) System.out.println("DIFF " + kind + " seed " + c.get("seed") + " scale " + c.get("scale") + " sizes " + got.size() + "/" + want.size() + " at " + at + " g=" + c.get("g"));
        }
        for (Map.Entry<String, int[]> e : tally.entrySet()) System.out.println(e.getKey() + " same=" + e.getValue()[0] + " diff=" + e.getValue()[1]);
    }
}
