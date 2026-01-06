
#include "SipControllerCore.h"

#ifdef ANDROID

#include <android/log.h>
#include <rutil/DnsUtil.hxx>
#include <resip/stack/ExtensionHeader.hxx>
//#include <resip/stack/ConnectionTerminated.hxx>

#define TAG "SipControllerCore"

#define  LOGV(...)  __android_log_print(ANDROID_LOG_VERBOSE,    TAG, __VA_ARGS__)
#define  LOGW(...)  __android_log_print(ANDROID_LOG_WARN,       TAG, __VA_ARGS__)
#define  LOGD(...)  __android_log_print(ANDROID_LOG_DEBUG,      TAG, __VA_ARGS__)
#define  LOGI(...)  __android_log_print(ANDROID_LOG_INFO,       TAG, __VA_ARGS__)
#define  LOGE(...)  __android_log_print(ANDROID_LOG_ERROR,      TAG, __VA_ARGS__)
#include<string>
#include <utility>

#endif

namespace rtcsip {


    SipControllerCore::SipControllerCore(SipServerSettings serverSettings) :
            m_run(false),
            m_receiveClosed(false),
            m_inCall(false),
            m_isCaller(false),
            m_localSdpSet(false),
            m_remoteSdpSet(false),
            m_localCandidatesCollected(false),
            m_receiver(NULL),
            m_registrationHandler(NULL),
            m_callHandler(NULL),
            m_sdpHandler(NULL),
            m_callStateHandler(NULL) {
        m_domain = serverSettings.domain;
        m_dnsServer = serverSettings.dnsServer;
        m_proxyServer = serverSettings.proxyServer;
        m_fallbackProxyServer = serverSettings.fallbackProxyServer;
        m_userAgentName = serverSettings.userAgent;
    }

    SipControllerCore::~SipControllerCore() {
        if (m_receiver)
            m_receiver->detach();
        if (m_stack) {
            m_stack->shutdown();
        }
        LOG_D("SipControllerCore: deinit : ");
    }

    void SipControllerCore::receive() {
        bool run = true;
        while (run) {
            if (m_userAgent != NULL) {
                m_userAgent->process();
                m_receiveMutex.lock();
                run = m_run;
                sleepMs(100);
                m_receiveMutex.unlock();
            }
        }
        m_receiveMutex.lock();
        m_receiveClosed = true;
        m_receiveMutex.unlock();
        m_receiveCondition.notify_one();
    }


/*
 *
 MARK: Handler setup
 *
 */
    void SipControllerCore::registerRegistrationHandler(SipRegistrationHandler *handler) {
        std::lock_guard<std::mutex> lock(m_controllerMutex);
        m_registrationHandler = handler;
    }

    void SipControllerCore::registerCallHandler(SipCallHandler *handler) {
        std::lock_guard<std::mutex> lock(m_controllerMutex);
        m_callHandler = handler;
    }


    void SipControllerCore::sipSDPHandler(SipSDPHandler *handler) {
        std::lock_guard<std::mutex> lock(m_controllerMutex);
        m_sdpHandler = handler;
    }

    void SipControllerCore::sipCallInfoHandler(SipCallInfoHandler *handler) {
        std::lock_guard<std::mutex> lock(m_controllerMutex);
        m_callInfoHandler = handler;
    }

    void SipControllerCore::sipLogHandler(SipLogHandler *handler) {
        std::lock_guard<std::mutex> lock(m_controllerMutex);
        m_logHandler = handler;
    }


    void SipControllerCore::sipCallStateHandler(SipCallStateHandler *handler) {
        std::lock_guard<std::mutex> lock(m_controllerMutex);
        m_callStateHandler = handler;
    }


/*
 *
 MARK: Custom methods
 *
 */
    std::string SipControllerCore::processCallInfo(const SipMessage &message) {
        std::string callInfo = "";
        callInfo = callInfo + "type:" + "outgoing";

        if (m_current_callid.size() != 0) {
            callInfo = callInfo + "," + "call_id:" + m_current_callid;
        }

        for (auto &&i :message.getRawUnknownHeaders()) {
            std::string value = message.header(ExtensionHeader(i.first)).front().value().c_str();
            callInfo = callInfo + "," + i.first.c_str() + ":" + value;
        }

        resip::H_From fromHT;
        H_From::Type from = message.header(fromHT);
        const char *fromC = from.uri().user().c_str();

        callInfo = callInfo + "," + "from:" + fromC;

        resip::H_To toHT;
        H_From::Type to = message.header(toHT);
        const char *toC = to.uri().user().c_str();

        callInfo = callInfo + "," + "to:" + toC;
        return callInfo;
    }

    Tuple SipControllerCore::processIpAndPort(const SipMessage &message) {
        if (message.exists(h_Vias) == false) {
            return Tuple();
        }
        Vias::const_iterator it = message.header(h_Vias).end();
        while (true) {
            it--;
            if (it->exists(p_received)) {
                // Check IP from received parameter
                Tuple address(it->param(p_received), 0, UNKNOWN_TRANSPORT);
                if (!address.isPrivateAddress()) {
                    address.setPort(
                            it->exists(p_rport) ? it->param(p_rport).port() : it->sentPort());
                    address.setType(Tuple::toTransport(it->transport()));
                    if (address.getType() != UNKNOWN_TRANSPORT) {
                        m_public_ip = Tuple::inet_ntop(address).c_str();
                    }
                }
            }
            // Check IP from Via sentHost
            if (DnsUtil::isIpV4Address(
                    it->sentHost())  // Ensure the via host is an IP address (note: web-rtc uses hostnames here instead)
                #ifdef USE_IPV6
                || DnsUtil::isIpV6Address(it->sentHost())
#endif
                    ) {
                Tuple address(it->sentHost(), 0, UNKNOWN_TRANSPORT);
                if (address.isPrivateAddress()) {
                    address.setPort(
                            it->exists(p_rport) ? it->param(p_rport).port() : it->sentPort());
                    address.setType(Tuple::toTransport(it->transport()));
                    return address;
                }
            }
            if (it == message.header(h_Vias).begin()) break;
        }
        return Tuple();
    }

    std::string SipControllerCore::getCallId(const SipMessage &msg) {
        std::string result;
        if (msg.exists(h_CallId)) {
            result = msg.header(h_CallId).value().c_str();
        }
        return result;
    }

    bool SipControllerCore::isValidUserAgent(const SipMessage &msg) {
        bool result = false;
        if (msg.exists(h_UserAgent)) {
            std::string user_agent = msg.header(h_UserAgent).value().c_str();
            for (char &ch : user_agent) {
                ch = std::tolower(ch);
            }
            if (user_agent.find("plivo") != std::string::npos) {
                result = true;
            }
        }
        return result;
    }

    int SipControllerCore::getStatusCode(const SipMessage &msg) {
        int result;
        result = msg.header(h_StatusLine).responseCode();
        return result;
    }

    void SipControllerCore::handleConnection(std::string value){
        if ((value.find("Failed to find connection") != std::string::npos) && (
                (!m_outboundProxy.host().empty() &&
                 value.find("targetDomain=" + patch::to_string(m_outboundProxy.host())) !=
                 std::string::npos) ||
                value.find("targetDomain=" + m_domain) != std::string::npos)) {
            refresh_targets = true;
            isConnectionLost = true;
            bool isOnline = pingSite("google.com");
            handleLog({"Connection terminated isOnline: ", printBool(isOnline), " isRegistrationInitiated: " , printBool(isRegistrationInitiated), "CallState",
                       CallProgressToString(mProgress)});
            if (checkIfFallbackRequired(isOnline)) {
                int transportKey = extractTransportKey(value);
                m_stack->removeTransport(transportKey);
                sleep(1);
                getTransport();
                handleLog({"removing transport ", printInt(transportKey)});
                moveToFallback();
            }
        }
    }

