// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include "rpctarget.h"
#include <vespa/vespalib/util/exceptions.h>
#include <vespa/fnet/frt/supervisor.h>

namespace mbus {

RPCTarget::RPCTarget(const string &spec, FRT_Supervisor &orb) :
    _lock(),
    _orb(orb),
    _name(spec),
    _target(*_orb.GetTarget(spec.c_str())),
    _state(VERSION_NOT_RESOLVED),
    _version(),
    _versionHandlers()
{
    // empty
}

RPCTarget::~RPCTarget()
{
    _target.SubRef();
}

void
RPCTarget::resolveVersion(double timeout, RPCTarget::IVersionHandler &handler)
{
    bool hasVersion = false;
    bool shouldInvoke = false;
    {
        vespalib::MonitorGuard guard(_lock);
        if (_state == VERSION_RESOLVED || _state == PROCESSING_HANDLERS) {
            while (_state == PROCESSING_HANDLERS) {
                guard.wait();
            }
            hasVersion = true;
        } else {
            _versionHandlers.push_back(&handler);
            if (_state != TARGET_INVOKED) {
                _state = TARGET_INVOKED;
                shouldInvoke = true;
            }
        }
    }
    if (hasVersion) {
        handler.handleVersion(_version.get());
    } else if (shouldInvoke) {
        FRT_RPCRequest *req = _orb.AllocRPCRequest();
        req->SetMethodName("mbus.getVersion");
        _target.InvokeAsync(req, timeout, this);
    }
}

bool
RPCTarget::isValid() const
{
    vespalib::MonitorGuard guard(_lock);
    if (_target.IsValid()) {
        return true;
    }
    if (_state == TARGET_INVOKED || _state == PROCESSING_HANDLERS) {
        return true; // keep alive until RequestDone() is called
    }
    return false;
}

void
RPCTarget::RequestDone(FRT_RPCRequest *req)
{
    HandlerList handlers;
    {
        vespalib::MonitorGuard guard(_lock);
        assert(_state == TARGET_INVOKED);
        if (req->CheckReturnTypes("s")) {
            FRT_Values &val = *req->GetReturn();
            try {
                _version.reset(new vespalib::Version(val[0]._string._str));
            } catch (vespalib::IllegalArgumentException &e) {
                (void)e;
            }
        } else if (req->GetErrorCode() == FRTE_RPC_NO_SUCH_METHOD) {
            _version.reset(new vespalib::Version("4.1"));
        }
        _versionHandlers.swap(handlers);
        _state = PROCESSING_HANDLERS;
    }
    for (HandlerList::iterator it = handlers.begin();
         it != handlers.end(); ++it)
    {
        (*it)->handleVersion(_version.get());
    }
    {
        vespalib::MonitorGuard guard(_lock);
        _state = (_version.get() ? VERSION_RESOLVED : VERSION_NOT_RESOLVED);
        guard.broadcast();
    }
    req->SubRef();
}

} // namespace mbus
