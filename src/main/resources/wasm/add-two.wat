;; wat2wasm add-two.wat -o add-two.wasm
(module
  (func (export "addTwo") (param i32 i32) (result i32)
    local.get 0
    local.get 1
    i32.add
  )
)