    void SipControllerCore::reRegister() {
        LOG_D("SipControllerCore :: reRegister");
        if(isLoginWithJWT){
            registerUser(m_username,m_password,m_app_id,m_cert_id,m_proxyServer,m_headers,true);
        }else if (m_cert_id.empty() == false && m_cert_id != "" && m_cert_id != "" &&
                  m_app_id.empty() == false && m_app_id != "" && m_app_id != "") {
            registerUser(m_username, m_password, m_app_id, m_cert_id);
        } else if (m_app_id.empty() == false && m_app_id != "" && m_app_id != "") {
            registerUser(m_username, m_password, m_app_id);
        } else {
            registerUser(m_username, m_password);
        }
    }

    std::string SipControllerCore::createUri(const std::string &username) {
        m_uri = username + "@" + m_domain;
        std::string sipUri = "<sip:" + m_uri + ";transport=tls>";
        return sipUri;
    }

    void SipControllerCore::addContactDetails(const std::string &certid,const std::string &username, std::string &contactAddress,
                                              const std::string &token) {

        contactAddress = contactAddress + ">";

        if ((certid.size() == 0) && (token.size() == 0)) {
            return;
        }

        if (certid.size() != 0) {
            m_cert_id = certid;

            std::string p = ">";
            std::string::size_type n = p.length();

            for (std::string::size_type i = contactAddress.find(p);
                 i != std::string::npos;
                 i = contactAddress.find(p))
                contactAddress.erase(i, n);

            contactAddress = contactAddress + ";certid=" + certid + ">";
        }

        if (token.size() != 0) {
            m_app_id = token;
            std::string p = ">";
            std::string::size_type n = p.length();

            for (std::string::size_type i = contactAddress.find(p);
                 i != std::string::npos;
                 i = contactAddress.find(p))
                contactAddress.erase(i, n);

            contactAddress = contactAddress + ";fcm=" + token + ">";
        }
    }

/// Outgoing call
    void SipControllerCore::
    sendSdpOffer(std::map<std::string, std::string> headers = {}) {
        LOG_D("SDP Offer local sdp : %s", m_localSdp.c_str());
        LOG_D("***sendSdpOffer");

        if(refresh_targets == false)
            isCallRinging = true;

        Data txt(m_localSdp);
        HeaderFieldValue hfv(txt.data(), txt.size());
        Mime type("application", "sdp");
        SdpContents offerSdp(hfv, type);
        SdpContents::Session::Medium *audioMedium = NULL;
        SdpContents::Session::Medium *videoMedium = NULL;

        SdpContents::Session::MediumContainer &mediumContainer = offerSdp.session().media();

        SdpContents::Session::MediumContainer::iterator iter = mediumContainer.begin();
        while (iter != mediumContainer.end()) {
            if (iter->name() == "audio")
                audioMedium = &(*iter);
            else if (iter->name() == "video")
                videoMedium = &(*iter);
            iter++;
        }

        std::vector<IceCandidate> localCandidates;
        {
            std::lock_guard<std::mutex> lock(m_controllerMutex);
            localCandidates = m_localCandidates;
        }

        std::vector<IceCandidate>::iterator candidateIterator = localCandidates.begin();
        while (candidateIterator != localCandidates.end()) {
            std::string localCandidate = candidateIterator->candidate;
            std::string midString(candidateIterator->mid);
            std::string candidateString(candidateIterator->candidate);
            Data candidateData(candidateString.c_str());
            if (audioMedium != NULL && midString.compare("0") == 0)
                audioMedium->addAttribute(Data("candidate"), candidateData.substr(10));
            else if (videoMedium != NULL && midString.compare("video") == 0)
                videoMedium->addAttribute(Data("candidate"), candidateData.substr(10));
            candidateIterator++;
        }

        std::string address = "sip:" + m_remoteUri + "@" + m_domain + ";transport=tls";
        SharedPtr<SipMessage> msg = m_userAgent->makeInviteSession(NameAddr(address.c_str()),
                                                                   m_masterProfile, &offerSdp, 0);
        for (auto it = headers.begin(); it != headers.end(); it++) {
            const Data headerName(it->first.c_str());
            resip::ExtensionHeader h_Tmp(headerName);
            LOG_D("SDP Offer header values: %s ",it->second.c_str());
            msg->header(h_Tmp).push_back(StringCategory(it->second.c_str()));
        }

        m_userAgent->send(msg);

        m_localCandidates.clear();

        SipCallStateHandler *callStateHandler;
        {
            callStateHandler = m_callStateHandler;
        }

        callStateHandler->handleCallState("Calling", 000);
    }

/// Incoming call
    void SipControllerCore::sendSdpAnswer() {
        LOG_D("SDP Answer Sent | Incoming call flow");

        LOG_D("***sendSdpAnswer");

        isCallRinging = false;
        Data txt(m_localSdp);

        HeaderFieldValue hfv(txt.data(), txt.size());
        Mime type("application", "sdp");
        SdpContents answerSdp(hfv, type);

        SdpContents::Session::Medium *audioMedium = NULL;
        SdpContents::Session::Medium *videoMedium = NULL;

        SdpContents::Session::MediumContainer &mediumContainer = answerSdp.session().media();

        SdpContents::Session::MediumContainer::iterator iter = mediumContainer.begin();
        while (iter != mediumContainer.end()) {
            if (iter->name() == "audio")
                audioMedium = &(*iter);
            else if (iter->name() == "video")
                videoMedium = &(*iter);
            iter++;
        }

        std::vector<IceCandidate> localCandidates;
        {
            std::lock_guard<std::mutex> lock(m_controllerMutex);
            localCandidates = m_localCandidates;
        }

        std::vector<IceCandidate>::iterator candidateIterator = localCandidates.begin();
        while (candidateIterator != localCandidates.end()) {
            std::string midString(candidateIterator->mid);
            std::string candidateString(candidateIterator->candidate);
            Data candidateData(candidateString.c_str());
            if (audioMedium != NULL && midString.compare("0") == 0)
                audioMedium->addAttribute(Data("candidate"), candidateData.substr(10));
            else if (videoMedium != NULL && midString.compare("video") == 0)
                videoMedium->addAttribute(Data("candidate"), candidateData.substr(10));
            candidateIterator++;
        }

        // Call state handler
        SipCallStateHandler *callStateHandler;
        {
            callStateHandler = m_callStateHandler;
        }

        callStateHandler->handleCallState("CallAccepted", 200);

        m_localCandidates.clear();

        m_serverInviteSession->provideAnswer(answerSdp);
        m_serverInviteSession->accept();
    }

    void SipControllerCore::registerUser(const std::string& username, std::string password) {

        isRegistrationInitiated = true;
        clearRegistrationRetry();
        isProxyRegister = false;
        std::string sipUri = createUri(username);

        LOG_D("registerUser with (username/password): uri : %s",sipUri.c_str());
#ifndef ANDROID
        Log::setLevel(Log::Stack);
#else
//        resip::Log::initialize(resip::Log::Cout, resip::Log::Stack, "SIP", m_androidLog);
#endif
        m_username = username;
        m_password = password;
        setupTransport(sipUri, sipUri);
    }

    void SipControllerCore::registerUser(const std::string& username, std::string password, const std::string& token) {
        isRegistrationInitiated = true;
        clearRegistrationRetry();
        isProxyRegister = false;

        std::string sipUri = createUri(username);

        std::string contactAddress = sipUri;

        addContactDetails("", username,contactAddress, token);

        LOG_D("registerUser: uri with (username/password/token): %s", sipUri.c_str());
#ifndef ANDROID
        Log::setLevel(Log::Stack);
#else
//        resip::Log::initialize(resip::Log::Cout, resip::Log::Stack, "SIP", m_androidLog);
#endif
        m_username = username;
        m_password = password;

        setupTransport(sipUri, contactAddress);
    }

    void SipControllerCore::registerUser(const std::string& username, std::string password,
                                         const std::string &token, const std::string& certid) {
        isRegistrationInitiated = true;
        clearRegistrationRetry();
        isProxyRegister = false;

        std::string sipUri = createUri(username);

        std::string contactAddress = sipUri;

        addContactDetails(certid, username,contactAddress, token);
        LOG_D("registerUser: uri with (username/password/token/certid): %s", sipUri.c_str());

        m_username = username;
        m_password = password;

        setupTransport(sipUri, contactAddress);
    }

