//
// Created by Rahul Singh Dhek on 19/04/21.
//

#ifndef TESTRESIP_JNICONTROLLER_H
#define TESTRESIP_JNICONTROLLER_H

#include <string>
#include "jni.h"

class JNIController {
public:

    virtual void testCall();
    virtual void setRemoteSDP(const char *remoteSDP);
};


#endif //TESTRESIP_JNICONTROLLER_H
