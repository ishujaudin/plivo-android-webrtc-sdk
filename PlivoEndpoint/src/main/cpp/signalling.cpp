//
// Created by Rahul Singh Dhek on 21/04/21.
//

#include "signalling.h"


using namespace std;

//static PlivoAppCallback* callbackObj = new PlivoAppCallback();

/**
 * Login to plivo cloud.
 */


void createMasterProfile()
{
    m_masterProfile = SharedPtr<MasterProfile>(new MasterProfile);
    m_masterProfile->setInstanceId(m_uri.c_str());

    m_masterProfile->clearSupportedMethods();
    m_masterProfile->addSupportedMethod(INVITE);
    m_masterProfile->addSupportedMethod(ACK);
    m_masterProfile->addSupportedMethod(CANCEL);
    m_masterProfile->addSupportedMethod(OPTIONS);
    m_masterProfile->addSupportedMethod(BYE);
    m_masterProfile->addSupportedMethod(NOTIFY);
    m_masterProfile->addSupportedMethod(SUBSCRIBE);
    m_masterProfile->addSupportedMethod(INFO);
    m_masterProfile->addSupportedMethod(MESSAGE);
    m_masterProfile->addSupportedMethod(PRACK);
    m_masterProfile->setUacReliableProvisionalMode(MasterProfile::Supported);
    m_masterProfile->setUasReliableProvisionalMode(MasterProfile::SupportedEssential);

    // Support Languages
    m_masterProfile->clearSupportedLanguages();
    m_masterProfile->addSupportedLanguage(Token("en"));

    // Support Mime Types
    m_masterProfile->clearSupportedMimeTypes();
    m_masterProfile->addSupportedMimeType(INVITE, Mime("application", "sdp"));
    m_masterProfile->addSupportedMimeType(INVITE, Mime("multipart", "mixed"));
    m_masterProfile->addSupportedMimeType(INVITE, Mime("multipart", "signed"));
    m_masterProfile->addSupportedMimeType(INVITE, Mime("multipart", "alternative"));
    m_masterProfile->addSupportedMimeType(OPTIONS,Mime("application", "sdp"));
    m_masterProfile->addSupportedMimeType(OPTIONS,Mime("multipart", "mixed"));
    m_masterProfile->addSupportedMimeType(OPTIONS, Mime("multipart", "signed"));
    m_masterProfile->addSupportedMimeType(OPTIONS, Mime("multipart", "alternative"));
    m_masterProfile->addSupportedMimeType(PRACK, Mime("application", "sdp"));
    m_masterProfile->addSupportedMimeType(PRACK, Mime("multipart", "mixed"));
    m_masterProfile->addSupportedMimeType(PRACK, Mime("multipart", "signed"));
    m_masterProfile->addSupportedMimeType(PRACK, Mime("multipart", "alternative"));
    m_masterProfile->addSupportedMimeType(UPDATE, Mime("application", "sdp"));
    m_masterProfile->addSupportedMimeType(UPDATE, Mime("multipart", "mixed"));
    m_masterProfile->addSupportedMimeType(UPDATE, Mime("multipart", "signed"));
    m_masterProfile->addSupportedMimeType(UPDATE, Mime("multipart", "alternative"));

    // Supported Options Tags
    m_masterProfile->clearSupportedOptionTags();
    //mMasterProfile->addSupportedOptionTag(Token(Symbols::Replaces));
    m_masterProfile->addSupportedOptionTag(Token(Symbols::Timer));     // Enable Session Timers

    // Supported Schemes
    m_masterProfile->clearSupportedSchemes();
    m_masterProfile->addSupportedScheme("sip");
    m_masterProfile->addSupportedScheme("sips");

    // Validation Settings
    m_masterProfile->validateContentEnabled() = false;
    m_masterProfile->validateContentLanguageEnabled() = false;
    m_masterProfile->validateAcceptEnabled() = false;

    // Have stack add Allow/Supported/Accept headers to INVITE dialog establishment messages
    m_masterProfile->clearAdvertisedCapabilities(); // Remove Profile Defaults, then add our preferences
    m_masterProfile->addAdvertisedCapability(Headers::Allow);
    //_masterProfile->addAdvertisedCapability(Headers::AcceptEncoding);  // This can be misleading - it might specify what is expected in response
    m_masterProfile->addAdvertisedCapability(Headers::AcceptLanguage);
    m_masterProfile->addAdvertisedCapability(Headers::Supported);
    m_masterProfile->setMethodsParamEnabled(true);

    m_masterProfile->setDefaultFrom(m_clientAddress);
    m_masterProfile->setDigestCredential(m_clientAddress.uri().host(), m_clientAddress.uri().user(), m_password.c_str());

    if (!m_outboundProxy.host().empty())
    {
        m_masterProfile->setOutboundProxy(m_outboundProxy);
        m_masterProfile->addSupportedOptionTag(Token(Symbols::Outbound));
    }

    m_masterProfile->setUserAgent("plivo-browser-sdk 2.2.3");
    m_masterProfile->setKeepAliveTimeForDatagram(30);
    m_masterProfile->setKeepAliveTimeForStream(120);
}