    void SipControllerCore::registerUser(const std::string& username, std::string password, std::string token,
                                         const std::string& certid, std::string proxy,
                                         std::map<std::string, std::string> headers, bool is_log_with_jwt) {

        isRegistrationInitiated = true;
        clearRegistrationRetry();
        isLoginWithJWT = is_log_with_jwt;
        isUACRejected = false;
        isProxyRegister = false;

        if (!proxy.empty()) {
            m_proxyServer = proxy;
        }

        std::string sipUri = createUri(username);

        std::string contactAddress = sipUri;

        addContactDetails(certid, username,contactAddress, token);

        LOG_D(
                "registerUser: uri with (username/password/token/certid/proxy/headers): %s", sipUri.c_str());
#ifndef ANDROID
        Log::setLevel(Log::Stack);
#else
//        resip::Log::initialize(resip::Log::Cout, resip::Log::Stack, "SIP", m_androidLog);
#endif


        m_headers = std::move(headers);

        m_username = username;
        m_password = password;

        setupTransport(sipUri, contactAddress);
    }

    bool SipControllerCore::operator()(Log::Level level,
                                       const Subsystem& subsystem,
                                       const Data& appName,
                                       const char* file,
                                       int line,
                                       const Data& message,
                                       const Data& messageWithHeaders) {
        std::string raw = message.c_str();
        if(isRegistrationInitiated && checkIfSipMessage(raw)){
            std::string dateString = getDateString();
            sipLogString = sipLogString +"["+ dateString +"]" +raw + "##";
        }
        LOG_D("\n %s", raw.c_str());
        handleConnection(raw);
        return false;
    }


    void SipControllerCore::setupTransport(const std::string& address, const std::string& contact_url) {

        LOG_D("setupTransport: url: address: %s contact_url: %s", address.c_str(), contact_url.c_str());
        Data appname("plivo_resip.log");
        Data loglevel("STACK");

        resip::Log::initialize("plivo_resip_log", loglevel, "", appname.c_str(), (ExternalLogger*) this);

        //Proxy Setup
        if (!m_proxyServer.empty()) {
            std::string proxyAddress = "sip:" + m_proxyServer + ";transport=tls";
            m_outboundProxy = Uri(Data(proxyAddress));
        } else {
            m_outboundProxy = Uri();
        }

        if (m_stack == nullptr) {
            //DNS Setup
            LOG_D("inside m_stack");
            Data dnsServer("8.8.8.8");
            m_dnsServers.push_back(Tuple(dnsServer, 0, UNKNOWN_TRANSPORT).toGenericIPAddress());

            //Certificate and Security setup
            Data caFile(root_cert);

            Security *security;
            security = new Security(caFile);

            //Note: With opensigcom do not remove these line
            //    Compression *compression = new Compression(Compression::DEFLATE);

            security->addCAFile(caFile);
            //    m_stack.reset(new SipStack(security, m_dnsServers,0,false,0,compression));

            m_stack.reset(new SipStack(security, m_dnsServers, 0, false, 0));
            getTransport();

            m_stack->statisticsManagerEnabled() = false;

            m_userAgent = new DialogUsageManager(*m_stack);

            m_userAgent->setClientRegistrationHandler(this);
            m_userAgent->setInviteSessionHandler(this);

            createMasterProfile();
            m_userAgent->setMasterProfile(m_masterProfile);

            std::auto_ptr<KeepAliveManager> keepAlive(new KeepAliveManager);
            m_userAgent->setKeepAliveManager(keepAlive);

            m_pollGrp = FdPollGrp::create();
            m_interruptor = new EventThreadInterruptor(*m_pollGrp);
            m_stackThread = new EventStackThread(*m_stack, *m_interruptor, *m_pollGrp);

            m_stack->run();
            m_stackThread->run();
        }
        m_clientAuth = std::auto_ptr<ClientAuthManager>(new ClientAuthManager);
        m_userAgent->setClientAuthManager(m_clientAuth);


        m_clientAddress = NameAddr(address.c_str());
        if(!isLoginWithJWT){
            setupCredential(m_username, m_password);
        }
        m_masterProfile->setDefaultFrom(m_clientAddress);
        SharedPtr<SipMessage> regMessage = m_userAgent->makeRegistration(m_clientAddress);

        if (m_headers.size() != 0) {
            map<string, string>::iterator it;
            for (it = m_headers.begin(); it != m_headers.end(); it++) {
                const Data headerName(it->first);
                resip::ExtensionHeader h_Tmp(headerName);

                regMessage->header(h_Tmp).push_back(StringCategory(it->second.c_str()));
            }
        }
        if (contact_url.size() != 0) {
            Data contactString(contact_url);
            NameAddr contact(contactString);

            regMessage->header(h_Contacts).pop_back();
            regMessage->header(h_Contacts).push_back(contact);
        }

        regMessage->header(h_Expires).value() = m_reg_timeout;
//        if (isProxyRegister == true) {
//            if (!m_outboundProxy.host().empty()) {
//                regMessage->header(h_Routes).push_back(NameAddr(m_outboundProxy));
//            }
//        }
        LOG_D("m_useagent before");
        m_userAgent->send(regMessage);
        LOG_D("m_useagent after");

        if (isProxyRegister == false && m_run == false) {
            LOG_D("****new thread created");
            std::lock_guard<std::mutex> lock(m_controllerMutex);
            m_run = true;
            m_receiveClosed = false;
            m_receiver = new std::thread(&SipControllerCore::receive, this);
        }
    }

    void SipControllerCore::setupCredential(std::string username, std::string password) {
        m_masterProfile->setDigestCredential(m_clientAddress.uri().host(),
                                             m_clientAddress.uri().user(), password.c_str());
    }

    void SipControllerCore::createMasterProfile() {
        m_masterProfile = SharedPtr<MasterProfile>(new MasterProfile);
        m_masterProfile->setInstanceId(m_uri.c_str());

        m_masterProfile->clearSupportedMethods();
        m_masterProfile->addSupportedMethod(INVITE);
//        m_masterProfile->addSupportedMethod(UPDATE);
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
        m_masterProfile->addSupportedMimeType(OPTIONS, Mime("application", "sdp"));
        m_masterProfile->addSupportedMimeType(OPTIONS, Mime("multipart", "mixed"));
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
        m_masterProfile->addSupportedScheme("Digest");

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

        m_masterProfile->setDefaultRegistrationTime(m_reg_timeout);
        m_masterProfile->setDefaultRegistrationRetryTime(0);

        if (!m_outboundProxy.host().empty()) {
            m_masterProfile->setOutboundProxy(m_outboundProxy);
            m_masterProfile->addSupportedOptionTag(Token(Symbols::Outbound));
        }

        m_masterProfile->setUserAgent(Data(m_userAgentName));
        m_masterProfile->setKeepAliveTimeForDatagram(25);
        m_masterProfile->setKeepAliveTimeForStream(25);
        m_masterProfile->setDefaultStaleCallTime(60);
    }

    void SipControllerCore::registerTimeOut(int time) {
        LOG_D(" registerTimeOut() m_reg_timeout %s", patch::to_string(time).c_str());
        m_reg_timeout = time;
    }

    void SipControllerCore::resetStack() {
        handleLog({"reset stack"});
        if (m_userAgent != NULL) {
            m_userAgent->shutdown(this);
        }

        if (m_stackThread != NULL) {
            m_stackThread->shutdown();
            m_stackThread->join();
        }

        if (m_stack != NULL && m_stack != nullptr) {
            m_stack->removeTransport(tls_key);

            if (m_stack)
                m_stack.reset();
        }
    }

    void SipControllerCore::onDumCanBeDeleted() {
        LOG_D(" onDumCanBeDeleted() ");
    }

    void SipControllerCore::unregisterUser(std::map<std::string,std::string> headers) {
        LOG_D("unregister jwtuser");
        if (headers.size() != 0) {
            m_headers = headers;
        }
        isLogOutAttempt = true;

        unregisterUser();
    }

