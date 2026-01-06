//#ifndef SINGLETON_H
//#define SINGLETON_H

class IPSingleton {
private:
    static IPSingleton* instance;  // The single instance of the class
    char* address;

    // Private constructor to prevent instantiation
    IPSingleton();

public:

    // Public static method to access the singleton instance
    static IPSingleton* getInstance();
    void setAddress(const char* newValue);
    void clearAddress();
    const char* getAddress() const;
    bool isEmpty() const;
    // Other member functions of the Singleton class
    // ...
};

//#endif  // SINGLETON_H