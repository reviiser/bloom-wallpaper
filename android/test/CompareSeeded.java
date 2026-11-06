import com.reviiser.bloom.core.*;
import java.io.*;

public class CompareSeeded {
    public static void main(String[] a) throws Exception {
        Config c = new Config();
        c.timeOfDay = a[1];
        c.season = a[2];
        c.seed = Long.parseLong(a[3]);
        World world = new World(c, 480, 270, new File(a[0]), false);
        System.out.print(Fingerprint.of(world, Integer.parseInt(a[4])));
    }
}