    void SipControllerCore::unregisterUser() {

        LOG_D("SipControllerCore logout called");


        LOG_D("SipControllerCore unregister %s cert", m_app_id.c_str(), m_cert_id.c_str());

        isLoginWithJWT = false;
        m_reg_timeout = 0;

        reRegister();

    }

    void SipControllerCore::createSession(std::string remoteUser, std::string localSdp,
                                          std::map<std::string, std::string> headers) {
        //Block start
        mProgress = Dialing;
        LOG_D("createSession remote user %s localSdp %s", remoteUser.c_str(), localSdp.c_str());

        {
            std::lock_guard<std::mutex> lock(m_controllerMutex);
            m_headers = headers;
            m_isCaller = true;
            m_remoteUri = remoteUser;
        }

        bool localCandidatesCollected;
        {
            std::lock_guard<std::mutex> lock(m_controllerMutex);
            m_localSdp = localSdp;
            m_localSdpSet = true;
            localCandidatesCollected = m_localCandidatesCollected;
        }

        if (localCandidatesCollected) {
            LOG_D("@@SipControllerCore : localCandidatesCollected");
            sendSdpOffer(headers);
        } else {
            LOG_D("@@SipControllerCore : localCandidates not Collected");
        }
    }


/*
 --------------------------------------------------------------------------------------------------------------------
 *
 *
 * Note: These blocks should run after registration
 MARK: Application layer calling methods
 --------------------------------------------------------------------------------------------------------------------
 */
    void SipControllerCore::acceptSession(std::string localSdp) {
        LOG_D("acceptSession(localsdp) %s", localSdp.c_str());

        mProgress = Ringing;
        m_inInBoundAccepted = true;

        m_localSdp = localSdp;
        m_localSdpSet = true;

        if (m_localCandidatesCollected) {
            LOG_D("acceptSession localCandidatesCollected sendSdpAnswer called");
            sendSdpAnswer();
            std::lock_guard<std::mutex> lock(m_controllerMutex);
            m_inCall = true;
        }
        LOG_D("acceptSession executed");
    }

    void SipControllerCore::rejectOtherCall() {
        LOG_D("rejectOtherCall() already on another call. rejecting second call.");
        if (m_otherServerInviteSession != nullptr) {
            m_otherServerInviteSession->reject(486);
            m_otherServerInviteSession = nullptr;
        }
    }

/// Call got rejected from UAC side. Make sure now onTerminated is not getting called
/// Note : It can lead to app crash!
    void SipControllerCore::reject() {
        LOG_D("Rejecting call in SIP Controle layer");
        if (mProgress == Done && isUACRejected) {
            LOG_D(
                    "reject() already rejected cannot accept repeat reject request | returing from here");
            return;
        }

        InfoLog(<<"Info Rejecting call in SIP Controle layer");

        isUACRejected = true;

        m_localCandidatesCollected = false;

        m_inCall = false;

        SipCallHandler *callHandler;

        callHandler = m_callHandler;

        m_inInBoundAccepted = false;
        m_isCaller = false;
        m_remoteSdpSet = false;
        m_localSdp.clear();
        m_remoteSdp.clear();
        m_localCandidates.clear();
        m_current_callid.clear();

//        try{
        if (m_serverInviteSession != nullptr) {
            if (!m_serverInviteSession->isConnected()) {
                mProgress = Done;
                LOG_D("Rejecting with status code 486");
                m_serverInviteSession->reject(486);
                m_serverInviteSession = nullptr;

                callHandler->handleCall(TerminateCall, "Local Rejected");
            } else {
                LOG_D("Rejecting with hangup");
                terminateSession();
            }
        } else {
            mProgress = Done;
            callHandler->handleCall(TerminateCall, "Local Rejected");
            LOG_D("reject() m_serverInviteSession is nil");
        }
    }


    void SipControllerCore::networkChange(std::map<std::string,std::string> headers) {
        LOG_D("networkChange");

        refresh_targets = !isCallRinging;

        LOG_D("***Network change : %s", printBool(refresh_targets));
        LOG_D("***isCallRinging  : %s", printBool(isCallRinging));

        if (m_stack != NULL) {
            m_stack->removeTransport(tls_key);
            sleep(1);
            getTransport();
        }
        if (headers.size() != 0 && m_headers.size() == 0) {
            m_headers = headers;
        }
        reRegister();
    }

    void SipControllerCore::refreshTargetsForInvite() {
        std::string address = "sip:" + m_username + "@" + m_domain + ";transport=tls";
        handleLog({"reINVITE sending... : %s", address.c_str()});
        if (m_clientInviteSession != nullptr) {
            m_clientInviteSession->targetRefresh(NameAddr(address.c_str()));
        }

        if (m_serverInviteSession != nullptr) {
            m_serverInviteSession->targetRefresh(NameAddr(address.c_str()));
        }
    }

    void SipControllerCore::ringing() {
        LOG_D("Sending ringing 180 in SIP Controle layer");
        if (m_serverInviteSession) {
            m_serverInviteSession->provisional(180);
        }
    }

    void SipControllerCore::onIceCandidate(std::string &sdp, std::string &mid) {
        LOG_D("onIceCandidate");

        {
            IceCandidate iceCandidate;
            iceCandidate.mid = mid;
            iceCandidate.candidate = sdp;
            m_localCandidates.push_back(iceCandidate);
        }
    }

    void SipControllerCore::onIceGatheringFinished() {

        LOG_D("onIceGatheringFinished");

        if (mProgress == Done) {
            LOG_D(
                    "onIceGatheringFinished() session is destroyed due to progress == done | returing from here");
            return;
        }


        bool isCaller;
        bool localSdpSet;
        {
            std::lock_guard<std::mutex> lock(m_controllerMutex);
            isCaller = m_isCaller;
            localSdpSet = m_localSdpSet;
        }

        if (isCaller && localSdpSet) {
            if (!m_headers.empty()) {
                LOG_D("@@m_headers is not empty");
                sendSdpOffer(m_headers);
            } else {
                LOG_D("@@ m_headers is empty");
                sendSdpOffer();
            }

            {
                std::lock_guard<std::mutex> lock(m_controllerMutex);
                m_inCall = true;
            }

        } else if (!isCaller && localSdpSet && m_inInBoundAccepted) {
            LOG_D("onIceGatheringFinished sending answer");
            sendSdpAnswer();

            {
                std::lock_guard<std::mutex> lock(m_controllerMutex);
                m_inCall = true;
            }
        } else {
            LOG_D("onIceGatheringFinished incoming call ice gathering state finish");

            {
                std::lock_guard<std::mutex> lock(m_controllerMutex);
                m_inCall = true;
            }
        }

        {
            std::lock_guard<std::mutex> lock(m_controllerMutex);
            m_localCandidatesCollected = true;
        }
    }