int Login(std::string username, std::string password) {

    //InfoLog(<< "Here1");
    std::string m_domain = "test";
    std::string m_uri = username + "@" + m_domain ;
    std::string address = "sip:" + m_uri + ":5061;transport=TLS" ;//+ ":5063";
//    m_username = username;
//    m_password = password;

#ifndef ANDROID
    Log::setLevel(Log::Stack);
#else
    resip::Log::initialize(resip::Log::Cout, resip::Log::Stack, "SIP", m_androidLog);
#endif

    if (m_proxyServer.size() != 0)
    {
        std::string proxyAddress = "sip:" + m_proxyServer + ":5061;transport=TLS";
        m_outboundProxy = Uri(Data(proxyAddress));
    }
    else
    {
        m_outboundProxy = Uri();
    }

    Data dnsServer("8.8.8.8");
    m_dnsServers.push_back(Tuple(dnsServer, 0, UNKNOWN_TRANSPORT).toGenericIPAddress());


    Data caFile(root_cert);

    Security* security;
    security = new Security(caFile);


    security->addCAFile(caFile);


    m_stack.reset(new SipStack(security, m_dnsServers,0,false,0));


    m_userAgent = new DialogUsageManager(*m_stack);

    m_clientAddress = NameAddr(address.c_str());

    createMasterProfile();

    m_stack->addTransport(TLS,5061,V4,StunEnabled,Data::Empty,Data::Empty,Data::Empty,SecurityTypes::SSLv23, 0, "","", SecurityTypes::Optional, false, wsConnectionValidator, wsCookieFactory);


    m_clientAuth = std::auto_ptr<ClientAuthManager>(new ClientAuthManager);
    m_userAgent->setClientAuthManager(std::move(m_clientAuth));

    std::auto_ptr<KeepAliveManager> keepAlive(new KeepAliveManager);
    m_userAgent->setKeepAliveManager(std::move(keepAlive));

    m_userAgent->setClientRegistrationHandler(this);

    m_userAgent->setInviteSessionHandler(this);

    m_userAgent->setMasterProfile(m_masterProfile);

    m_pollGrp = FdPollGrp::create();
    m_interruptor = new EventThreadInterruptor(*m_pollGrp);
    m_stackThread = new EventStackThread(*m_stack, *m_interruptor,*m_pollGrp);

    SharedPtr<SipMessage> regMessage = m_userAgent->makeRegistration(m_clientAddress);


    m_stack->run();
    m_stackThread->run();
    m_userAgent->send(regMessage);
    InfoLog(<< "After Registration");

    {
        std::lock_guard<std::mutex> lock(m_controllerMutex);

        m_run = true;
        m_receiveClosed = false;

        m_receiver = new std::thread(&SipControllerCore::receive, this);
    }
}

/**
 * Logout
 */
int Logout() {
    callbackObj->onDebugMessage("Logout");
}



void plivoDestroy()
{
    callbackObj->onDebugMessage("plivoDestroy");
    //pjsua_app_destroy();

    /** This is on purpose **/
    //pjsua_app_destroy();
}

int plivoRestart()
{
    callbackObj->onDebugMessage("plivoRestart");

    plivoDestroy();

    return 0;// initMain(restart_argc, restart_argv);
}

void setCallbackObject(PlivoAppCallback* callback)
{
    callbackObj = callback;
}


