##  第 3 章 Netty的组件和设计 

## 3.1 Channel、EventLoop 和 ChannelFuture 

Netty网络抽象的代表：

- Channel—Socket； 
- EventLoop—控制流、多线程处理、并发；
- ChannelFuture—异步通知。

### 3.1.1 Channel接口 

Netty 的 Channel接口所提供的 API，大大地降低了直接使用 Socket 类的复杂性。此外，Channel也是拥有许多预定义的、专门化实现的广泛类层次结构的根，下面是一个简短的部分清单

- EmbeddedChannel 
- LocalServerChannel
- NioDatagramChannel
- NioSctpChannel
- NioSocketChannel

### 3.1.2 EventLoop 接口

EventLoop 定义了 Netty 的核心抽象，用于处理连接的生命周期中所发生的事件

Channel、EventLoop、Thread 以及 EventLoopGroup 之间的关系：

![在这里插入图片描述](https://img-blog.csdnimg.cn/20201211172644893.png?x-oss-process=image/watermark,type_ZmFuZ3poZW5naGVpdGk,shadow_10,text_aHR0cHM6Ly9ibG9nLmNzZG4ubmV0L3dlaXhpbl80MzM5NTkxMQ==,size_16,color_FFFFFF,t_70)

- 一个 EventLoopGroup 包含一个或者多个 EventLoop； 
- 一个 EventLoop 在它的生命周期内只和一个 Thread 绑定； 
- 所有由 EventLoop 处理的 I/O 事件都将在它专有的 Thread 上被处理 
- 一个 Channel 在它的生命周期内只注册于一个 EventLoop
- 一个 EventLoop 可能会被分配给一个或多个 Channel 

在这种设计中，一个给定 Channel 的 I/O 操作都是由相同的 Thread 执行的，实际上消除了对于同步的需要



### 3.1.3 ChannelFuture 接口

Netty 中所有的 I/O 操作都是异步的。因为一个操作可能不会立即返回，所以我们需要一种用于在之后的某个时间点确定其结果的方法。为此，Netty 提供了 ChannelFuture 接口，其addListener()方法注册了一个ChannelFutureListener，以便在某个操作完成时（无论是否成功）得到通知。

关于 ChannelFuture 的更多讨论：可以将 ChannelFuture 看作是将来要执行的操作的结果的占位符。它究竟什么时候被执行则可能取决于若干的因素，因此不可能准确地预测，但是可以肯定的是它将会被执行。此外，所有属于同一个 Channel 的操作都被保证其将以它们被调用的顺序被执行

## 

##3.2 ChannelHandler 和 ChannelPipeline 

我们将更加细致地看一看那些管理数据流以及执行应用程序处理逻辑的组件。

### 3.2.1 ChannelHandler 接口

 从应用程序开发人员的角度来看，Netty的主要组件是ChannelHandler，它充当了所有处理入站和出站数据的应用程序逻辑的容器。

事实上，ChannelHandler 可专门用于几乎任何类型的动作，例如将数据从一种格式转换为另外一种格式，或者处理转换过程中所抛出的异常。



举例来说，ChannelInboundHandler 是一个你将会经常实现的子接口。这种类型的ChannelHandler 接收入站事件和数据，这些数据随后将会被你的应用程序的业务逻辑所处理。当你要给连接的客户端发送响应时，也可以从 ChannelInboundHandler 冲刷数据。你的应用程序的业务逻辑通常驻留在一个或者多个 ChannelInboundHandler 中。



### 3.2.2 ChannelPipeline 接口 

ChannelPipeline 提供了 ChannelHandler 链的容器，并定义了用于在该链上传播入站和出站事件流的 API。当 Channel 被创建时，它会被自动地分配到它专属的 ChannelPipeline。

ChannelHandler 安装到 ChannelPipeline 中的过程如下所示： 

- 一个ChannelInitializer的实现被注册到了ServerBootstrap中； 
- 当 ChannelInitializer.initChannel()方法被调用时，ChannelInitializer将在 ChannelPipeline 中安装一组自定义的 ChannelHandler； 
- ChannelInitializer 将它自己从 ChannelPipeline 中移除

ChannelHandler 是专为支持广泛的用途而设计的，可以将它看作是处理往来 ChannelPipeline 事件（包括数据）的任何代码的通用容器。使得事件流经 ChannelPipeline 是 ChannelHandler 的工作，它们是在应用程序的初始化或者引导阶段被安装的。这些对象接收事件、执行它们所实现的处理逻辑，并将数据传递给链中的下一个 ChannelHandler。它们的执行顺序是由它们被添加的顺序所决定的。实际上，被我们称为 ChannelPipeline 的是这些 ChannelHandler 的编排顺序。

入站和出站 ChannelHandler 可以被安装到同一个 ChannelPipeline 中。如果一个消息或者任何其他的入站事件被读取，那么它会从 ChannelPipeline 的头部 开始流动，并被传递给第一个 ChannelInboundHandler。这个 ChannelHandler 不一定 会实际地修改数据，具体取决于它的具体功能，在这之后，数据将会被传递给链中的下一个ChannelInboundHandler。最终，数据将会到达 ChannelPipeline 的尾端，届时，所有处理就都结束了。 数据的出站运动（即正在被写的数据）在概念上也是一样的。在这种情况下，数据将从ChannelOutboundHandler 链的尾端开始流动，直到它到达链的头部为止。在这之后，出站数据将会到达网络传输层，这里显示为 Socket。通常情况下，这将触发一个写操作。

鉴于出站操作和入站操作是不同的，你可能会想知道如果将两个类别的 ChannelHandler都混合添加到同一个 ChannelPipeline 中会发生什么。虽然 ChannelInboundHandle 和ChannelOutboundHandle 都扩展自 ChannelHandler，但是 Netty 能区分 ChannelIboundHandler 实现和 ChannelOutboundHandler 实现，并确保数据只会在具有相同定向类型的两个 ChannelHandler 之间传递。 

当ChannelHandler 被添加到ChannelPipeline 时，它将会被分配一个ChannelHandlerContext，其代表了 ChannelHandler 和 ChannelPipeline 之间的绑定。虽然这个对象可以被用于获取底层的 Channel，但是它主要还是被用于写出站数据。 在 Netty 中，有两种发送消息的方式。你可以直接写到 Channel 中，也可以 写到和 ChannelHandler相关联的ChannelHandlerContext对象中。前一种方式将会导致消息从ChannelPipeline 的尾端开始流动，而后者将导致消息从 ChannelPipeline 中的下一个 ChannelHandler 开始流动。

编写自定义 ChannelHandler 时经常会用到的适配器类：

- ChannelHandlerAdapter
- ChannelInboundHandlerAdapter
- ChannelOutboundHandlerAdapter
- ChannelDuplexHandler 