    void SipControllerCore::terminateSession() {
        m_localCandidatesCollected = false;

        if (mProgress == Done) {
            LOG_D(
                    "@@terminateSession() already destroyed due to progress == done | returing from here");
            return;
        }

        mProgress = Done;
        m_inCall = false;


        if (m_clientInviteSession != nullptr) {
            LOG_D("@@terminateSession m_clientInviteSession");
            m_clientInviteSession->end(InviteSession::UserHangup);
            m_clientInviteSession = nullptr;
        }

        if (m_serverInviteSession != nullptr) {
            LOG_D("@@terminateSession m_serverInviteSession");
            m_serverInviteSession->end(InviteSession::UserHangup);
            m_serverInviteSession = nullptr;
        }

        m_inInBoundAccepted = false;
        m_isCaller = false;
        m_localSdpSet = false;
        m_remoteSdpSet = false;

        m_localSdp.clear();
        m_remoteSdp.clear();
        m_localCandidates.clear();
        m_current_callid.clear();
        isUACRejected = false;
        m_headers.clear();


        LOG_D("@@terminateSession 2");

//        // Call state handler
//        SipCallStateHandler *callStateHandler;
//        {
//            callStateHandler = m_callStateHandler;
//        }
//        callStateHandler->handleCallState("Local Ignored", 486);
        SipCallHandler *callHandler;

        callHandler = m_callHandler;
        callHandler->handleCall(TerminateCall, "Local Ignored");


        LOG_D("terminateSession freed all memory");
    }







/*
 --------------------------------------------------------------------------------------------------------------------
 *
 *
 *These blocks should run after registration
 MARK: InviteSessionHandle
 *
 --------------------------------------------------------------------------------------------------------------------
 */

/// called when an dialog enters the terminated state - this can happen
/// after getting a BYE, Cancel, or 4xx,5xx,6xx response - or the session
/// times out
    void SipControllerCore::onTerminated(InviteSessionHandle,
                                         InviteSessionHandler::TerminatedReason reason,
                                         const SipMessage *msg) {
        isCallRinging = false;

        LOG_D("***onTerminated");

        if (isSecondaryUAC == true) {
            isSecondaryUAC = false;
            LOG_D("onTerminated() already on another call | returing from here");
            return;
        }

        m_localCandidatesCollected = false;

        if (mProgress == Done) {
            LOG_D(
                    "@@onTerminated() already destroyed due to progress == done | returing from here");
            return;
        }

        mProgress = Done;
        LOG_D("@@onTerminated() terminate from the same callid ");

        SipCallStateHandler *callStateHandler;
        bool inCall;

        callStateHandler = m_callStateHandler;


        SipCallHandler *callHandler;

        callHandler = m_callHandler;
        inCall = m_inCall;


        if (isUACRejected == true) {
            callHandler->handleCall(TerminateCall, "Local call rejected");
//            sleep(2);
//            m_serverInviteSession = nullptr;
            LOG_D("@@onTerminated() iSUACRejected = true | returing from here");
            return;
        }

//        std::string callid = msg->header(h_CallId).value().c_str();


        //Some basic reset
        Data reasonData;
        std::string reasonDataString;
        unsigned int statusCode = 603;

        switch (reason) {
            case InviteSessionHandler::RemoteBye:
                reasonData = "received a BYE from peer";
                reasonDataString = "RemoteBye";
                statusCode = 200;//Making it 200 bcz i m expecting call terminated after successfully connected
                break;
            case InviteSessionHandler::RemoteCancel:
                reasonData = "received a CANCEL from peer";
                reasonDataString = "RemoteCancel";
                statusCode = 487;
                break;
            case InviteSessionHandler::Rejected:
                reasonData = "received a rejection from peer";
                reasonDataString = "Rejected";
                statusCode = 486;
                break;
            case InviteSessionHandler::LocalBye:
                reasonData = "ended locally via BYE";
                reasonDataString = "LocalBye";
                break;
            case InviteSessionHandler::LocalCancel:
                reasonData = "ended locally via CANCEL";
                reasonDataString = "LocalCancel";
                break;
            case InviteSessionHandler::Replaced:
                reasonData = "ended due to being replaced";
                reasonDataString = "Replaced";
                break;
            case InviteSessionHandler::Referred:
                reasonData = "ended due to being reffered";
                reasonDataString = "Referred";
                break;
            case InviteSessionHandler::Error:
                reasonData = "ended due to an error";
                reasonDataString = "Error";
                break;
            case InviteSessionHandler::Timeout:
                reasonData = "ended due to a timeout";
                reasonDataString = "Timeout";
                break;
            default:
                reasonData = "default error";
                reasonDataString = "Default Error";
                break;
        }


        // 486 - Busy
        // 487 - Cancelled
        if (msg) {
            if (msg->isResponse()) {
                statusCode = msg->header(h_StatusLine).responseCode();
            }
        }

        if (statusCode == 486) {
            reasonDataString = "Rejected";
        } else if (statusCode == 487) {
            reasonDataString = "RemoteCancel";
        }

        if (msg) {
            InfoLog(<< "InviteSessionHandle onTerminated: status code = " << statusCode <<" reason= " << reasonData << ", msg=" << msg->brief());
        } else {
            InfoLog(<< "InviteSessionHandle onTerminated: reason=" << reasonData);
        }

        callStateHandler->handleCallState(reasonDataString, statusCode);

        if (callHandler)
            callHandler->handleCall(TerminateCall, reasonDataString);

        if (!inCall) {
            LOG_D("@@onTerminated() already destroyed | returing from here");
            return;
        }

        m_inInBoundAccepted = false;

        m_current_callid.clear();
        m_inCall = false;
        m_isCaller = false;
        m_localSdpSet = false;
        m_remoteSdpSet = false;
        m_localSdp.clear();
        m_remoteSdp.clear();
        m_localCandidates.clear();
        m_headers.clear();
        m_clientInviteSession = nullptr;
        m_serverInviteSession = nullptr;
    }

    void SipControllerCore::onReadyToSend(InviteSessionHandle, SipMessage& msg) {
        LOG_D("@SipControllerCore::onReadyToSend");

        if (msg.isRequest() && !m_outboundProxy.host().empty()) {
            LOG_D("@SipControllerCore::onReadyToSend:- Setting Force Target To Outbound Proxy");
            msg.setForceTarget(m_outboundProxy);
        }
    }

/// Outgoing call - called when an answer is received - has nothing to do with user
/// answering the call
    void SipControllerCore::onAnswer(InviteSessionHandle, const SipMessage &msg,
                                     const SdpContents &sdp) {

        LOG_D("***onAnswer");
        isCallRinging = false;
        LOG_D("InviteSessionHandle Answer received | Outgoing call flow");

        SipCallHandler *callHandler;
        {
            std::lock_guard<std::mutex> lock(m_controllerMutex);
            callHandler = m_callHandler;
        }

        SipCallStateHandler *callStateHandler;
        {
            std::lock_guard<std::mutex> lock(m_controllerMutex);
            callStateHandler = m_callStateHandler;
        }

        callStateHandler->handleCallState("CallAccepted", msg.header(h_StatusLine).responseCode());

        SipSDPHandler *sdpHandler;
        {
            std::lock_guard<std::mutex> lock(m_controllerMutex);
            sdpHandler = m_sdpHandler;
        }

        HeaderFieldValue headerFieldValue = msg.getRawBody();
        std::string remoteSdp = sdp.getBodyData().c_str();
        LOG_D("SDP Answer | outgoing call flow remote SDP :%s", remoteSdp.c_str());

        std::string callid = getCallId(msg);
        m_current_callid = callid;

        std::string callInfo = "call_id:" + callid;

        callInfo = callInfo + "," + "type:" + "outgoing";

        for (auto &&i :msg.getRawUnknownHeaders()) {
            std::string value = msg.header(ExtensionHeader(i.first)).front().value().c_str();
            callInfo = callInfo + "," + i.first.c_str() + ":" + value;
        }

        //Append extra headers also in the same info
        //Call info handler
        SipCallInfoHandler *callInfoHandler;
        {
            std::lock_guard<std::mutex> lock(m_controllerMutex);
            callInfoHandler = m_callInfoHandler;
        }

        callInfoHandler->handleCallInfo(callInfo);

//        sdpHandler->handleSDP(remoteSdp);

        {
            std::lock_guard<std::mutex> lock(m_controllerMutex);
            m_inCall = true;
            m_remoteSdp = remoteSdp;
            m_remoteSdpSet = true;
        }

        if (callHandler)
            callHandler->handleCall(CallAccepted, "");
    }


/// Incoming call - called when an offer is received - must send an answer soon after this
    void
    SipControllerCore::onOffer(InviteSessionHandle, const SipMessage &msg, const SdpContents &sdp) {
        LOG_D("InviteSessionHandle Offer received | Incoming call flow");
        LOG_D("***onOffer");

        if(refresh_targets == false)
            isCallRinging = true;

        SipCallStateHandler *callStateHandler;
        SipCallHandler *callHandler;
        SipSDPHandler *sdpHandler;

        resip::H_From headerType;
        H_From::Type from = msg.header(headerType);
        const char* fromC = from.uri().user().c_str();

        ///Call Handler
        {
            std::lock_guard<std::mutex> lock(m_controllerMutex);
            callHandler = m_callHandler;
        }
        if (callHandler) {
            callHandler->handleCall(IncomingCall, fromC);
        }

        ///Call State Handler
        {
            std::lock_guard<std::mutex> lock(m_controllerMutex);
            callStateHandler = m_callStateHandler;
        }
        callStateHandler->handleCallState("EarlyMedia", 183);

        ///SDP Handler
        {
            std::lock_guard<std::mutex> lock(m_controllerMutex);
            sdpHandler = m_sdpHandler;
        }
        HeaderFieldValue headerFieldValue = msg.getRawBody();
        std::string remoteSdp = sdp.getBodyData().c_str();
        sdpHandler->handleSDP(remoteSdp);

        LOG_D("SDP Offer incoming \n %s", remoteSdp.c_str());

        {
            std::lock_guard<std::mutex> lock(m_controllerMutex);
            m_isCaller = false;
            m_inCall = true;
            m_remoteSdp = remoteSdp;
            m_remoteSdpSet = true;
        }
    }

/// called when a dialog initiated as a UAS enters the connected state
    void SipControllerCore::onConnected(InviteSessionHandle, const SipMessage &msg) {
        mProgress = Connected;
        LOG_D("InviteSessionHandle Session connected");
    }

/// called when an Invite w/out offer is sent, or any other context which
/// requires an offer from the user
    void SipControllerCore::onOfferRequired(InviteSessionHandle, const SipMessage &msg) {
        LOG_D("InviteSessionHandle onOfferRequired");
    }

/// called if an offer in a UPDATE or re-INVITE was rejected - not real
/// useful. A SipMessage is provided if one is available
    void SipControllerCore::onOfferRejected(InviteSessionHandle, const SipMessage *msg) {
        LOG_D("InviteSessionHandle onOfferRejected");
    }

/// called when INFO message is received
/// the application must call acceptNIT() or rejectNIT()
/// once it is ready for another message.
    void SipControllerCore::onInfo(InviteSessionHandle, const SipMessage &msg) {
        LOG_D("InviteSessionHandle onInfo");
    }

/// called when response to INFO message is received
    void SipControllerCore::onInfoSuccess(InviteSessionHandle, const SipMessage &msg) {
        LOG_D("InviteSessionHandle onInfoSuccess");
    }

