//
// Created by Rahul Singh Dhek on 23/02/21.
//

#ifndef HELLO_LIBS_SIPCONTROLLERCORE_H
#define HELLO_LIBS_SIPCONTROLLERCORE_H

#endif //HELLO_LIBS_SIPCONTROLLERCORE_H

#ifndef SIPCONTROLLERCORE_H__
#define SIPCONTROLLERCORE_H__

#include "JNIController.h"
#include "resip/stack/SipStack.hxx"
#include "resip/dum/DialogUsageManager.hxx"
#include "resip/dum/MasterProfile.hxx"
#include "resip/dum/ClientAuthManager.hxx"
#include "resip/dum/KeepAliveManager.hxx"
#include "resip/dum/ClientRegistration.hxx"
#include "resip/dum/RegistrationHandler.hxx"
#include "resip/dum/OutOfDialogHandler.hxx"
#include "resip/dum/ClientInviteSession.hxx"
#include "resip/dum/InviteSessionHandler.hxx"
#include "resip/dum/ServerInviteSession.hxx"
#include "resip/stack/EventStackThread.hxx"
#include "resip/stack/SdpContents.hxx"
#include "resip/stack/Helper.hxx"
#include "resip/stack/SipMessage.hxx"
#include "resip/dum/DumShutdownHandler.hxx"
#include "resip/recon/sdp/SdpCodec.hxx"
#include "resip/recon/sdp/Sdp.hxx"
#include "resip/recon/sdp/SdpMediaLine.hxx"
#include "resip/recon/sdp/SdpHelperResip.hxx"
#include "rutil/Log.hxx"
#include "rutil/Logger.hxx"
#include "resip/stack/PlainContents.hxx"
#include "resip/stack/GenericContents.hxx"
#include <map>
#ifdef ANDROID
#include "rutil/AndroidLogger.hxx"
#endif

#include <mutex>
#include <thread>
#include <string>
#include <map>
#include <sstream>
#include <condition_variable>

#define RESIPROCATE_SUBSYSTEM resip::Subsystem::APP

#define REG_REQUEST_RETRY 3


#include "resip/stack/ssl/Security.hxx"
#include "resip/stack/IPSingleton.hxx"
#include "CertManager.h"
//#include "SdpCodec.hxx"
//#include "Sdp.hxx"
//#include "SdpMediaLine.hxx"
//#include "SdpHelperResip.hxx"

#include <mutex>
#include <thread>

using namespace std;
using namespace resip;

namespace patch
{
    template < typename T > std::string to_string( const T& n )
    {
        std::ostringstream stm ;
        stm << n ;
        return stm.str() ;
    }
}


namespace rtcsip
{
    enum SipRegistrationEvent
    {
        Registered,
        NotRegistered,
        NotRegisteredJWT
    };

    enum SipCallEvent
    {
        IncomingCall,
        TerminateCall,
        CallAccepted
    };

    struct SipServerSettings
    {
        std::string domain;
        std::string dnsServer;
        std::string proxyServer;
        std::string fallbackProxyServer;
        std::string userAgent;
    };

    class SipRegistrationHandler
    {
    public:
        virtual void handleRegistration(SipRegistrationEvent sipEvent, std::string user) = 0;
    };

    class SipCallHandler
    {
    public:
        virtual void handleCall(SipCallEvent sipEvent, std::string user) = 0;
    };

    class SipSDPHandler
    {
    public:
        virtual void handleSDP(std::string sdp) = 0;
    };

    class SipCallInfoHandler
    {
    public:
        virtual void handleCallInfo(std::string callInfo) = 0;
    };

    class SipLogHandler
    {
    public:
        virtual void handleInfoLog(std::string callInfo) = 0;
    };

    class SipCallStateHandler
    {
    public:
        virtual void handleCallState(std::string state, int statusCode) = 0;
    };

