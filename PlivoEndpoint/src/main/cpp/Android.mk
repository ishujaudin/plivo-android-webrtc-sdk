# A simple test for the minimal standard C++ library
#

LOCAL_PATH := $(call my-dir)


#include $(CLEAR_VARS)
#LOCAL_MODULE := local_gperf
#LOCAL_SRC_FILES := $(LOCAL_PATH)/../../../distribution/gperf/lib/x86/libgperf.so
#LOCAL_EXPORT_C_INCLUDES := $(LOCAL_PATH)/../../../distribution/gperf/include

#include $(PREBUILT_SHARED_LIBRARY)

include $(CLEAR_VARS)
LOCAL_MODULE := local_ssl
LOCAL_SRC_FILES :=$(LOCAL_PATH)/../../../distribution/openssl/$(TARGET_ARCH_ABI)/lib/libssl.a
LOCAL_EXPORT_C_INCLUDES := $(LOCAL_PATH)/../../../distribution/openssl/$(TARGET_ARCH_ABI)/include
include $(PREBUILT_STATIC_LIBRARY)

include $(CLEAR_VARS)
LOCAL_MODULE := local_crypto
LOCAL_SRC_FILES :=$(LOCAL_PATH)/../../../distribution/openssl/$(TARGET_ARCH_ABI)/lib/libcrypto.a
LOCAL_EXPORT_C_INCLUDES := $(LOCAL_PATH)/../../../distribution/openssl/$(TARGET_ARCH_ABI)/include
include $(PREBUILT_STATIC_LIBRARY)

#include $(CLEAR_VARS)
#LOCAL_MODULE := local_ssl
#LOCAL_SRC_FILES := $(LOCAL_PATH)/../../../distribution/openssl/x86/lib/libssl.so
#LOCAL_EXPORT_C_INCLUDES := $(LOCAL_PATH)/../../../distribution/openssl/x86/include
#include $(PREBUILT_SHARED_LIBRARY)

#include $(CLEAR_VARS)
#LOCAL_MODULE := local_crypto
#LOCAL_SRC_FILES := $(LOCAL_PATH)/../../../distribution/openssl/x86/lib/libcrypto.so
#LOCAL_EXPORT_C_INCLUDES := $(LOCAL_PATH)/../../../distribution/openssl/x86/include
#include $(PREBUILT_SHARED_LIBRARY)

#include $(CLEAR_VARS)
#LOCAL_MODULE := local_resip
#LOCAL_SRC_FILES := $(LOCAL_PATH)/../../../distribution/resip/libs/$(TARGET_ARCH_ABI)/libresip-1.12.so
#LOCAL_EXPORT_C_INCLUDES := $(LOCAL_PATH)/../../../distribution/resip/include
##LOCAL_EXPORT_C_INCLUDES :=  $(LOCAL_PATH)/../../../../../../dev/new_plivo-resip-krishna/plivo-resip/resiprocate
#include $(PREBUILT_SHARED_LIBRARY)

#include $(CLEAR_VARS)
#LOCAL_MODULE := local_rutil
#LOCAL_SRC_FILES := $(LOCAL_PATH)/../../../distribution/resip/libs/$(TARGET_ARCH_ABI)/librutil-1.12.so
#LOCAL_EXPORT_C_INCLUDES := $(LOCAL_PATH)/../../../distribution/resip/include
#include $(PREBUILT_SHARED_LIBRARY)

#include $(CLEAR_VARS)
#LOCAL_MODULE := local_ares
#LOCAL_SRC_FILES := $(LOCAL_PATH)/../../../distribution/resip/libs/$(TARGET_ARCH_ABI)/libresipares-1.12.so
#LOCAL_EXPORT_C_INCLUDES := $(LOCAL_PATH)/../../../distribution/resip/include
#include $(PREBUILT_SHARED_LIBRARY)
#
#include $(CLEAR_VARS)
#LOCAL_MODULE := local_dum
#LOCAL_SRC_FILES := $(LOCAL_PATH)/../../../distribution/resip/libs/$(TARGET_ARCH_ABI)/libdum-1.12.so
#LOCAL_EXPORT_C_INCLUDES := $(LOCAL_PATH)/../../../distribution/resip/include
#include $(PREBUILT_SHARED_LIBRARY)

include $(CLEAR_VARS)
LOCAL_MODULE := rtcsip_jni
#FILE_LIST := $(wildcard $(LOCAL_PATH)/*.cxx)
#traverse all the directory and subdirectory
define walk
  $(wildcard $(1)) $(foreach e, $(wildcard $(1)/*), $(call walk, $(e)))
endef

#find all the file recursively under jni/
ALLFILES = $(call walk, $(LOCAL_PATH))
FILE_LIST := $(filter %.cxx %.c, $(ALLFILES))

LOCAL_SRC_FILES := $(FILE_LIST:$(LOCAL_PATH)/%=%) ../cpp/rtcsip_jni.cpp ../cpp/SipControllerCore.cpp
#LOCAL_SRC_FILES := $(FILE_LIST:$(LOCAL_PATH)/%=%) $(LOCAL_PATH)/  ../cpp/rtcsip_jni.cpp ../cpp/SipControllerCore.cpp
#LOCAL_SRC_FILES :=  $(LOCAL_PATH)/  ../cpp/rtcsip_jni.cpp ../cpp/SipControllerCore.cpp
#LOCAL_C_INCLUDES := $(LOCAL_PATH)/../../../src/main/cpp

# ============================================
# 16 KB PAGE SIZE ALIGNMENT FLAGS
# ============================================
# Linker flags for 16 KB page size alignment (required for Android 15+)
LOCAL_LDLIBS    := -Wl,--gc-sections -Wl,-z,max-page-size=16384 -Wl,-z,common-page-size=16384 -llog -landroid

# C flags (applies to both C and C++ files)
LOCAL_CFLAGS := -Os -fomit-frame-pointer -ffunction-sections -fdata-sections -fstack-protector -DUSE_SSL -DUSE_ARES -DUSE_IPV6

# C++ flags (applies only to C++ files, not C files)
LOCAL_CPPFLAGS := -std=c++11 -fexceptions -frtti
#ifeq ($(APP_USE_SSL),true)
#    LOCAL_CFLAGS +=
#endif

LOCAL_STATIC_LIBRARIES := local_ssl local_crypto
#LOCAL_SHARED_LIBRARIES := local_resip local_ares local_dum

include $(BUILD_SHARED_LIBRARY)