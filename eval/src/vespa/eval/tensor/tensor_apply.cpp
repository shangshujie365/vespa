// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "tensor_apply.h"

namespace vespalib {
namespace tensor {

template <class TensorT>
TensorApply<TensorT>::TensorApply(const TensorImplType &tensor,
                                  const CellFunction &func)
    : Parent(tensor.fast_type())
{
    for (const auto &cell : tensor.cells()) {
        _builder.insertCell(cell.first, func.apply(cell.second));
    }
}

template class TensorApply<SparseTensor>;

} // namespace vespalib::tensor
} // namespace vespalib
