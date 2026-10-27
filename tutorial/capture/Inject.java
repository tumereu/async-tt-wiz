// Framework-level multitouch injector, run with app_process (like scrcpy's server):
//   CLASSPATH=/data/local/tmp/inject.jar app_process / Inject script.txt
// Script lines, coordinates in display pixels:
//   d <slot> <x> <y>   finger down      m <slot> <x> <y>   move
//   u <slot>           finger up        s                  sync: send the pending changes
//   w <ms>             sleep
// Raw /dev/input injection does not work on these emulators: with a `wm size` override,
// the input reader clips touches against the physical frame in the wrong coordinate space.
import android.os.SystemClock;
import android.view.InputDevice;
import android.view.InputEvent;
import android.view.MotionEvent;
import java.io.BufferedReader;
import java.io.FileReader;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeMap;

public final class Inject {
  private static Object inputManager;
  private static Method inject;
  private static long downTime;
  // slot -> {x, y}; insertion-ordered by slot number for stable pointer indices.
  private static final TreeMap<Integer, float[]> active = new TreeMap<>();

  public static void main(String[] args) throws Exception {
    Class<?> cls = Class.forName("android.hardware.input.InputManager");
    inputManager = cls.getMethod("getInstance").invoke(null);
    inject = cls.getMethod("injectInputEvent", InputEvent.class, int.class);
    List<Integer> downs = new ArrayList<>();
    List<Integer> ups = new ArrayList<>();
    TreeMap<Integer, float[]> pending = new TreeMap<>();
    boolean moved = false;
    try (BufferedReader in = new BufferedReader(new FileReader(args[0]))) {
      String line;
      while ((line = in.readLine()) != null) {
        String[] p = line.trim().split("\\s+");
        if (p[0].isEmpty()) continue;
        switch (p[0]) {
          case "d":
            pending.put(Integer.parseInt(p[1]), new float[] {Float.parseFloat(p[2]), Float.parseFloat(p[3])});
            downs.add(Integer.parseInt(p[1]));
            break;
          case "m":
            pending.put(Integer.parseInt(p[1]), new float[] {Float.parseFloat(p[2]), Float.parseFloat(p[3])});
            moved = true;
            break;
          case "u":
            ups.add(Integer.parseInt(p[1]));
            break;
          case "s":
            // Moves first, then each new finger, then each lifted finger, as separate events.
            for (Integer slot : pending.keySet()) {
              if (active.containsKey(slot)) active.put(slot, pending.get(slot));
            }
            if (moved && !active.isEmpty()) send(MotionEvent.ACTION_MOVE, -1);
            for (Integer slot : downs) {
              if (active.isEmpty()) downTime = SystemClock.uptimeMillis();
              active.put(slot, pending.get(slot));
              send(active.size() == 1 ? MotionEvent.ACTION_DOWN : MotionEvent.ACTION_POINTER_DOWN, slot);
            }
            for (Integer slot : ups) {
              send(active.size() == 1 ? MotionEvent.ACTION_UP : MotionEvent.ACTION_POINTER_UP, slot);
              active.remove(slot);
            }
            downs.clear();
            ups.clear();
            pending.clear();
            moved = false;
            break;
          case "w":
            SystemClock.sleep(Long.parseLong(p[1]));
            break;
          default:
            throw new IllegalArgumentException("bad line: " + line);
        }
      }
    }
  }

  private static void send(int action, int slot) throws Exception {
    int n = active.size();
    MotionEvent.PointerProperties[] props = new MotionEvent.PointerProperties[n];
    MotionEvent.PointerCoords[] coords = new MotionEvent.PointerCoords[n];
    int index = 0;
    int actionIndex = 0;
    for (Integer s : active.keySet()) {
      props[index] = new MotionEvent.PointerProperties();
      props[index].id = s;
      props[index].toolType = MotionEvent.TOOL_TYPE_FINGER;
      coords[index] = new MotionEvent.PointerCoords();
      coords[index].x = active.get(s)[0];
      coords[index].y = active.get(s)[1];
      coords[index].pressure = 1f;
      coords[index].size = 0.05f;
      if (s == slot) actionIndex = index;
      index++;
    }
    if (action == MotionEvent.ACTION_POINTER_DOWN || action == MotionEvent.ACTION_POINTER_UP) {
      action |= actionIndex << MotionEvent.ACTION_POINTER_INDEX_SHIFT;
    }
    MotionEvent event = MotionEvent.obtain(downTime, SystemClock.uptimeMillis(), action, n, props, coords, 0, 0, 1f, 1f,
        0, 0, InputDevice.SOURCE_TOUCHSCREEN, 0);
    inject.invoke(inputManager, event, 0);
    event.recycle();
  }
}
