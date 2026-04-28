#include <ApplicationServices/ApplicationServices.h>
#include <math.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>

static int rounded_away_from_zero(double value) {
  if (value > 0.0) {
    return (int) ceil(value);
  }
  if (value < 0.0) {
    return (int) floor(value);
  }
  return 0;
}

static void post_control_scroll(double amount) {
  if (!isfinite(amount) || amount == 0.0) {
    return;
  }

  const double wheel_delta = -amount;
  int wheel = rounded_away_from_zero(wheel_delta);
  if (wheel == 0) {
    return;
  }

  CGEventSourceRef source = CGEventSourceCreate(kCGEventSourceStateHIDSystemState);
  CGEventRef event = CGEventCreateScrollWheelEvent(source, kCGScrollEventUnitPixel, 1, wheel);
  if (!event) {
    if (source) {
      CFRelease(source);
    }
    return;
  }

  CGEventSetFlags(event, kCGEventFlagMaskControl);
  CGEventSetIntegerValueField(event, kCGScrollWheelEventIsContinuous, 1);
  CGEventSetIntegerValueField(event, kCGScrollWheelEventScrollPhase, kCGScrollPhaseChanged);
  CGEventSetDoubleValueField(event, kCGScrollWheelEventPointDeltaAxis1, wheel_delta);
  CGEventSetDoubleValueField(event, kCGScrollWheelEventFixedPtDeltaAxis1, wheel_delta * 65536.0);
  CGEventPost(kCGSessionEventTap, event);

  CFRelease(event);
  if (source) {
    CFRelease(source);
  }
}

int main(void) {
  char line[128];
  while (fgets(line, sizeof(line), stdin) != NULL) {
    if (strncmp(line, "quit", 4) == 0) {
      break;
    }
    if (strncmp(line, "pinch ", 6) == 0) {
      post_control_scroll(strtod(line + 6, NULL));
      fflush(stdout);
    }
  }
  return 0;
}
