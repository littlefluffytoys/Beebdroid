LOCAL_PATH := $(call my-dir)

include $(CLEAR_VARS)

LOCAL_CFLAGS := -DANDROID_NDK \
                -DDISABLE_IMPORTGL
                
LOCAL_MODULE    := bbcmicro

ifeq ($(TARGET_ARCH),arm)
    LOCAL_CFLAGS +=  -march=armv6t2 -O9
    LOCAL_SRC_FILES := 6502asm_arm.S
endif
ifeq ($(TARGET_ARCH),x86)
    LOCAL_CFLAGS += -m32
    LOCAL_SRC_FILES := 6502asm_x86.S
endif

LOCAL_SRC_FILES += \
    6502.c \
    8271.c \
    adc.c \
    disc.c \
    main.c \
    sound.c \
    ssd.c \
    sysvia.c \
    uservia.c \
    video.c \
    importgl.c
    
LOCAL_LDLIBS += -lm -llog -ljnigraphics -lz -lGLESv1_CM

include $(BUILD_SHARED_LIBRARY)
