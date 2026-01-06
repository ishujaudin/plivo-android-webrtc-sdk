%module (directors="1") SwigInterface

// Anything in the following section is added verbatim to the .cxx wrapper file

%{
//    #include "../../../sampledata/newSip.h"
#include "../cpp/SipControllerCore.h"



#ifdef __cplusplus
extern "C" {
#endif
    //void setCallbackObject(SipController* callback);
#ifdef __cplusplus
}
#endif
%}
%inline %{
using namespace resip;
%}

/* turn on director wrapping PlivoAppCallback */
%feature("director") SipControllerCore;
//%feature("director")  SipControllerCore;


// Process our C++ file (only the public section)




%include "../cpp/SipControllerCore.h"
void setCallbackObject(SipController* callback);