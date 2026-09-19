#!/usr/bin/env python3
"""Apply Linux resource and network isolation, then exec one trusted media tool."""

from __future__ import annotations

import ctypes
import errno
import json
import os
import resource
import signal
import socket
import sys


_SCMP_ACT_ALLOW = 0x7FFF0000
_SCMP_ACT_ERRNO = 0x00050000
_SCMP_CMP_EQ = 4
_SCMP_CMP_MASKED_EQ = 7
_CLONE_THREAD = 0x00010000
_PR_SET_PDEATHSIG = 1
_PR_SET_NO_NEW_PRIVS = 38
_DENIED_SYSCALLS = (
    b"socket",
    b"socketpair",
    b"connect",
    b"accept",
    b"accept4",
    b"bind",
    b"listen",
    b"sendto",
    b"sendmsg",
    b"sendmmsg",
    b"recvfrom",
    b"recvmsg",
    b"recvmmsg",
    b"shutdown",
    b"getsockname",
    b"getpeername",
    b"getsockopt",
    b"setsockopt",
    b"io_uring_setup",
    b"io_uring_enter",
    b"io_uring_register",
    b"fork",
    b"vfork",
    b"unshare",
    b"setns",
    b"setsid",
    b"setpgid",
    b"kill",
    b"tkill",
    b"pidfd_send_signal",
    b"pidfd_getfd",
    b"process_vm_readv",
    b"process_vm_writev",
    b"ptrace",
)


class _ScmpArgCompare(ctypes.Structure):
    _fields_ = (
        ("arg", ctypes.c_uint),
        ("op", ctypes.c_uint),
        ("datum_a", ctypes.c_uint64),
        ("datum_b", ctypes.c_uint64),
    )


class SandboxError(RuntimeError):
    """Stable internal failure with no target or workspace path."""


def _set_parent_death_signal(expected_parent_pid: int) -> None:
    if expected_parent_pid <= 1 or os.getppid() != expected_parent_pid:
        raise SandboxError("sandbox_parent_changed")
    libc = ctypes.CDLL(None, use_errno=True)
    libc.prctl.argtypes = (
        ctypes.c_int,
        ctypes.c_ulong,
        ctypes.c_ulong,
        ctypes.c_ulong,
        ctypes.c_ulong,
    )
    libc.prctl.restype = ctypes.c_int
    if libc.prctl(_PR_SET_PDEATHSIG, signal.SIGKILL, 0, 0, 0) != 0:
        raise SandboxError("sandbox_parent_guard_unavailable")
    if os.getppid() != expected_parent_pid:
        raise SandboxError("sandbox_parent_changed")


def _apply_resource_limits(
    *, cpu_seconds: int, address_space_bytes: int, file_size_bytes: int
) -> None:
    if (
        not 1 <= cpu_seconds <= 120
        or not 256 * 1024 * 1024 <= address_space_bytes <= 16 * 1024 * 1024 * 1024
        or not 1 <= file_size_bytes <= 1024 * 1024 * 1024
    ):
        raise SandboxError("sandbox_resource_limit_invalid")
    resource.setrlimit(resource.RLIMIT_CORE, (0, 0))
    resource.setrlimit(resource.RLIMIT_CPU, (cpu_seconds, cpu_seconds))
    resource.setrlimit(resource.RLIMIT_AS, (address_space_bytes, address_space_bytes))
    resource.setrlimit(resource.RLIMIT_FSIZE, (file_size_bytes, file_size_bytes))
    _, hard_limit = resource.getrlimit(resource.RLIMIT_NOFILE)
    descriptor_limit = min(64, hard_limit if hard_limit >= 0 else 64)
    resource.setrlimit(resource.RLIMIT_NOFILE, (descriptor_limit, descriptor_limit))


