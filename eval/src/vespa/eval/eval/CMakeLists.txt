# Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_library(eval_eval OBJECT
    SOURCES
    aggr.cpp
    basic_nodes.cpp
    call_nodes.cpp
    delete_node.cpp
    function.cpp
    gbdt.cpp
    interpreted_function.cpp
    key_gen.cpp
    node_types.cpp
    operation.cpp
    operator_nodes.cpp
    param_usage.cpp
    simple_tensor.cpp
    simple_tensor_engine.cpp
    tensor.cpp
    tensor_engine.cpp
    tensor_function.cpp
    tensor_nodes.cpp
    tensor_spec.cpp
    value.cpp
    value_type.cpp
    value_type_spec.cpp
    vm_forest.cpp
)
