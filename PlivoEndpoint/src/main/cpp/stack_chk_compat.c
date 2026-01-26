/**
 * stack_chk_compat.c
 * 
 * Compatibility shim for __stack_chk_fail_local symbol.
 * 
 * This is needed because:
 * - Old OpenSSL (1.0.2k) was compiled with an older NDK that used __stack_chk_fail_local
 *   for stack smashing protection on x86 architectures (both 32-bit and 64-bit).
 * - New NDK (r28+) uses __stack_chk_fail instead (without _local suffix).
 * 
 * This shim provides __stack_chk_fail_local by forwarding to __stack_chk_fail.
 * 
 * Needed for x86 architectures (__i386__ and __x86_64__).
 */

#if defined(__i386__) || defined(__x86_64__)

#include <stdlib.h>

// External declaration of the standard __stack_chk_fail
extern void __stack_chk_fail(void);

// Provide __stack_chk_fail_local as a hidden symbol that calls __stack_chk_fail
__attribute__((visibility("hidden")))
void __stack_chk_fail_local(void) {
    __stack_chk_fail();
}

#endif // __i386__ || __x86_64__

