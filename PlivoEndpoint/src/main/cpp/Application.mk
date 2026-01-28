APP_STL     := c++_shared
APP_ABI := armeabi-v7a arm64-v8a x86 x86_64
APP_USE_SSL := true
# ideally, this should be set to "all"
APP_PLATFORM := android-21

# ============================================
# 16 KB PAGE SIZE ALIGNMENT FLAGS
# ============================================
# REQUIRED: NDK r28+ for 16 KB page size support.
# These flags ensure native libraries are linked with 16 KB alignment
# and apply globally to all native modules in this application.
# 
# WARNING: Building with NDK < r28 will result in 4 KB aligned libraries
# which will NOT work on Android 15+ devices with 16 KB page sizes.
APP_LDFLAGS := -Wl,-z,max-page-size=16384 -Wl,-z,common-page-size=16384
