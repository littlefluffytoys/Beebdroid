# The ARMv7 is significanly faster due to the use of the hardware FPU
APP_ABI := armeabi armeabi-v7a
APP_PLATFORM := android-8

ifneq ($(APP_OPTIM),debug)
  APP_CFLAGS += -O3
endif
