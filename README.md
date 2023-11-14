# SpinalHDL Mem Bist Example

一个简单的例子，在顶层定义一个 bist ctrl 模块，其他所有代码不用改，自动把所有 mem 中的 bist 信号连进这个控制模块

利用 SpinalConfig 中的 memBlackBoxers 实现，在 Phase 阶段获取所有 mem，并建立 bist 信号的连接