    void SipControllerCore::onInfoFailure(InviteSessionHandle, const SipMessage &msg) {
        LOG_D("InviteSessionHandle onInfoFailure");
    }

/// called when MESSAGE message is received
    void SipControllerCore::onMessage(InviteSessionHandle, const SipMessage &msg) {
        LOG_D("InviteSessionHandle onMessage");
    }

/// called when response to MESSAGE message is received
    void SipControllerCore::onMessageSuccess(InviteSessionHandle, const SipMessage &msg) {
        LOG_D("InviteSessionHandle onMessageSuccess");
    }

    void SipControllerCore::onMessageFailure(InviteSessionHandle, const SipMessage &msg) {
        LOG_D("InviteSessionHandle onMessageFailure");
    }

/// called when an REFER message is received.  The refer is accepted or
/// rejected using the server subscription. If the offer is accepted,
/// DialogUsageManager::makeInviteSessionFromRefer can be used to create an
/// InviteSession that will send notify messages using the ServerSubscription
    void SipControllerCore::onRefer(InviteSessionHandle, ServerSubscriptionHandle,
                                    const SipMessage &msg) {
        LOG_D("InviteSessionHandle onRefer");
    }

    void SipControllerCore::onReferNoSub(InviteSessionHandle, const SipMessage &msg) {
        LOG_D("InviteSessionHandle onReferNoSub");
    }

/// called when an REFER message receives a failure response
    void SipControllerCore::onReferRejected(InviteSessionHandle, const SipMessage &msg) {
        LOG_D("InviteSessionHandle onReferRejected");
    }

/// called when an REFER message receives an accepted response
    void SipControllerCore::onReferAccepted(InviteSessionHandle, ClientSubscriptionHandle,
                                            const SipMessage &msg) {
        LOG_D("InviteSessionHandle onReferAccepted");
    }





/*
 --------------------------------------------------------------------------------------------------------------------
 *
 *
 *These blocks should run after registration
 MARK: Register ClientRegistrationHandle
 *
 *
 *
 --------------------------------------------------------------------------------------------------------------------
 */
/// Called when registraion succeeds or each time it is sucessfully
/// refreshed (manual refreshes only).
    void SipControllerCore::onSuccess(ClientRegistrationHandle, const SipMessage &response) {
        LOG_D("***ClientRegistrationHandle onSuccess()");
        isRegistrationInitiated = false;
        sipLogString = "";
        retry_count = 1;

        isConnectionLost = false;

        if(isMovedToFallback) {
            LOG_D("ismOveToFallback to false");
            isMovedToFallback = false;
        }
        if (refresh_targets) {
            refresh_targets = false;
            refreshTargetsForInvite();
        }

        if (isCallRinging) {
            LOG_D("***isCallRinging is true : terminate session");
            handleLog({"terminating Session, isCallRinging", printBool(mProgress)});
            terminateSession();
        }

        if (mProgress == Connected) {
            LOG_D(
                    "Register onSuccess() already on another call No need to notify. returing from here");
            return;
        }

        resip::H_From headerType;
        H_From::Type from = response.header(headerType);
        Data uri = from.uri().getAor();
        const char *fromC = from.uri().user().c_str();

        //TODO:Give it back to where it required
        std::string callid = response.header(h_CallId).value().c_str();
        std::string callInfo = "call_id:" + callid;
        if (true) {
            SipRegistrationHandler *registrationHandler;
            {
                std::lock_guard<std::mutex> lock(m_controllerMutex);
                registrationHandler = m_registrationHandler;
            }

            SipCallInfoHandler *callInfoHandler;
            {
                std::lock_guard<std::mutex> lock(m_controllerMutex);
                callInfoHandler = m_callInfoHandler;
            }

            callInfoHandler->handleCallInfo(callInfo);

            LOG_D("onSuccess: call info : %s", callInfo.c_str());
            registrationHandler->handleRegistration(Registered, fromC);
        }

        isProxyRegister = false;

        if (response.header(h_Vias).empty() == false) {
            Tuple publicAddress = processIpAndPort(response);

            if (publicAddress.getType() != UNKNOWN_TRANSPORT) {
                std::string ip = Tuple::inet_ntop(publicAddress).c_str();
                std::string port = patch::to_string(publicAddress.getPort());
                if (ip.size() != 0 && port.size() != 0) {
                    m_public_ip_port = ip + ":" + port;
                    LOG_D("resolving ip and port %s %s ", ip.c_str(), port.c_str());
                }
            }
        }
    }

/// Called when all of my bindings have been removed
    void SipControllerCore::onRemoved(ClientRegistrationHandle, const SipMessage &response) {
        LOG_D("ClientRegistrationHandle onRemoved");

        m_receiveMutex.lock();
        m_run = false;
        m_receiveMutex.unlock();

        m_receiveMutex.lock();
        m_receiveClosed = true;
        m_receiveMutex.unlock();

        {
            std::unique_lock<std::mutex> receiveConditionMutex(m_receiveMutex);
            if (!m_receiveClosed)
                m_receiveCondition.wait(receiveConditionMutex);
        }

//        sleep(1);
        IPSingleton* ipSingleton = IPSingleton::getInstance();
        ipSingleton->clearAddress();
        m_inInBoundAccepted = false;
        m_inCall = false;
        m_isCaller = false;
//        m_localSdpSet = false;
        m_remoteSdpSet = false;
        m_localCandidatesCollected = false;
        m_localSdp.clear();
        m_remoteSdp.clear();
        m_localCandidates.clear();
        m_inInBoundAccepted = false;
        m_headers.clear();

        resetStack();

        m_registrationHandler->handleRegistration(NotRegistered, "Logout");
    }

/// From resip/dum/RegistrationHandler.hxx
/// call on Retry-After failure.
/// return values: -1 = fail, 0 = retry immediately, N = retry in N seconds
    int SipControllerCore::onRequestRetry(ClientRegistrationHandle, int retrySeconds,
                                          const SipMessage &response) {
        handleLog({"ClientRegistration Retry , errorCode: ", printInt(getStatusCode(response))});
        if (retry_count <= REG_REQUEST_RETRY) {
            LOG_E("ClientRegistrationHandle onRequestRetry : Retry");
            retry_count += 1;
            return 2;
        } else {
            LOG_E("ClientRegistrationHandle | onRequestRetry | onFailure : Fallback : %s", m_fallbackProxyServer.c_str());
            if(pingSite("google.com")){
                moveToFallback();
                return -1;
            }
            return 0;
        }
    }

