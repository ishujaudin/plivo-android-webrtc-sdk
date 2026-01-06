//
// Created by Rahul Singh Dhek on 21/04/21.
//

#ifndef TESTRESIP_SIGNALLING_H
#define TESTRESIP_SIGNALLING_H

#include "resip/stack/SipStack.hxx"
#include "resip/dum/DialogUsageManager.hxx"
#include "resip/dum/MasterProfile.hxx"
#include "resip/dum/ClientAuthManager.hxx"
#include "resip/dum/KeepAliveManager.hxx"
#include "resip/dum/ClientRegistration.hxx"
#include "resip/dum/RegistrationHandler.hxx"
#include "rutil/AndroidLogger.hxx"

#include <mutex>
#include <thread>
#include <condition_variable>

#define RESIPROCATE_SUBSYSTEM resip::Subsystem::APP




#include "resip/stack/ssl/Security.hxx"
#include "CertManager.h"

//#include "resip/dum/OutOfDialogHandler.hxx"
//#include "resip/dum/ClientInviteSession.hxx"
#include "resip/dum/InviteSessionHandler.hxx"
//#include "resip/dum/ServerInviteSession.hxx"
#include "resip/stack/EventStackThread.hxx"
//#include "resip/stack/SdpContents.hxx"
//#include "resip/stack/Helper.hxx"
//#include "resip/stack/SipMessage.hxx"
//#include "resip/recon/sdp/SdpCodec.hxx"
//#include "resip/recon/sdp/Sdp.hxx"
//#include "resip/recon/sdp/SdpMediaLine.hxx"
//#include "resip/recon/sdp/SdpHelperResip.hxx"
//#include "rutil/Log.hxx"
//#include "rutil/Logger.hxx"
//#include "resip/stack/PlainContents.hxx"
//#include "resip/stack/GenericContents.hxx"
//#ifdef ANDROID
//#include "rutil/AndroidLogger.hxx"
//#endif

#ifdef ANDROID
resip::AndroidLogger m_androidLog;
#endif

namespace rtcsip {
    enum SipRegistrationEvent {
        Registered,
        NotRegistered
    };

    enum SipCallEvent {
        IncomingCall,
        TerminateCall,
        CallAccepted
    };
    struct SipServerSettings
    {
        std::string domain;
        std::string dnsServer;
        std::string proxyServer;
    };
}

class SipRegistrationHandler
{
public:
    virtual void handleRegistration(rtcsip::SipRegistrationEvent sipEvent, std::string user) = 0;
};

class PlivoAppCallback : public resip::ClientRegistrationHandler, public resip::InviteSessionHandler{
public:

    virtual ~PlivoAppCallback() {}
    PlivoAppCallback(rtcsip::SipServerSettings serverSettings);
    virtual void onStarted(const char *msg) {}
    virtual void onStopped(int restart) {}
    virtual void onLogin(){}
    virtual void onLogout(){}
    virtual void onLoginFailed(){}
    virtual void onDebugMessage(const char *msg){}
    virtual void onDebugMessage(int element){}
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
    virtual void onAnswer(InviteSessionHandle, const SipMessage& msg, const SdpContents&);
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


private:
    void createMasterProfile();
    void sendSdpAnswer();
    void sendSdpOffer();

private:
    std::string m_domain;
    bool m_run;
    bool m_receiveClosed;
    std::string m_localSdp;
    std::string m_remoteSdp;
    //std::vector<IceCandidate> m_localCandidates;
    bool m_inCall;
    bool m_isCaller;
    bool m_localSdpSet;
    bool m_remoteSdpSet;
    bool m_localCandidatesCollected;
    std::thread *m_receiver;
    std::mutex m_receiveMutex;
    std::condition_variable m_receiveCondition;
    std::mutex m_controllerMutex;
    std::mutex m_logMutex;
    std::mutex m_sdpMutex;
    SipRegistrationHandler *m_registrationHandler;
    //SipCallHandler *m_callHandler;
    //SipLogHandler *m_logHandler;
    //SipSDPHandler *m_sdpHandler;
    char m_logBuffer[65536];
#ifdef ANDROID
    resip::AndroidLogger m_androidLog;
#endif

    std::shared_ptr<resip::SipStack> m_stack;
    resip::DnsStub::NameserverList m_dnsServers;
    resip::EventStackThread* m_stackThread;
    resip::DialogUsageManager* m_userAgent;
    //FdPollGrp* m_pollGrp;
    resip::EventThreadInterruptor* m_interruptor;
    resip::NameAddr m_clientAddress;
    resip::Uri m_outboundProxy;
    resip::SharedPtr<resip::MasterProfile> m_masterProfile;
    std::auto_ptr<resip::ClientAuthManager> m_clientAuth;
    std::shared_ptr<resip::ClientInviteSession> m_clientInviteSession;
    std::shared_ptr<resip::ServerInviteSession> m_serverInviteSession;
    resip::SharedPtr<resip::WsConnectionValidator> wsConnectionValidator;
    resip::SharedPtr<resip::WsCookieContextFactory> wsCookieFactory;

    std::string m_username;
    std::string m_password;
    std::string m_uri;
    std::string m_remoteUri;
    std::string m_dnsServer;
    std::string m_proxyServer;

};

extern "C" {
int plivoStart();
void plivoDestroy();
int plivoRestart();


/* Login */
int Login(char *username, char *password);
int Logout();

void setCallbackObject(PlivoAppCallback* callback);

}



#endif //TESTRESIP_SIGNALLING_H