def _deny_network_and_process_escape() -> None:
    libc = ctypes.CDLL(None, use_errno=True)
    if libc.prctl(_PR_SET_NO_NEW_PRIVS, 1, 0, 0, 0) != 0:
        raise SandboxError("sandbox_no_new_privileges_unavailable")
    try:
        seccomp = ctypes.CDLL("libseccomp.so.2", use_errno=True)
    except OSError:
        raise SandboxError("sandbox_seccomp_unavailable") from None
    seccomp.seccomp_init.argtypes = (ctypes.c_uint32,)
    seccomp.seccomp_init.restype = ctypes.c_void_p
    seccomp.seccomp_syscall_resolve_name.argtypes = (ctypes.c_char_p,)
    seccomp.seccomp_syscall_resolve_name.restype = ctypes.c_int
    seccomp.seccomp_rule_add.argtypes = (
        ctypes.c_void_p,
        ctypes.c_uint32,
        ctypes.c_int,
        ctypes.c_uint,
    )
    seccomp.seccomp_rule_add.restype = ctypes.c_int
    seccomp.seccomp_rule_add_array.argtypes = (
        ctypes.c_void_p,
        ctypes.c_uint32,
        ctypes.c_int,
        ctypes.c_uint,
        ctypes.POINTER(_ScmpArgCompare),
    )
    seccomp.seccomp_rule_add_array.restype = ctypes.c_int
    seccomp.seccomp_load.argtypes = (ctypes.c_void_p,)
    seccomp.seccomp_load.restype = ctypes.c_int
    seccomp.seccomp_release.argtypes = (ctypes.c_void_p,)
    context = seccomp.seccomp_init(_SCMP_ACT_ALLOW)
    if not context:
        raise SandboxError("sandbox_seccomp_unavailable")
    deny = _SCMP_ACT_ERRNO | errno.EPERM
    try:
        for name in _DENIED_SYSCALLS:
            number = seccomp.seccomp_syscall_resolve_name(name)
            if number >= 0 and seccomp.seccomp_rule_add(context, deny, number, 0) != 0:
                raise SandboxError("sandbox_seccomp_unavailable")
        clone3_number = seccomp.seccomp_syscall_resolve_name(b"clone3")
        if clone3_number < 0 or seccomp.seccomp_rule_add(
            context,
            _SCMP_ACT_ERRNO | errno.ENOSYS,
            clone3_number,
            0,
        ) != 0:
            raise SandboxError("sandbox_seccomp_unavailable")
        clone_number = seccomp.seccomp_syscall_resolve_name(b"clone")
        non_thread_clone = _ScmpArgCompare(
            arg=0,
            op=_SCMP_CMP_MASKED_EQ,
            datum_a=_CLONE_THREAD,
            datum_b=0,
        )
        if clone_number < 0 or seccomp.seccomp_rule_add_array(
            context, deny, clone_number, 1, ctypes.byref(non_thread_clone)
        ) != 0:
            raise SandboxError("sandbox_seccomp_unavailable")
        parent_death_change = _ScmpArgCompare(
            arg=0,
            op=_SCMP_CMP_EQ,
            datum_a=_PR_SET_PDEATHSIG,
            datum_b=0,
        )
        prctl_number = seccomp.seccomp_syscall_resolve_name(b"prctl")
        if prctl_number < 0 or seccomp.seccomp_rule_add_array(
            context, deny, prctl_number, 1, ctypes.byref(parent_death_change)
        ) != 0:
            raise SandboxError("sandbox_seccomp_unavailable")
        if seccomp.seccomp_load(context) != 0:
            raise SandboxError("sandbox_seccomp_unavailable")
    finally:
        seccomp.seccomp_release(context)


def _parse(arguments: list[str]) -> tuple[int, int, int, int, list[str]]:
    if (
        len(arguments) < 10
        or arguments[0] != "--expected-parent-pid"
        or arguments[2] != "--cpu-seconds"
        or arguments[4] != "--address-space-bytes"
        or arguments[6] != "--file-size-bytes"
        or arguments[8] != "--"
    ):
        raise SandboxError("sandbox_arguments_invalid")
    try:
        values = tuple(int(arguments[index]) for index in (1, 3, 5, 7))
    except ValueError:
        raise SandboxError("sandbox_arguments_invalid") from None
    target = arguments[9:]
    if not target or not os.path.isabs(target[0]):
        raise SandboxError("sandbox_target_invalid")
    return values[0], values[1], values[2], values[3], target


def main(arguments: list[str] | None = None) -> int:
    raw = list(sys.argv[1:] if arguments is None else arguments)
    try:
        if raw == ["--self-test"]:
            _set_parent_death_signal(os.getppid())
            os.setsid()
            _apply_resource_limits(
                cpu_seconds=1,
                address_space_bytes=256 * 1024 * 1024,
                file_size_bytes=1024 * 1024,
            )
            _deny_network_and_process_escape()
            try:
                socket.socket()
            except PermissionError:
                print(json.dumps({"networkIsolation": "seccomp_socket_denied"}))
                return 0
            return 126
        parent_pid, cpu_seconds, address_space_bytes, file_size_bytes, target = _parse(raw)
        _set_parent_death_signal(parent_pid)
        os.setsid()
        _apply_resource_limits(
            cpu_seconds=cpu_seconds,
            address_space_bytes=address_space_bytes,
            file_size_bytes=file_size_bytes,
        )
        _deny_network_and_process_escape()
        if os.getppid() != parent_pid:
            raise SandboxError("sandbox_parent_changed")
        os.execve(target[0], target, dict(os.environ))
    except (OSError, SandboxError, ValueError):
        return 126
    return 126


if __name__ == "__main__":
    raise SystemExit(main())