    class SipControllerCore :
            public ClientRegistrationHandler,
            public InviteSessionHandler,
            public ExternalLogger,
            public DumShutdownHandler {

    public:
        struct IceCandidate
        {
            std::string mid;
            std::string candidate;
        };

        SipControllerCore(SipServerSettings serverSettings);
        ~SipControllerCore();

        void receive();
        void registerRegistrationHandler(SipRegistrationHandler* handler);
        void registerCallHandler(SipCallHandler* handler);
        void sipSDPHandler(SipSDPHandler* handler);
        void sipCallStateHandler(SipCallStateHandler* handler);
        void sipCallInfoHandler(SipCallInfoHandler* handler);
        void sipLogHandler(SipLogHandler* handler);
        std::string createUri(const std::string &username);

        void registerUser(const std::string& username, std::string password);
        void registerUser(const std::string& username, std::string password, const std::string& token);
        void addContactDetails(const std::string &certid, const std::string &username, std::string &contactAddress, const std::string &token);

        void registerUser(const std::string& username, std::string password, const std::string& token, const std::string& certid);
        void registerUser(const std::string& username, std::string password, std::string token, const std::string& certid, std::string proxy, std::map<std::string,std::string> headers = {},bool is_log_with_jwt = false);
        void registerTimeOut(int time);
        void unregisterUser();
        void unregisterUser(std::map<std::string,std::string> headers);
        void createSession(std::string remoteUser, std::string localSdp, std::map<std::string,std::string> headers = {});
        void acceptSession(std::string localSdp);
        void reject();
        void rejectOtherCall();
        void networkChange(std::map<std::string,std::string> headers);
        std::string getCallId(const SipMessage& msg);
        bool isValidUserAgent(const SipMessage& msg);
        int getStatusCode(const SipMessage& msg);
        void ringing();
        void removeAllActive();
        void terminateSession();
        void resetStack();
        void arrayToMap(JNIEnv *env, jobjectArray j_headerKeys,jobjectArray j_headerValues,
                        std::map<std::string, std::string>& mapOut);
        std::string getPublicIp();
        virtual void onSuccess(ClientRegistrationHandle, const SipMessage& response);
        virtual void onRemoved(ClientRegistrationHandle, const SipMessage& response);
        virtual int onRequestRetry(ClientRegistrationHandle, int retrySeconds, const SipMessage& response);
        virtual void onFailure(ClientRegistrationHandle, const SipMessage& response);
        virtual void onFlowTerminated(ClientRegistrationHandle);

        virtual void onNewSession(ClientInviteSessionHandle, InviteSession::OfferAnswerType oat, const SipMessage& msg);
        virtual void onNewSession(ServerInviteSessionHandle, InviteSession::OfferAnswerType oat, const SipMessage& msg);
        virtual void onFailure(ClientInviteSessionHandle, const SipMessage& msg);
        virtual void onEarlyMedia(ClientInviteSessionHandle, const SipMessage&, const SdpContents&);
        virtual void onProvisional(ClientInviteSessionHandle, const SipMessage&);
        virtual void onConnected(ClientInviteSessionHandle, const SipMessage& msg);
        virtual void onConnected(InviteSessionHandle, const SipMessage& msg);
        virtual void onTerminated(InviteSessionHandle, InviteSessionHandler::TerminatedReason reason, const SipMessage* related=0);
        virtual void onForkDestroyed(ClientInviteSessionHandle);
        virtual void onRedirected(ClientInviteSessionHandle, const SipMessage& msg);
        virtual void onStaleCallTimeout(ClientInviteSessionHandle);
        virtual void onAnswer(InviteSessionHandle, const SipMessage& msg, const SdpContents&);
        virtual void onReadyToSend(InviteSessionHandle, SipMessage& msg);
        virtual void onOffer(InviteSessionHandle, const SipMessage& msg, const SdpContents&);
        virtual void onOfferRequired(InviteSessionHandle, const SipMessage& msg);
        virtual void onOfferRejected(InviteSessionHandle, const SipMessage* msg);
        virtual void onInfo(InviteSessionHandle, const SipMessage& msg);
        virtual void onInfoSuccess(InviteSessionHandle, const SipMessage& msg);
        virtual void onInfoFailure(InviteSessionHandle, const SipMessage& msg);
        virtual void onMessage(InviteSessionHandle, const SipMessage& msg);
        virtual void onMessageSuccess(InviteSessionHandle, const SipMessage& msg);
        virtual void onMessageFailure(InviteSessionHandle, const SipMessage& msg);
        virtual void onRefer(InviteSessionHandle, ServerSubscriptionHandle, const SipMessage& msg);
        virtual void onReferNoSub(InviteSessionHandle, const SipMessage& msg);
        virtual void onReferRejected(InviteSessionHandle, const SipMessage& msg);
        virtual void onReferAccepted(InviteSessionHandle, ClientSubscriptionHandle, const SipMessage& msg);
        virtual bool operator()(Log::Level level,
                                const Subsystem& subsystem,
                                const Data& appName,
                                const char* file,
                                int line,
                                const Data& message,
                                const Data& messageWithHeaders);

        void onIceCandidate(std::string &sdp, std::string &mid);
        void onIceGatheringFinished();

        void onDumCanBeDeleted() override;

        void setDebugMode(jboolean i);

    private:
        void setupTransport(const std::string& address, const std::string& contact_url);
        void setupCredential(std::string username, std::string password);
        void createMasterProfile();
        void sendSdpAnswer();
        void sendSdpOffer(std::map<std::string,std::string> headers);
        Tuple processIpAndPort(const SipMessage& message);
        std::string processCallInfo(const SipMessage& message);


    private:
        std::string m_domain;
        bool m_run;
        bool m_receiveClosed;
        std::string m_localSdp;
        std::string m_remoteSdp;
        std::vector<IceCandidate> m_localCandidates;
        bool m_inCall;
        bool m_isRunning;
        bool m_isCaller;
        bool m_localSdpSet;
        bool m_remoteSdpSet;
        bool m_localCandidatesCollected;
        bool m_inInBoundAccepted;
        int retry_count = 1;
        std::thread *m_receiver;
        std::mutex m_receiveMutex;
        std::condition_variable m_receiveCondition;
        std::mutex m_controllerMutex;
        std::mutex m_logMutex;
        std::mutex m_sdpMutex;
//        WebRtcEngine *m_webRtcEngine;
        SipRegistrationHandler *m_registrationHandler;
        SipCallHandler *m_callHandler;
        SipSDPHandler *m_sdpHandler;
        SipCallStateHandler *m_callStateHandler;
        SipCallInfoHandler *m_callInfoHandler;
        SipLogHandler *m_logHandler;
        char m_logBuffer[65536];
#ifdef ANDROID
        AndroidLogger m_androidLog;
#endif

        SharedPtr<SipStack> m_stack;
        DnsStub::NameserverList m_dnsServers;
        EventStackThread* m_stackThread = nullptr;
        DialogUsageManager* m_userAgent = nullptr;
        FdPollGrp* m_pollGrp;
        EventThreadInterruptor* m_interruptor;
        NameAddr m_clientAddress;
        Uri m_outboundProxy;
        SharedPtr<MasterProfile> m_masterProfile;
//        SharedPtr<UserProfile> m_userProfile;
        std::auto_ptr<ClientAuthManager> m_clientAuth;
        ClientInviteSession* m_clientInviteSession = nullptr;
        ServerInviteSession* m_serverInviteSession = nullptr;
        ServerInviteSession* m_otherServerInviteSession = nullptr;
//        SharedPtr<WsConnectionValidator> wsConnectionValidator;
//        SharedPtr<WsCookieContextFactory> wsCookieFactory;


        std::map<std::string,std::string> m_headers;
        std::string m_uri;
        std::string m_current_callid;
        std::string m_app_id;
        std::string m_cert_id;
        std::string m_username;
        std::string m_password;
        std::string m_remoteUri;
        std::string m_dnsServer;
        std::string m_proxyServer;
        std::string m_fallbackProxyServer;
        std::string m_userAgentName;
        std::string m_public_ip_port;
        std::string m_public_ip;
        std::string sipLogString = "";
        bool isProxyRegister;
        bool isUACRejected = false;
        bool isDebug = false;
        bool isLoginWithJWT = false;
        bool isLogOutAttempt = false;
        int m_reg_timeout = 0;
        bool isSecondaryUAC = false;
        bool isRegistrationInitiated = false;
        int tls_key;
        int tls_transport_port  = 5061;

        typedef enum
        {
            Dialing,
            Ringing,
            Connected,
            Done
        } DialProgress;

        DialProgress mProgress;

        void reRegister();
        void moveToFallback();

        void refreshTargetsForInvite();

        bool refresh_targets = false;
        bool isCallRinging = false;

        bool handleHeaderErrorCode(const SipMessage &message);

        const char *printBool(bool targets);
        void getTransport();
        void handleConnection(string value);

        bool checkIfSipMessage(string basicString);

        void LOG_D(const char *string, const char *str, const char *stt);
        void LOG_D(const char *string, const char *str);
        void LOG_D(const char *string);
        void LOG_E(const char *string, const char *str);
        void LOG_E(const char *string);
        std::string getDateString();

        bool pingSite(const char *site);

        void handleLog(initializer_list<const char *> logs);

        const char *printInt(int i);

        bool isMovedToFallback = false;

        void clearRegistrationRetry();

        const char *CallProgressToString(SipControllerCore::DialProgress progress);

        bool checkIfFallbackRequired(bool online);

        bool isConnectionLost = false;

        int extractTransportKey(std::string value);
    };

}
#endif






//RESIPROCATE CHEAT

//
//
//
//NameAddr route;
//              route.uri().scheme() = "sip";
//              route.uri().user() = "proxy";
//              route.uri().host() = SipStack::getHostname();
//              route.uri().port() = 5070;
//              route.uri().param(p_transport) = Tuple::toData(mTransport);
//              rRoutes.push_front(route);
//
//              NameAddr& frontRRoute = rRoutes.front();
//              ErrLog ( << "rRoute: " << frontRRoute.uri().toString());




