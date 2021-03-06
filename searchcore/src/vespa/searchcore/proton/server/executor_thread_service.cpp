// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "executor_thread_service.h"
#include <vespa/vespalib/util/closuretask.h>
#include <vespa/fastos/thread.h>

using vespalib::makeClosure;
using vespalib::makeTask;
using vespalib::Executor;
using vespalib::Gate;
using vespalib::Runnable;
using vespalib::ThreadStackExecutorBase;

namespace proton {

namespace internal {

struct ThreadId {
    FastOS_ThreadId _id;
};
}

namespace {

void
sampleThreadId(FastOS_ThreadId *threadId)
{
    *threadId = FastOS_Thread::GetCurrentThreadId();
}

std::unique_ptr<internal::ThreadId>
getThreadId(ThreadStackExecutorBase &executor)
{
    std::unique_ptr<internal::ThreadId> id = std::make_unique<internal::ThreadId>();
    executor.execute(makeTask(makeClosure(&sampleThreadId, &id->_id)));
    executor.sync();
    return id;
}

void
runRunnable(Runnable *runnable, Gate *gate)
{
    runnable->run();
    gate->countDown();
}

} // namespace

ExecutorThreadService::ExecutorThreadService(ThreadStackExecutorBase &executor)
    : _executor(executor),
      _threadId(getThreadId(executor))
{
}

ExecutorThreadService::~ExecutorThreadService() {}

void
ExecutorThreadService::run(Runnable &runnable)
{
    if (isCurrentThread()) {
        runnable.run();
    } else {
        Gate gate;
        _executor.execute(makeTask(makeClosure(&runRunnable, &runnable, &gate)));
        gate.await();
    }
}

bool
ExecutorThreadService::isCurrentThread() const
{
    FastOS_ThreadId currentThreadId = FastOS_Thread::GetCurrentThreadId();
    return FastOS_Thread::CompareThreadIds(_threadId->_id, currentThreadId);
}

} // namespace proton
