// Multitouch gesture injector for the Android emulator (static x86_64 binary).
// Usage: inject /dev/input/eventN script
// Script lines (coordinates in raw device units 0..32767):
//   d <slot> <x> <y>   finger down      m <slot> <x> <y>   move
//   u <slot>           finger up        s                  sync (end of frame)
//   w <ms>             sleep
#include <fcntl.h>
#include <linux/input.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <time.h>
#include <unistd.h>

static int fd;
static int tracking = 100;

static void emit(int type, int code, int value) {
  struct input_event e;
  memset(&e, 0, sizeof e);
  e.type = type;
  e.code = code;
  e.value = value;
  if (write(fd, &e, sizeof e) != sizeof e) { perror("write"); exit(1); }
}

int main(int argc, char **argv) {
  if (argc != 3) { fprintf(stderr, "usage: inject device script\n"); return 2; }
  fd = open(argv[1], O_WRONLY);
  if (fd < 0) { perror("open device"); return 1; }
  FILE *in = fopen(argv[2], "r");
  if (!in) { perror("open script"); return 1; }
  char op;
  int a, x, y, down = 0;
  while (fscanf(in, " %c", &op) == 1) {
    switch (op) {
      case 'd':
        fscanf(in, "%d %d %d", &a, &x, &y);
        emit(EV_ABS, ABS_MT_SLOT, a);
        emit(EV_ABS, ABS_MT_TRACKING_ID, tracking++);
        emit(EV_ABS, ABS_MT_POSITION_X, x);
        emit(EV_ABS, ABS_MT_POSITION_Y, y);
        emit(EV_ABS, ABS_MT_PRESSURE, 50);
        if (down++ == 0) emit(EV_KEY, BTN_TOUCH, 1);
        break;
      case 'm':
        fscanf(in, "%d %d %d", &a, &x, &y);
        emit(EV_ABS, ABS_MT_SLOT, a);
        emit(EV_ABS, ABS_MT_POSITION_X, x);
        emit(EV_ABS, ABS_MT_POSITION_Y, y);
        break;
      case 'u':
        fscanf(in, "%d", &a);
        emit(EV_ABS, ABS_MT_SLOT, a);
        emit(EV_ABS, ABS_MT_TRACKING_ID, -1);
        if (--down == 0) emit(EV_KEY, BTN_TOUCH, 0);
        break;
      case 's':
        emit(EV_SYN, SYN_REPORT, 0);
        break;
      case 'w': {
        fscanf(in, "%d", &a);
        struct timespec t = {a / 1000, (a % 1000) * 1000000L};
        nanosleep(&t, NULL);
        break;
      }
      default:
        fprintf(stderr, "bad op %c\n", op);
        return 1;
    }
  }
  return 0;
}