    void SipControllerCore::moveToFallback() {
        isMovedToFallback = true;
        handleLog({"move to fallback-domain :", m_fallbackProxyServer.c_str()});
        string switchFallBackProxy = m_proxyServer;
        m_proxyServer = m_fallbackProxyServer;
        m_fallbackProxyServer = switchFallBackProxy;
        retry_count = 1;
        IPSingleton* ipSingleton = IPSingleton::getInstance();
        ipSingleton->clearAddress();
        std::string proxyAddress = "sip:" + m_proxyServer + ";transport=tls";
        m_outboundProxy = Uri(Data(proxyAddress));

        if (!m_outboundProxy.host().empty()) {
            m_masterProfile->setOutboundProxy(m_outboundProxy);
            m_masterProfile->addSupportedOptionTag(Token(Symbols::Outbound));
        }

        isProxyRegister = true;
        std::string sipUri = createUri(m_username);


        std::string contact_url = "<sip:" + m_uri + ";transport=tls;fcm=" + m_app_id + ">";
        setupTransport(sipUri, contact_url);
    }

/// Called if registration fails, usage will be destroyed (unless a
/// Registration retry interval is enabled in the Profile)
    void SipControllerCore::onFailure(ClientRegistrationHandle, const SipMessage &response) {
        LOG_E("ClientRegistrationHandle onFailure");

        if(!handleHeaderErrorCode(response)) {
            resip::H_From headerType;
            H_From::Type from = response.header(headerType);
            Data uri = from.uri().getAor();
            const char *fromC = from.uri().user().c_str();
            m_registrationHandler->handleRegistration(NotRegistered, sipLogString);
            isRegistrationInitiated = false;
            sipLogString = "";
        }
    }

/// Called when a TCP or TLS flow to the server has terminated.  This can be caused by socket
/// errors, or missing CRLF keep alives pong responses from the server.
// Called only if clientOutbound is enabled on the UserProfile and the first hop server
/// supports RFC5626 (outbound).
/// Default implementation is to immediately re-Register in an attempt to form a new flow.
    void SipControllerCore::onFlowTerminated(ClientRegistrationHandle) {
        LOG_D("ClientRegistrationHandle onFlowTerminated");
    }





/*
 --------------------------------------------------------------------------------------------------------------------
 *
 * Call flow handlers method from reSIProcate
 * These blocks should run after registration.
 MARK: Call flow | InviteSessionHandle
 *
 --------------------------------------------------------------------------------------------------------------------
 */
/// called when an initial INVITE or the intial response to an incoming invite
    void SipControllerCore::onNewSession(ServerInviteSessionHandle sis,
                                         InviteSession::OfferAnswerType oat,
                                         const SipMessage &msg) {

        LOG_D("Incoming call.....onNewSession()");
        isSecondaryUAC = false;
        if (mProgress == Connected) {
            LOG_D("onNewSession() already on another call. init second call session");
            isSecondaryUAC = true;
            m_otherServerInviteSession = sis.get();
            rejectOtherCall();
            return;
        }

        m_current_callid = getCallId(msg);

        //Call info handler
        SipCallInfoHandler *callInfoHandler;
        {
            std::lock_guard<std::mutex> lock(m_controllerMutex);
            callInfoHandler = m_callInfoHandler;
        }

        callInfoHandler->handleCallInfo(processCallInfo(msg));

        LOG_D("Incoming call.....onNewSession() callid : %s", m_current_callid.c_str());

        mProgress = Ringing;

        LOG_D("Incoming call.....onNewSession()");
        if (isValidUserAgent(msg)) {
            LOG_D("onNewSession() found valid user agent init ServerInviteSession");
            m_serverInviteSession = sis.get();
            if (isUACRejected) {
                isUACRejected = false;
                reject();
                LOG_D("terminating session rejecting");
            }
            isUACRejected = false;
        } else {
            isUACRejected = false;
            LOG_D(
                    "ServerInviteSessionHandle onNewSession: Error Incoming call from invalid user agent");
        }
    }

/// called when an initial INVITE or the intial response to an outgoing invite
    void SipControllerCore::onNewSession(ClientInviteSessionHandle cis,
                                         InviteSession::OfferAnswerType oat,
                                         const SipMessage &msg) {
        LOG_D("Outgoing call.....onNewSession()");
        isUACRejected = false;
//        m_current_callid = getCallId(msg);

        m_current_callid = getCallId(msg);

        //Call info handler
        SipCallInfoHandler *callInfoHandler;
        {
            std::lock_guard<std::mutex> lock(m_controllerMutex);
            callInfoHandler = m_callInfoHandler;
        }

        callInfoHandler->handleCallInfo(processCallInfo(msg));
        LOG_D("Outgoing call.....onNewSession() callid : %s", m_current_callid.c_str());

        if (mProgress == Done) {
            LOG_D(
                    "Outgoing call.....onNewSession() mProgress == Done terminating call session");
            mProgress = Dialing;

            if (isValidUserAgent(msg)) {
                {
                    std::lock_guard<std::mutex> lock(m_controllerMutex);
                    m_clientInviteSession = cis.get();

                }
            } else {
                LOG_D(
                        "ClientInviteSessionHandle onNewSession: in (mProgress == Done) Error Outgoing call not a valid user agent");
            }

            terminateSession();
        } else {
            mProgress = Dialing;

            if (isValidUserAgent(msg)) {
                {
                    std::lock_guard<std::mutex> lock(m_controllerMutex);
                    m_clientInviteSession = cis.get();
                }
            } else {
                LOG_D(
                        "ClientInviteSessionHandle onNewSession: Error Outgoing call not a valid user agent");
            }
            SipSDPHandler *sdpHandler;
            {
                std::lock_guard<std::mutex> lock(m_controllerMutex);
                sdpHandler = m_sdpHandler;
            }
            sdpHandler->handleSDP(msg.getContents()->getBodyData().c_str());
        }
    }

/// Received a failure response from UAS
    void SipControllerCore::onFailure(ClientInviteSessionHandle, const SipMessage &msg) {
        if(!handleHeaderErrorCode(msg)) {
            m_current_callid = getCallId(msg);
            //Call info handler
            SipCallInfoHandler *callInfoHandler;
            {
                std::lock_guard<std::mutex> lock(m_controllerMutex);
                callInfoHandler = m_callInfoHandler;
            }

            callInfoHandler->handleCallInfo(processCallInfo(msg));
        }
    }

/// called when an in-dialog provisional response is received that contains a body | Note: To handle ringing state
    void SipControllerCore::onEarlyMedia(ClientInviteSessionHandle, const SipMessage &msg,
                                         const SdpContents &sdp) {
        LOG_D("***onEarlyMedia");
        LOG_D("ClientInviteSessionHandle onEarlyMedia");
        mProgress = Dialing;

        m_current_callid = getCallId(msg);

        std::string callInfo = "call_id:" + m_current_callid;

        for (auto &&i :msg.getRawUnknownHeaders()) {
            std::string value = msg.header(ExtensionHeader(i.first)).front().value().c_str();
            callInfo = callInfo + "," + i.first.c_str() + ":" + value;
        }

        SipCallInfoHandler *callInfoHandler;
        {
            std::lock_guard<std::mutex> lock(m_controllerMutex);
            callInfoHandler = m_callInfoHandler;
        }
        callInfoHandler->handleCallInfo(callInfo);

        SipCallStateHandler *callStateHandler;
        {
            std::lock_guard<std::mutex> lock(m_controllerMutex);
            callStateHandler = m_callStateHandler;
        }
        callStateHandler->handleCallState("EarlyMedia", getStatusCode(msg));
    }

/// called when dialog enters the Early state - typically after getting 18x
    void SipControllerCore::onProvisional(ClientInviteSessionHandle, const SipMessage &msg) {
        mProgress = Dialing;
        LOG_D("ClientInviteSessionHandle onProvisional");
    }

/// called when a dialog initiated as a UAC enters the connected state
    void SipControllerCore::onConnected(ClientInviteSessionHandle cis, const SipMessage &msg) {
        mProgress = Connected;
        LOG_D("ClientInviteSessionHandle Session connected");
    }

/// called when a fork that was created through a 1xx never receives a 2xx
/// because another fork answered and this fork was canceled by a proxy.
    void SipControllerCore::onForkDestroyed(ClientInviteSessionHandle) {
        mProgress = Done;
        LOG_D("ClientInviteSessionHandle onForkDestroyed");
    }

/** UAC gets no final response within the stale call timeout (default is 3
 * minutes). This is just a notification. After the notification is
 * called, the InviteSession will then call
 * InviteSessionHandler::terminate() */
    void SipControllerCore::onStaleCallTimeout(ClientInviteSessionHandle) {
        mProgress = Done;
        LOG_D("ClientInviteSessionHandle onStaleCallTimeout");
    }

/// called when a 3xx with valid targets is encountered in an early dialog
/// This is different then getting a 3xx in onTerminated, as another
/// request will be attempted, so the DialogSet will not be destroyed.
/// Basically an onTermintated that conveys more information.
/// checking for 3xx respones in onTerminated will not work as there may
/// be no valid targets.
    void SipControllerCore::onRedirected(ClientInviteSessionHandle, const SipMessage &msg) {
        mProgress = Done;
        LOG_D("ClientInviteSessionHandle onRedirected");
    }

