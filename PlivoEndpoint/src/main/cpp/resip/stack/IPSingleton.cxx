#include "IPSingleton.hxx"
#include <cstring>

IPSingleton *IPSingleton::instance = NULL;


IPSingleton::IPSingleton() {
    address = NULL;
}

IPSingleton* IPSingleton::getInstance() {
    if (instance == NULL) {
        instance = new IPSingleton();
    }
    return instance;
}

void IPSingleton::setAddress(const char* newValue) {
    // Allocate memory for the value and copy the input string
    if (address != NULL) {
        delete[] address;
    }
    address = new char[strlen(newValue) + 1];
    strcpy(address, newValue);
}

void IPSingleton::clearAddress() {
    if (address != NULL) {
        address = NULL;
    }
}

const char* IPSingleton::getAddress() const {
    return address;
}

bool IPSingleton::isEmpty() const {
    return (address == NULL || strlen(address) == 0);  // Check if val is nullptr or has length 0
}

