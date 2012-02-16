LOCAL_PATH := $(call my-dir)

include $(CLEAR_VARS)

## remove -ffunction-sections and -fomit-frame-pointer from the default compile flags
#TARGET_thumb_release_CFLAGS := $(filter-out -ffunction-sections,$(TARGET_thumb_release_CFLAGS))
#TARGET_thumb_release_CFLAGS := $(filter-out -fomit-frame-pointer,$(TARGET_thumb_release_CFLAGS))
#TARGET_CFLAGS := $(filter-out -ffunction-sections,$(TARGET_CFLAGS))

## include libandprof.a in the build
#include $(CLEAR_VARS)
#LOCAL_MODULE := andprof
#LOCAL_SRC_FILES := $(TARGET_ARCH_ABI)/libandprof.a
#include $(PREBUILT_STATIC_LIBRARY)


## this is your shared library
#include $(CLEAR_VARS)
## compile with profiling
#LOCAL_CFLAGS := -pg
#LOCAL_STATIC_LIBRARIES := andprof

LOCAL_CFLAGS := -DANDROID_NDK \
                -DDISABLE_IMPORTGL
                
LOCAL_MODULE    := bbcmicro
LOCAL_SRC_FILES := \
    6502asm.S \
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
