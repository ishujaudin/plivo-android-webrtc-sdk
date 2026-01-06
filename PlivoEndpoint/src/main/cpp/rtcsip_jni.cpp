#include <android/log.h>

#include "SipControllerCore.h"
//#include "/Users/zeeshan.saiyed/Library/Android/sdk/ndk/android-ndk-r12b/platforms/android-21/arch-x86/usr/include/jni.h"

using namespace rtcsip;
//using namespace webrtc_jni;

class SipControllerHandler;

static JavaVM *g_jvm = NULL;
static jobject g_sipControllerJava;
static jclass g_registrationEventEnum;
static jclass g_callEventEnum;
static SipControllerCore *g_sipControllerCore = NULL;

static SipControllerHandler *g_sipControllerHandler = NULL;


extern "C" {

JNIEXPORT jstring JNICALL Java_com_plivo_endpoint_SipController_getPublicIp
        (JNIEnv *env, jobject obj);

JNIEXPORT void JNICALL Java_com_plivo_endpoint_SipController_init
        (JNIEnv *env, jobject obj, jobject j_settings,jboolean is_debug_mode);

JNIEXPORT void JNICALL Java_com_plivo_endpoint_SipController_deinit
        (__attribute__((unused)) JNIEnv *env, jobject obj);


JNIEXPORT void JNICALL Java_com_plivo_endpoint_SipController_registerUser
        (JNIEnv *env, jobject, jstring j_username, jstring j_password);

JNIEXPORT void JNICALL Java_com_plivo_endpoint_SipController_registerUserToken
        (JNIEnv *env, jobject, jstring j_username, jstring j_password, jstring j_token,jstring j_certId);

JNIEXPORT void JNICALL Java_com_plivo_endpoint_SipController_registerUserTokenHeaders
        (JNIEnv *env, jobject, jstring j_username, jstring j_password, jstring j_token,jstring j_certId, jstring j_proxy,
         jobjectArray j_headerKeys, jobjectArray j_headerValues,jboolean j_isLoginWithJWT);

JNIEXPORT void JNICALL Java_com_plivo_endpoint_SipController_iceGatheringFinish
        (__attribute__((unused)) JNIEnv *env, jobject);

JNIEXPORT void JNICALL Java_com_plivo_endpoint_SipController_networkChange
        (__attribute__((unused)) JNIEnv *env, jobject);

JNIEXPORT void JNICALL Java_com_plivo_endpoint_SipController_networkChangeHeader
        (JNIEnv *env, jobject, jobjectArray j_headerKeys, jobjectArray j_headerValues);


JNIEXPORT void JNICALL Java_com_plivo_endpoint_SipController_iceCandidate
        (JNIEnv *env, jobject, jstring j_mid, jstring j_sdp);

JNIEXPORT void JNICALL Java_com_plivo_endpoint_SipController_unregisterUser
        (JNIEnv *, jobject);

JNIEXPORT void JNICALL Java_com_plivo_endpoint_SipController_unregisterUserHeader
        (JNIEnv *env, jobject, jobjectArray j_headersKeys, jobjectArray j_headerValues);

JNIEXPORT void JNICALL Java_com_plivo_endpoint_SipController_registerTimeOut
        (__attribute__((unused)) JNIEnv *env, jobject, jint time);

JNIEXPORT void JNICALL Java_com_plivo_endpoint_SipController_makeCall
        (JNIEnv *env, jobject, jstring j_sipUri, jstring j_localSDP, jobjectArray j_headersKeys,jobjectArray j_headerValues);

JNIEXPORT void JNICALL Java_com_plivo_endpoint_SipController_answer
        (JNIEnv *, jobject, jstring sdp);

JNIEXPORT void JNICALL Java_com_plivo_endpoint_SipController_rejectCall
        (__attribute__((unused)) JNIEnv *, jobject);

JNIEXPORT void JNICALL Java_com_plivo_endpoint_SipController_sendRinging
        (__attribute__((unused)) JNIEnv *, jobject);

JNIEXPORT void JNICALL Java_com_plivo_endpoint_SipController_endCall
        (__attribute__((unused)) JNIEnv *env, jobject);


class SipControllerHandler
        : public SipRegistrationHandler,
          public SipCallHandler,
          public SipSDPHandler,
          public SipCallInfoHandler,
          public SipLogHandler,
          public SipCallStateHandler {
public:

    virtual void handleSDP(std::string sdp) {
        JNIEnv *env;
        bool isAttached = false;

        if (g_jvm->GetEnv((void **) &env, JNI_VERSION_1_6) != JNI_OK) {
            g_jvm->AttachCurrentThread(&env, NULL);
            isAttached = true;
        }
        jclass j_sipControllerClass = env->GetObjectClass(g_sipControllerJava);

        jmethodID j_midOnCall = env->GetMethodID(j_sipControllerClass, "setRemoteSDP",
                                                 "(Ljava/lang/String;)V");
        jstring j_sdp = env->NewStringUTF(sdp.c_str());
        env->CallVoidMethod(g_sipControllerJava, j_midOnCall, j_sdp);

        if (isAttached)
            g_jvm->DetachCurrentThread();

    }
    virtual void handleRegistration(SipRegistrationEvent event, std::string user)
    {
        JNIEnv *env;
        bool isAttached = false;

        if (g_jvm->GetEnv((void **) &env, JNI_VERSION_1_6) != JNI_OK) {
            g_jvm->AttachCurrentThread(&env, NULL);
            isAttached = true;
        }

        jclass j_sipControllerClass = env->GetObjectClass(g_sipControllerJava);

        jmethodID j_midOnRegistration = env->GetMethodID(j_sipControllerClass, "onRegistration",
                                                         "(Lcom/plivo/endpoint/SipController$RegistrationEvent;Ljava/lang/String;)V");
        jfieldID j_fidRegistrationEvent;
        if (event == SipRegistrationEvent::Registered)
            j_fidRegistrationEvent = env->GetStaticFieldID(g_registrationEventEnum, "REGISTERED",
                                                           "Lcom/plivo/endpoint/SipController$RegistrationEvent;");
        else if (event == SipRegistrationEvent::NotRegistered)
            j_fidRegistrationEvent = env->GetStaticFieldID(g_registrationEventEnum,
                                                           "NOT_REGISTERED",
                                                           "Lcom/plivo/endpoint/SipController$RegistrationEvent;");
        else if (event == SipRegistrationEvent::NotRegisteredJWT)
            j_fidRegistrationEvent = env->GetStaticFieldID(g_registrationEventEnum,
                                                           "NOT_REGISTERED_JWT",
                                                           "Lcom/plivo/endpoint/SipController$RegistrationEvent;");

        else
            return;

        jobject j_registrationEvent = env->GetStaticObjectField(g_registrationEventEnum,
                                                                j_fidRegistrationEvent);

        jstring j_user = env->NewStringUTF(user.c_str());

        env->CallVoidMethod(g_sipControllerJava, j_midOnRegistration, j_registrationEvent, j_user);

        if (isAttached)
            g_jvm->DetachCurrentThread();
    }

    virtual void handleCall(SipCallEvent event, std::string user) {
        JNIEnv *env;
        bool isAttached = false;

        if (g_jvm->GetEnv((void **) &env, JNI_VERSION_1_6) != JNI_OK) {
            g_jvm->AttachCurrentThread(&env, NULL);
            isAttached = true;
        }

        jclass j_sipControllerClass = env->GetObjectClass(g_sipControllerJava);

        jmethodID j_midOnCall = env->GetMethodID(j_sipControllerClass, "onCall",
                                                 "(Lcom/plivo/endpoint/SipController$CallEvent;Ljava/lang/String;)V");

        jfieldID j_fidCallEvent;
        if (event == SipCallEvent::IncomingCall)
            j_fidCallEvent = env->GetStaticFieldID(g_callEventEnum, "INCOMING_CALL",
                                                   "Lcom/plivo/endpoint/SipController$CallEvent;");
        else if (event == SipCallEvent::TerminateCall)
            j_fidCallEvent = env->GetStaticFieldID(g_callEventEnum, "TERMINATE_CALL",
                                                   "Lcom/plivo/endpoint/SipController$CallEvent;");
        else if (event == SipCallEvent::CallAccepted)
            j_fidCallEvent = env->GetStaticFieldID(g_callEventEnum, "CALL_ACCEPTED",
                                                   "Lcom/plivo/endpoint/SipController$CallEvent;");
        else
            return;

        jobject j_callEvent = env->GetStaticObjectField(g_callEventEnum, j_fidCallEvent);

        jstring j_user = env->NewStringUTF(user.c_str());

        env->CallVoidMethod(g_sipControllerJava, j_midOnCall, j_callEvent, j_user);

        if (isAttached)
            g_jvm->DetachCurrentThread();
    }


    virtual void handleCallInfo(std::string callinfo) {
        JNIEnv *env;
        bool isAttached = false;

        if (g_jvm->GetEnv((void **) &env, JNI_VERSION_1_6) != JNI_OK) {
            g_jvm->AttachCurrentThread(&env, NULL);
            isAttached = true;
        }
        jclass j_sipControllerClass = env->GetObjectClass(g_sipControllerJava);

        jmethodID j_midOnCall = env->GetMethodID(j_sipControllerClass, "setCallInfo",
                                                 "(Ljava/lang/String;)V");

        jstring j_sdp = env->NewStringUTF(callinfo.c_str());
        env->CallVoidMethod(g_sipControllerJava, j_midOnCall, j_sdp);

        if (isAttached)
            g_jvm->DetachCurrentThread();
    }

    virtual void handleInfoLog(std::string log) {
        JNIEnv *env;
        bool isAttached = false;

        if (g_jvm->GetEnv((void **) &env, JNI_VERSION_1_6) != JNI_OK) {
            g_jvm->AttachCurrentThread(&env, NULL);
            isAttached = true;
        }
        jclass j_sipControllerClass = env->GetObjectClass(g_sipControllerJava);

        jmethodID j_midOnCall = env->GetMethodID(j_sipControllerClass, "handleInfoLog",
                                                 "(Ljava/lang/String;)V");

        jstring j_sdp = env->NewStringUTF(log.c_str());
        env->CallVoidMethod(g_sipControllerJava, j_midOnCall, j_sdp);

        if (isAttached)
            g_jvm->DetachCurrentThread();
    }

    virtual void handleCallState(std::string state, int statusCode){
        JNIEnv *env;
        bool isAttached = false;

        if (g_jvm->GetEnv((void **) &env, JNI_VERSION_1_6) != JNI_OK) {
            g_jvm->AttachCurrentThread(&env, NULL);
            isAttached = true;
        }
        jclass j_sipControllerClass = env->GetObjectClass(g_sipControllerJava);

        jmethodID j_midOnCall = env->GetMethodID(j_sipControllerClass, "setCallState",
                                                 "(Ljava/lang/String;I)V");

        jstring j_state = env->NewStringUTF(state.c_str());
        env->CallVoidMethod(g_sipControllerJava, j_midOnCall, j_state, statusCode);
        if (isAttached)
            g_jvm->DetachCurrentThread();
    }
};

JNIEXPORT jstring JNICALL Java_com_plivo_endpoint_SipController_getPublicIp
        (JNIEnv *env, jobject ) {
    std::string publicIp = g_sipControllerCore->getPublicIp();
    return env->NewStringUTF(publicIp.c_str());
}

JNIEXPORT void JNICALL Java_com_plivo_endpoint_SipController_init
        (JNIEnv *env, jobject obj, jobject j_settings,jboolean is_debug_mode)
{
    g_sipControllerJava = env->NewGlobalRef(obj);

    //webrtc::VoiceEngine::SetAndroidObjects(g_jvm, context);
    //webrtc::SetRenderAndroidVM(g_jvm);
    //AndroidVideoCapturerJni::SetAndroidObjects(env, context);

    SipServerSettings serverSettings;

//    g_sipControllerCore->setDebugMode(is_debug_mode);

    jclass j_serverSettingsClass = env->GetObjectClass(j_settings);

    jfieldID j_fidDomain = env->GetFieldID(j_serverSettingsClass, "domain", "Ljava/lang/String;");
    jfieldID j_fidDnsServer = env->GetFieldID(j_serverSettingsClass, "dnsServer", "Ljava/lang/String;");
    jfieldID j_fidProxyServer = env->GetFieldID(j_serverSettingsClass, "proxyServer", "Ljava/lang/String;");
    jfieldID j_fidFallbackProxyServer = env->GetFieldID(j_serverSettingsClass, "fallbackProxyServer", "Ljava/lang/String;");
    jfieldID j_fidUserAgent = env->GetFieldID(j_serverSettingsClass, "userAgent", "Ljava/lang/String;");

    jstring j_domain = static_cast<jstring>(env->GetObjectField(j_settings, j_fidDomain));
    jstring j_dnsServer = static_cast<jstring>(env->GetObjectField(j_settings, j_fidDnsServer));
    jstring j_proxyServer = static_cast<jstring>(env->GetObjectField(j_settings, j_fidProxyServer));
    jstring j_fallbackProxyServer = static_cast<jstring>(env->GetObjectField(j_settings, j_fidFallbackProxyServer));
    jstring j_userAgent = static_cast<jstring>(env->GetObjectField(j_settings, j_fidUserAgent));

    const char *domain = env->GetStringUTFChars(j_domain, NULL);
    const char *dnsServer = env->GetStringUTFChars(j_dnsServer, NULL);
    const char *proxyServer = env->GetStringUTFChars(j_proxyServer, NULL);
    const char *fallbackProxyServer = env->GetStringUTFChars(j_fallbackProxyServer, NULL);
    const char *userAgent = env->GetStringUTFChars(j_userAgent, NULL);

    serverSettings.domain = domain;
    serverSettings.dnsServer = dnsServer;
    serverSettings.proxyServer = proxyServer;
    serverSettings.fallbackProxyServer = fallbackProxyServer;
    serverSettings.userAgent = userAgent;

    env->ReleaseStringUTFChars(j_domain, domain);
    env->ReleaseStringUTFChars(j_dnsServer, dnsServer);
    env->ReleaseStringUTFChars(j_proxyServer, proxyServer);
    env->ReleaseStringUTFChars(j_fallbackProxyServer, fallbackProxyServer);
    env->ReleaseStringUTFChars(j_userAgent, userAgent);

    //g_webRtcEngine = new WebRtcEngine();

    g_sipControllerCore = new SipControllerCore(serverSettings);
    g_sipControllerHandler = new SipControllerHandler();
    //g_sdpHandler =  new SipSDPHandler();
    g_sipControllerCore->registerRegistrationHandler(g_sipControllerHandler);
    g_sipControllerCore->registerCallHandler(g_sipControllerHandler);
    g_sipControllerCore->sipSDPHandler(g_sipControllerHandler);
    g_sipControllerCore->sipCallInfoHandler(g_sipControllerHandler);
    g_sipControllerCore->sipLogHandler(g_sipControllerHandler);
    g_sipControllerCore->sipCallStateHandler(g_sipControllerHandler);
    g_sipControllerCore->setDebugMode(is_debug_mode);

}



JNIEXPORT void JNICALL Java_com_plivo_endpoint_SipController_deinit
        (__attribute__((unused)) JNIEnv *env, jobject )
{

    g_sipControllerCore = nullptr;
    g_sipControllerHandler = nullptr;
}



//Android ---> java class(ainActivity.java) ----> jni interface (rtcsip_jin.cpp) ----> C++ (Sipcontroller.cpp) ---------> resiprocate library

//

JNIEXPORT void JNICALL Java_com_plivo_endpoint_SipController_iceGatheringFinish
        (__attribute__((unused)) JNIEnv *env, jobject) {
    g_sipControllerCore->onIceGatheringFinished();
}

JNIEXPORT void JNICALL Java_com_plivo_endpoint_SipController_networkChange
        (__attribute__((unused)) JNIEnv *env, jobject) {
    map<string, string> headers;
    g_sipControllerCore->networkChange(headers);
    headers.clear();
}

JNIEXPORT void JNICALL Java_com_plivo_endpoint_SipController_networkChangeHeader
        (JNIEnv *env, jobject, jobjectArray j_headerKeys, jobjectArray j_headerValues) {

    map<string, string> headers;
    g_sipControllerCore->arrayToMap(env, j_headerKeys, j_headerValues, headers);
    g_sipControllerCore->networkChange(headers);
    headers.clear();
}

JNIEXPORT void JNICALL Java_com_plivo_endpoint_SipController_iceCandidate
        (JNIEnv *env, jobject, jstring j_mid, jstring j_sdp) {
    std::string mid = env->GetStringUTFChars(j_mid, 0);
    std::string sdp = env->GetStringUTFChars(j_sdp, 0);
    g_sipControllerCore->onIceCandidate(sdp, mid);

}

JNIEXPORT void JNICALL Java_com_plivo_endpoint_SipController_registerUser
        (JNIEnv *env, jobject, jstring j_username, jstring j_password)
{
    const char *username = env->GetStringUTFChars(j_username, NULL);
    const char *password = env->GetStringUTFChars(j_password, NULL);
    g_sipControllerCore->registerUser(username, password);

    env->ReleaseStringUTFChars(j_username, username);
    env->ReleaseStringUTFChars(j_password, password);
}

JNIEXPORT void JNICALL Java_com_plivo_endpoint_SipController_registerUserToken
        (JNIEnv *env, jobject, jstring j_username, jstring j_password, jstring j_token,jstring j_certId)
{
    const char *username = env->GetStringUTFChars(j_username, NULL);
    const char *password = env->GetStringUTFChars(j_password, NULL);
    const char *token = env->GetStringUTFChars(j_token, NULL);
    const char *certId = env->GetStringUTFChars(j_certId, NULL);

    g_sipControllerCore->registerUser(username, password, token,certId);

    env->ReleaseStringUTFChars(j_username, username);
    env->ReleaseStringUTFChars(j_password, password);
    env->ReleaseStringUTFChars(j_token, token);
    env->ReleaseStringUTFChars(j_certId, certId);
}

JNIEXPORT void JNICALL Java_com_plivo_endpoint_SipController_registerUserTokenHeaders
        (JNIEnv *env, jobject, jstring j_username, jstring j_password, jstring j_token,jstring j_certId, jstring j_proxy,
         jobjectArray j_headerKeys, jobjectArray j_headerValues, jboolean is_login_with_jwt)
{

    const char *username = env->GetStringUTFChars(j_username, NULL);
    const char *password = env->GetStringUTFChars(j_password, NULL);
    const char *token = env->GetStringUTFChars(j_token, NULL);
    const char *proxy = env->GetStringUTFChars(j_proxy, NULL);
    const char *certId = env->GetStringUTFChars(j_certId, NULL);

    map<string, string> headers;
    g_sipControllerCore->arrayToMap(env, j_headerKeys, j_headerValues, headers);

    g_sipControllerCore->registerUser(username, password, token, certId, proxy, headers, is_login_with_jwt);

    env->ReleaseStringUTFChars(j_username, username);
    env->ReleaseStringUTFChars(j_password, password);
    env->ReleaseStringUTFChars(j_token, token);
    env->ReleaseStringUTFChars(j_proxy, proxy);
    env->ReleaseStringUTFChars(j_certId, certId);
}

JNIEXPORT void JNICALL Java_com_plivo_endpoint_SipController_unregisterUser
        (JNIEnv *, jobject) {
    g_sipControllerCore->unregisterUser();
}

JNIEXPORT void JNICALL Java_com_plivo_endpoint_SipController_unregisterUserHeader
        (JNIEnv *env, jobject, jobjectArray j_headerKeys, jobjectArray j_headerValues) {
    std::map<std::string, std::string> headerMap;
    g_sipControllerCore->arrayToMap(env, j_headerKeys, j_headerValues, headerMap);
    g_sipControllerCore->unregisterUser(headerMap);
}

JNIEXPORT void JNICALL Java_com_plivo_endpoint_SipController_registerTimeOut
        (__attribute__((unused)) JNIEnv *env, jobject, jint time) {
    g_sipControllerCore->registerTimeOut(time);
}

JNIEXPORT void JNICALL Java_com_plivo_endpoint_SipController_makeCall
        (JNIEnv *env, jobject, jstring j_sipUri, jstring j_localSdp, jobjectArray j_headerKeys, jobjectArray j_headerValues)
{
    std::map<std::string, std::string> headerMap;
    g_sipControllerCore->arrayToMap(env, j_headerKeys, j_headerValues, headerMap);

    const char *sipUri = env->GetStringUTFChars(j_sipUri, NULL);
    const char *localSDP = env->GetStringUTFChars(j_localSdp, NULL);
    if (!headerMap.empty()) {
        g_sipControllerCore->createSession(sipUri, localSDP, headerMap);
    } else {
        g_sipControllerCore->createSession(sipUri, localSDP);
    }

    env->ReleaseStringUTFChars(j_sipUri, sipUri);
}

JNIEXPORT void JNICALL Java_com_plivo_endpoint_SipController_answer
        (JNIEnv *env, jobject, jstring j_localSdp) {
    //g_webRtcEngine->setVideoCapturer(g_capturer, g_captureConstraints);
    //g_webRtcEngine->setLocalRenderer(g_localRenderer);
    //g_webRtcEngine->setRemoteRenderer(g_remoteRenderer);
    const char *localSDP = env->GetStringUTFChars(j_localSdp, NULL);

    g_sipControllerCore->acceptSession(localSDP);
    env->ReleaseStringUTFChars(j_localSdp, localSDP);
}

JNIEXPORT void JNICALL Java_com_plivo_endpoint_SipController_rejectCall
        (__attribute__((unused)) JNIEnv *env, jobject) {
    g_sipControllerCore->reject();
}

JNIEXPORT void JNICALL Java_com_plivo_endpoint_SipController_sendRinging
        (__attribute__((unused)) JNIEnv *env, jobject) {
    g_sipControllerCore->ringing();
}

JNIEXPORT void JNICALL Java_com_plivo_endpoint_SipController_endCall
        (__attribute__((unused)) JNIEnv *env, jobject) {
    g_sipControllerCore->terminateSession();
}



JNIEXPORT jint JNICALL JNI_OnLoad(JavaVM *jvm, __attribute__((unused)) void *reserved) {
    __android_log_print(ANDROID_LOG_VERBOSE, "rtcsip_jni", "JNI_OnLoad");

    g_jvm = jvm;

    JNIEnv *env;
    if (jvm->GetEnv(reinterpret_cast<void **>(&env), JNI_VERSION_1_6) != JNI_OK)
        return -1;

    jclass j_registrationEventEnum = env->FindClass(
            "com/plivo/endpoint/SipController$RegistrationEvent");
    g_registrationEventEnum = reinterpret_cast<jclass>(env->NewGlobalRef(j_registrationEventEnum));

    jclass j_callEventEnum = env->FindClass("com/plivo/endpoint/SipController$CallEvent");
    g_callEventEnum = reinterpret_cast<jclass>(env->NewGlobalRef(j_callEventEnum));
    return JNI_VERSION_1_4;
//    jint ret = InitGlobalJniVariables(jvm);
//    if (ret < 0)
//      return -1;
//
//    //LoadGlobalClassReferenceHolder();
//
//    return ret;
}
}