    void SipControllerCore::arrayToMap(JNIEnv *env, jobjectArray j_headerKeys,
                                       jobjectArray j_headerValues,
                                       std::map<std::string, std::string> &headerMap) {

        int size = env->GetArrayLength(j_headerKeys);
        if (size > 0) {
            LOG_D("@@SipControllerCore : arrayToMap : size > 0");

            for (int i = 0; i < size; ++i) {
                jstring jkey = (jstring) env->GetObjectArrayElement(j_headerKeys, i);
                jstring jvalue = (jstring) env->GetObjectArrayElement(j_headerValues, i);
                const char *key = env->GetStringUTFChars(jkey, 0);
                const char *value = env->GetStringUTFChars(jvalue, 0);

                headerMap.insert(pair<string, string>(key, value));

                env->ReleaseStringUTFChars(jkey, key);
                env->DeleteLocalRef(jkey);

                env->ReleaseStringUTFChars(jvalue, value);
                env->DeleteLocalRef(jvalue);
            }

            for (auto itr = headerMap.begin();
                 itr != headerMap.end(); ++itr) {
                LOG_D("rtcsip_jni : makeCall : Traverse first : %s", itr->first.c_str());
                LOG_D("rtcsip_jni : makeCall : Traverse second : %s", itr->second.c_str());
            }

        }

    }

    std::string SipControllerCore::getPublicIp() {
        return m_public_ip;
    }

    bool SipControllerCore::handleHeaderErrorCode(const SipMessage &message) {
        for (auto &&i :message.getRawUnknownHeaders()) {
            std::string value = message.header(ExtensionHeader(i.first)).front().value().c_str();
            string  header = i.first.c_str();
            if(header == "X-Plivo-Jwt-Error-Code"){
                m_headers.clear();
                isLoginWithJWT = false;
                string send = value + "@@" + sipLogString;
                if(!isLogOutAttempt){
                    m_registrationHandler->handleRegistration(NotRegisteredJWT, send);
                    isRegistrationInitiated = false;
                    sipLogString = "";
                }
                return true;
            }
        }
        return false;
    }

    const char *SipControllerCore::printBool(bool targets) {
        return targets ? "true" : "false";
    }

    void SipControllerCore::getTransport() {
        bool isTransportAdded = false, isFirstAttempt = true;
        while(!isTransportAdded) {
            try {
                if (!isFirstAttempt) tls_transport_port = rand()%((5000 - 5100) + 1) + 5000;
                isFirstAttempt = false;
                tls_key  = m_stack->addTransport(TLS, tls_transport_port)->getKey();
                isTransportAdded = true;
            } catch (...) {
                LOG_D("Retry adding transport");
            }
        }
    }

    bool SipControllerCore::checkIfSipMessage(string value) {
        std::string prefix1 = "SIP/";
        std::string prefix2 = "REGISTER";
        if (value.find(prefix2) != std::string::npos || value.find(prefix1) != std::string::npos){
            return true;
        }
        return false;
    }

    void SipControllerCore::LOG_D(const char *string, const char *str) {
        if(isDebug){
            LOGD(string, str);
        }
    }

    void SipControllerCore::LOG_D(const char *string, const char *str, const char *stt) {
        if(isDebug){
            LOGD(string, str, stt);
        }
    }

    void SipControllerCore::LOG_D(const char *string) {
        if(isDebug){
            LOGD("%s", string);
        }
    }

    void SipControllerCore::LOG_E(const char *string) {
        if(isDebug){
            LOGE("%s", string);
        }
    }

    void SipControllerCore::LOG_E(const char *string, const char *str) {
        if(isDebug){
            LOGE(string, str);
        }
    }

    void SipControllerCore::setDebugMode(jboolean i) {
        LOG_D("setDebugMode %s", printBool(i));
        isDebug = i;
    }

    std::string SipControllerCore::getDateString() {
        time_t rawtime;
        struct tm * timeinfo;
        char buffer[80];
        time (&rawtime);
        timeinfo = localtime(&rawtime);
        strftime(buffer,sizeof(buffer),"%d-%m-%Y %H:%M:%S",timeinfo);
        std::string str(buffer);
        return str;
    }

    bool SipControllerCore::pingSite(const char *site) {
        std::string command = "ping -c 1 " + std::string(site);
        int result = system(command.c_str());
        return result == 0;
    }

    void SipControllerCore::handleLog(std::initializer_list<const char*> logs) {
        std::string result;
        for (const char* str : logs) {
            result += str;
        }
        SipLogHandler *logHandler;
        {
            std::lock_guard<std::mutex> lock(m_controllerMutex);
            logHandler = m_logHandler;
        }
        logHandler->handleInfoLog(std::string(result));
    }

    const char *SipControllerCore::printInt(int i) {
        return std::to_string(i).c_str();
    }

    void SipControllerCore::clearRegistrationRetry() {
        LOG_D("clearRegistrationRetry");
        if(isConnectionLost){
            resetStack();
            isConnectionLost = false;
        }
    }

    const char *SipControllerCore::CallProgressToString(SipControllerCore::DialProgress progress) {
        switch (progress) {
            case Dialing:
                return "Dialing";
            case Ringing:
                return "Ringing";
            case Connected:
                return "Connected";
            case Done:
                return "Done";
            default:
                return "Unknown";
        }
    }

    bool SipControllerCore::checkIfFallbackRequired(bool online) {
        return online && !isRegistrationInitiated && !isMovedToFallback && mProgress != Ringing;
    }

    int SipControllerCore::extractTransportKey(std::string value) {
        std::string keyString = "transportKey=";
        std::size_t pos = value.find(keyString);
        if (pos != std::string::npos) {
            pos += keyString.length();
            std::size_t endPos = value.find(' ', pos);
            if (endPos != std::string::npos) {
                try {
                    std::string keySubstr = value.substr(pos, endPos - pos);
                    return std::stoi(keySubstr); // Convert substring to int
                } catch (const std::invalid_argument& e) {
                    std::cerr << "Invalid argument: " << e.what() << std::endl;
                } catch (const std::out_of_range& e) {
                    std::cerr << "Out of range: " << e.what() << std::endl;
                }
            }
        }
        return -1;
    }
}
/*

//Outgoing call flow
SipControllerCore:: ***sendSdpOffer
SipControllerCore:: ***onAnswer
SipControllerCore:: ***onTerminated

//Incoming call flow
SipControllerCore:: ***onOffer
SipControllerCore:: ***sendSdpAnswer
SipControllerCore:: ***onTerminated

 * */